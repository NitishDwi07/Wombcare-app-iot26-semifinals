package com.silicovegas.wombcare.core.data

import com.google.firebase.database.FirebaseDatabase
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a confirmed-alert record to the patient's node so an approved doctor's feed can
 * pick it up. Called only when a session is being shared — an alert on an unshared session
 * stays a local notification and never leaves the phone, same privacy rule as readings.
 *
 * The record is intentionally minimal (which session, what severity, when) — the doctor
 * opens the patient to see the readings behind it; the alert row is just the pointer.
 */
@Singleton
class AlertRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {
    suspend fun writeAlert(patientUid: String, sessionId: String, reading: ClinicalReading) {
        val ref = db.getReference(DbPaths.alerts(patientUid)).push()
        ref.setValue(
            mapOf(
                "sessionId" to sessionId,
                "nsp" to nspWire(reading.status),
                "createdAt" to reading.receivedAtEpochMillis,
            ),
        ).await()
    }

    private fun nspWire(s: WellnessStatus) = when (s) {
        WellnessStatus.SUSPECT -> 1
        WellnessStatus.PATHOLOGIC -> 2
        else -> 0
    }
}
