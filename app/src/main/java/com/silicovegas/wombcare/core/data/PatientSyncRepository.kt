package com.silicovegas.wombcare.core.data

import com.google.firebase.database.FirebaseDatabase
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.device.SessionSnapshot
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The patient→cloud write path. Only ever called when the mother has sharing enabled AND is
 * signed in; if either is false, nothing here runs and nothing leaves the phone.
 *
 * Each completed window is pushed as ONE multi-location update covering three things a
 * doctor needs: the individual reading (for the live chart), the rolling session summary
 * (for history), and the small `liveStatus` map (for the patient-list live dot). Doing them
 * together keeps the doctor's three views mutually consistent — they never show a fresh
 * reading against a stale summary.
 */
@Singleton
class PatientSyncRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {
    suspend fun setSharingEnabled(patientUid: String, enabled: Boolean) {
        db.getReference(DbPaths.patient(patientUid)).child("sharingEnabled")
            .setValue(enabled).await()
    }

    /**
     * Store just enough on the patient node for a doctor's list row — name and weeks —
     * so the doctor reads ONE node per patient instead of also fetching the profile. Called
     * when sharing turns on.
     */
    suspend fun publishProfile(patientUid: String, fullName: String, pregnancyWeeks: Int?) {
        val updates = hashMapOf<String, Any?>(
            "${DbPaths.patient(patientUid)}/fullName" to fullName,
        )
        if (pregnancyWeeks != null) {
            updates["${DbPaths.patient(patientUid)}/pregnancyWeeks"] = pregnancyWeeks
        }
        db.reference.updateChildren(updates).await()
    }

    suspend fun pushWindow(patientUid: String, session: SessionSnapshot, reading: ClinicalReading) {
        // Absent (invalid) FHR must not be written as 0; ReadingWire omits the key.
        val readingPath = DbPaths.reading(patientUid, session.sessionId, reading.deviceMinute)
        val sessionPath = DbPaths.session(patientUid, session.sessionId)
        val livePath = DbPaths.liveStatus(patientUid)

        val updates = hashMapOf<String, Any?>(
            readingPath to ReadingWire.toMap(reading),
            "$sessionPath/startedAt" to session.startedAtEpochMillis,
            "$sessionPath/readingCount" to session.windowCount,
            "$sessionPath/avgFhr" to session.averageFhr,
            "$sessionPath/totalKicks" to session.totalKicks,
            "$sessionPath/worstNsp" to nspWire(session.worstStatus),
            livePath to mapOf(
                "sessionId" to session.sessionId,
                "nsp" to nspWire(reading.status),
                "fhrBpm" to reading.fhrBpm,
                "kickTotal" to session.totalKicks,
                "confidence" to reading.confidencePercent,
                "lastReadingAt" to reading.receivedAtEpochMillis,
            ),
        )
        db.reference.updateChildren(updates).await()
    }

    private fun nspWire(s: WellnessStatus) = when (s) {
        WellnessStatus.SUSPECT -> 1
        WellnessStatus.PATHOLOGIC -> 2
        else -> 0
    }
}
