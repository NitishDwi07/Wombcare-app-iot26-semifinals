package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus

/**
 * A running monitoring session, rebuilt as each window arrives.
 *
 * Kept immutable and derived so the UI just renders it, and so the same snapshot can be
 * written to Room / synced to the doctor without a second computation. Aggregates follow
 * the contract's rules:
 *  - [averageFhr] ignores invalid (null) FHR windows — a "no lock" minute must not drag the
 *    average toward zero.
 *  - [totalKicks] is the app's running SUM; the firmware only reports per-window counts.
 *  - [worstStatus] is the most severe class seen this session, for the summary card.
 */
data class SessionSnapshot(
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val readings: List<ClinicalReading> = emptyList(),
    val latest: ClinicalReading? = null,
    val totalKicks: Int = 0,
    val averageFhr: Int? = null,
    val worstStatus: WellnessStatus = WellnessStatus.NORMAL,
    val lastDecision: AlertDecision = AlertDecision.NONE,
) {
    val windowCount: Int get() = readings.size
}

/** Emitted when a window drives an alert-worthy transition, so the app can notify once. */
data class AlertEvent(
    val sessionId: String,
    val reading: ClinicalReading,
    val decision: AlertDecision,
)
