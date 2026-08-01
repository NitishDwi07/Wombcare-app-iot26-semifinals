package com.silicovegas.wombcare.core.data

import com.google.firebase.database.DataSnapshot
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.MotionState
import com.silicovegas.wombcare.core.ble.WellnessStatus

/**
 * Maps a [ClinicalReading] to/from its Realtime Database representation.
 *
 * Encode and decode live together so the doctor reconstructs exactly what the mother's
 * phone stored — including the honest nulls: an invalid FHR is written as an ABSENT key,
 * not 0, so `fhrBpm` round-trips back to null and the doctor's chart breaks the line at the
 * same minute the mother's did.
 */
object ReadingWire {

    fun toMap(r: ClinicalReading): Map<String, Any?> = buildMap {
        put("recordedAt", r.receivedAtEpochMillis)
        put("deviceMinute", r.deviceMinute)
        r.fhrBpm?.let { put("fhrBpm", it) } // absent when no lock
        put("kickCount", r.kickCountInWindow)
        put("nsp", r.status.wire())
        put("confidence", r.confidencePercent)
        put("signalLow", r.signalLow)
        put("motherActive", r.motherActive)
        r.motionState?.let { put("motionState", it.wire()) }
        r.batteryPercent?.let { put("batteryPct", it) }
    }

    fun fromSnapshot(s: DataSnapshot): ClinicalReading? {
        if (!s.exists()) return null
        fun int(key: String): Int? = s.child(key).getValue(Long::class.java)?.toInt()
        fun bool(key: String): Boolean = s.child(key).getValue(Boolean::class.java) ?: false
        return ClinicalReading(
            payloadVersion = 1,
            status = statusOf(int("nsp") ?: 0),
            confidencePercent = int("confidence") ?: 0,
            fhrBpm = int("fhrBpm"), // absent -> null, exactly as stored
            kickCountInWindow = int("kickCount") ?: 0,
            alert = false,
            signalLow = bool("signalLow"),
            motherActive = bool("motherActive"),
            deviceMinute = int("deviceMinute") ?: 0,
            motionState = int("motionState")?.let(::motionOf),
            batteryPercent = int("batteryPct"),
            receivedAtEpochMillis = s.child("recordedAt").getValue(Long::class.java) ?: 0L,
        )
    }

    private fun WellnessStatus.wire(): Int = when (this) {
        WellnessStatus.NORMAL, WellnessStatus.UNKNOWN -> 0
        WellnessStatus.SUSPECT -> 1
        WellnessStatus.PATHOLOGIC -> 2
    }

    private fun MotionState.wire(): Int = when (this) {
        MotionState.RESTING, MotionState.UNKNOWN -> 0
        MotionState.SITTING -> 1
        MotionState.WALKING -> 2
    }

    private fun statusOf(v: Int) = when (v) {
        1 -> WellnessStatus.SUSPECT
        2 -> WellnessStatus.PATHOLOGIC
        else -> WellnessStatus.NORMAL
    }

    private fun motionOf(v: Int) = when (v) {
        1 -> MotionState.SITTING
        2 -> MotionState.WALKING
        else -> MotionState.RESTING
    }
}
