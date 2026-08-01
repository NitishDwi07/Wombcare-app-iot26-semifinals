package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus

/** Result of feeding one window into [SessionEngine]. */
data class SessionUpdate(
    val snapshot: SessionSnapshot,
    val startedNewSession: Boolean,
    val alert: AlertEvent?,
)

/**
 * Assembles the stream of 60-second windows into a coherent session.
 *
 * Responsibilities, all order-dependent and therefore kept in one place:
 *  - **Reboot split.** The device's `timestamp` is minutes-since-boot, not a clock. If it
 *    jumps backwards, the device restarted — that's a new session, not a continuation, so a
 *    fresh [SessionSnapshot] begins and the alert streak resets. (Placeholder frames, which
 *    always carry minute 0, are exempt so a late-subscribe frame can't fake a reboot.)
 *  - **Aggregation** per [SessionSnapshot]'s rules (invalid FHR excluded, kicks summed).
 *  - **Alerts** via [AlertEvaluator]; an [AlertEvent] is emitted only when the decision
 *    rises to something the app should act on, and only on the transition into it.
 *
 * Pure and synchronous: the device layer owns the coroutine that pumps readings in.
 * [now] and [newSessionId] are injected so tests are deterministic.
 */
class SessionEngine(
    private val now: () -> Long,
    private val newSessionId: () -> String,
) {
    private var snapshot: SessionSnapshot? = null
    private var evaluator = AlertEvaluator()
    private var lastDeviceMinute: Int? = null
    private var lastActedDecision: AlertDecision = AlertDecision.NONE

    val current: SessionSnapshot? get() = snapshot

    fun onReading(reading: ClinicalReading): SessionUpdate {
        val rebooted = !reading.isPlaceholder &&
            lastDeviceMinute != null &&
            reading.deviceMinute < lastDeviceMinute!!

        val startNew = snapshot == null || rebooted
        if (startNew) {
            snapshot = SessionSnapshot(
                sessionId = newSessionId(),
                startedAtEpochMillis = now(),
            )
            evaluator = AlertEvaluator()
            lastActedDecision = AlertDecision.NONE
        }
        if (!reading.isPlaceholder) lastDeviceMinute = reading.deviceMinute

        val decision = evaluator.decisionFor(reading)
        val base = snapshot!!
        val readings = base.readings + reading

        val validFhr = readings.mapNotNull { it.fhrBpm }
        val updated = base.copy(
            readings = readings,
            latest = reading,
            totalKicks = base.totalKicks + reading.kickCountInWindow,
            averageFhr = if (validFhr.isEmpty()) null else validFhr.average().toInt(),
            worstStatus = worstOf(base.worstStatus, reading.status),
            lastDecision = decision,
        )
        snapshot = updated

        // Emit an alert event only when the decision escalates into ALERT/RECHECK/ADVISORY
        // it wasn't already sitting on — so the app notifies once per episode, not per minute.
        val alert = if (decision != lastActedDecision && decision.isActionable()) {
            AlertEvent(updated.sessionId, reading, decision)
        } else {
            null
        }
        lastActedDecision = decision

        return SessionUpdate(updated, startNew, alert)
    }

    /** End the current session (mother pressed stop / disconnected). Next reading starts fresh. */
    fun endSession() {
        snapshot = null
        lastDeviceMinute = null
        evaluator = AlertEvaluator()
        lastActedDecision = AlertDecision.NONE
    }

    private fun AlertDecision.isActionable() =
        this == AlertDecision.ALERT || this == AlertDecision.RECHECK ||
            this == AlertDecision.ADVISORY

    private fun worstOf(a: WellnessStatus, b: WellnessStatus): WellnessStatus =
        if (severity(b) > severity(a)) b else a

    private fun severity(s: WellnessStatus): Int = when (s) {
        WellnessStatus.NORMAL, WellnessStatus.UNKNOWN -> 0
        WellnessStatus.SUSPECT -> 1
        WellnessStatus.PATHOLOGIC -> 2
    }
}
