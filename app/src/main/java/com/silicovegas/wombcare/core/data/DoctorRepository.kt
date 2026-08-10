package com.silicovegas.wombcare.core.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.data.model.AlertRecord
import com.silicovegas.wombcare.core.data.model.CareLinkStatus
import com.silicovegas.wombcare.core.data.model.LiveStatus
import com.silicovegas.wombcare.core.data.model.PatientListEntry
import com.silicovegas.wombcare.core.data.model.PatientThresholds
import com.silicovegas.wombcare.core.data.model.SessionSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The doctor's read side. Everything here is a live [Flow] backed by RTDB listeners, and
 * every path it touches is one the security rules only open to a doctor with an ACTIVE
 * link — so if a mother revokes access mid-view, the listener is cancelled by the rules and
 * the stream simply ends (see [valueFlow]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DoctorRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {
    /**
     * The doctor's patient list: their active links, joined to each patient's live node.
     * Emits a fresh list whenever any linked patient's `liveStatus` changes, so the live
     * dots update in place.
     */
    fun linkedPatients(doctorUid: String): Flow<List<PatientListEntry>> =
        activePatientUids(doctorUid).flatMapLatest { uids ->
            if (uids.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(uids.map { patientEntry(it) }) { it.filterNotNull().toList() }
            }
        }

    private fun activePatientUids(doctorUid: String): Flow<List<String>> =
        db.getReference(DbPaths.careLinksForDoctor(doctorUid)).valueFlow().map { snap ->
            snap.children.mapNotNull { child ->
                val status = CareLinkStatus.from(child.child("status").getValue(String::class.java))
                child.key.takeIf { status == CareLinkStatus.ACTIVE }
            }
        }

    private fun patientEntry(patientUid: String): Flow<PatientListEntry?> =
        db.getReference(DbPaths.patient(patientUid)).valueFlow().map { snap ->
            if (!snap.exists()) return@map null
            PatientListEntry(
                patientUid = patientUid,
                fullName = snap.child("fullName").getValue(String::class.java) ?: "Patient",
                pregnancyWeeks = snap.child("pregnancyWeeks").getValue(Long::class.java)?.toInt(),
                live = snap.child("liveStatus").toLiveStatus(),
            )
        }

    /** The live summary map, for the detail header's "LIVE / last seen" state. */
    fun patientLive(patientUid: String): Flow<LiveStatus?> =
        db.getReference(DbPaths.liveStatus(patientUid)).valueFlow().map { it.toLiveStatus() }

    /**
     * All alerts across the doctor's linked patients, newest first — the doctor's inbox.
     * Recombines whenever any patient's alert set or name changes.
     */
    fun alertsFeed(doctorUid: String): Flow<List<AlertRecord>> =
        activePatientUids(doctorUid).flatMapLatest { uids ->
            if (uids.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(uids.map { alertsForPatient(it) }) { arrays ->
                    arrays.toList().flatten().sortedByDescending { it.createdAt }
                }
            }
        }

    private fun alertsForPatient(patientUid: String): Flow<List<AlertRecord>> =
        combine(
            db.getReference(DbPaths.patient(patientUid)).child("fullName").valueFlow(),
            db.getReference(DbPaths.alerts(patientUid)).valueFlow(),
        ) { nameSnap, alertsSnap ->
            val name = nameSnap.getValue(String::class.java) ?: "Patient"
            alertsSnap.children.mapNotNull { a ->
                val createdAt = a.child("createdAt").getValue(Long::class.java) ?: return@mapNotNull null
                AlertRecord(
                    id = a.key ?: return@mapNotNull null,
                    patientUid = patientUid,
                    patientName = name,
                    sessionId = a.child("sessionId").getValue(String::class.java).orEmpty(),
                    nsp = a.child("nsp").getValue(Long::class.java)?.toInt() ?: 2,
                    createdAt = createdAt,
                    acknowledgedBy = a.child("acknowledgedBy").getValue(String::class.java),
                    acknowledgedAt = a.child("acknowledgedAt").getValue(Long::class.java),
                )
            }
        }

    /**
     * The doctor's per-patient alert thresholds. Falls back to [PatientThresholds.DEFAULT]
     * (the literature-based values) whenever a field — or the whole node — is unset, so the
     * screen always has a complete, sensible set to show and edit.
     */
    fun thresholds(doctorUid: String, patientUid: String): Flow<PatientThresholds> =
        db.getReference(DbPaths.doctorThresholds(doctorUid, patientUid)).valueFlow().map { snap ->
            val d = PatientThresholds.DEFAULT
            fun int(key: String, fallback: Int) =
                snap.child(key).getValue(Long::class.java)?.toInt() ?: fallback
            PatientThresholds(
                fhrLowBpm = int("fhrLow", d.fhrLowBpm),
                fhrHighBpm = int("fhrHigh", d.fhrHighBpm),
                minVariabilityBpm = int("minVar", d.minVariabilityBpm),
                minConfidencePct = int("minConf", d.minConfidencePct),
            )
        }

    /** Save the doctor's thresholds for a patient (under the doctor's own node). */
    suspend fun setThresholds(doctorUid: String, patientUid: String, t: PatientThresholds) {
        db.getReference(DbPaths.doctorThresholds(doctorUid, patientUid)).setValue(
            mapOf(
                "fhrLow" to t.fhrLowBpm,
                "fhrHigh" to t.fhrHighBpm,
                "minVar" to t.minVariabilityBpm,
                "minConf" to t.minConfidencePct,
            ),
        ).await()
    }

    /** Doctor acknowledges an alert — the only clinical write a doctor is allowed. */
    suspend fun acknowledgeAlert(patientUid: String, alertId: String, doctorUid: String, nowMillis: Long) {
        db.reference.updateChildren(
            mapOf(
                "${DbPaths.alert(patientUid, alertId)}/acknowledgedBy" to doctorUid,
                "${DbPaths.alert(patientUid, alertId)}/acknowledgedAt" to nowMillis,
            ),
        ).await()
    }

    /**
     * Every session summary for a patient, newest first — the input to the doctor's history
     * and trends. One lightweight node per session (no per-minute readings), so this stays
     * cheap even over weeks. Rules already grant a linked doctor the whole `patients/$uid`
     * subtree, so no extra permission is needed.
     */
    fun recentSessions(patientUid: String): Flow<List<SessionSummary>> =
        db.getReference(DbPaths.sessions(patientUid)).valueFlow().map { snap ->
            snap.children.mapNotNull { s ->
                val startedAt = s.child("startedAt").getValue(Long::class.java) ?: return@mapNotNull null
                SessionSummary(
                    sessionId = s.key ?: return@mapNotNull null,
                    startedAt = startedAt,
                    readingCount = s.child("readingCount").getValue(Long::class.java)?.toInt() ?: 0,
                    avgFhr = s.child("avgFhr").getValue(Long::class.java)?.toInt(),
                    totalKicks = s.child("totalKicks").getValue(Long::class.java)?.toInt() ?: 0,
                    worstNsp = s.child("worstNsp").getValue(Long::class.java)?.toInt() ?: 0,
                )
            }.sortedByDescending { it.startedAt }
        }

    /** Readings for one session, ordered by device minute — feeds the same charts as P3. */
    fun sessionReadings(patientUid: String, sessionId: String): Flow<List<ClinicalReading>> =
        db.getReference(DbPaths.readings(patientUid, sessionId)).valueFlow().map { snap ->
            snap.children.mapNotNull { ReadingWire.fromSnapshot(it) }
                .sortedBy { it.deviceMinute }
        }

    private fun DataSnapshot.toLiveStatus(): LiveStatus? {
        if (!exists()) return null
        return LiveStatus(
            sessionId = child("sessionId").getValue(String::class.java),
            nsp = child("nsp").getValue(Long::class.java)?.toInt() ?: 0,
            fhrBpm = child("fhrBpm").getValue(Long::class.java)?.toInt(),
            kickTotal = child("kickTotal").getValue(Long::class.java)?.toInt() ?: 0,
            confidence = child("confidence").getValue(Long::class.java)?.toInt() ?: 0,
            lastReadingAt = child("lastReadingAt").getValue(Long::class.java) ?: 0L,
        )
    }
}
