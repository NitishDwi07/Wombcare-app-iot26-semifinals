package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ble.WombCareGatt

/** What the app should surface for a given window — the output of clinical-safety gating. */
enum class AlertDecision {
    /** Nothing unusual. */
    NONE,

    /** Suspect (or a lone Pathologic not yet confirmed): quiet in-app banner, no push. */
    ADVISORY,

    /** Reading is Pathologic-ish but the signal can't be trusted: ask her to re-check. */
    RECHECK,

    /** Confirmed Pathologic: raise the alert + notification. */
    ALERT,
}

/**
 * Turns the raw per-window classification into what the app is allowed to *do* about it.
 *
 * Two guards, both because a false alarm to a pregnant woman at 2 a.m. is a real harm, not
 * a UX nitpick:
 *
 *  1. **Confirm-and-persist** — a single Pathologic window does NOT alert. It takes
 *     [WombCareGatt.ALERT_CONSECUTIVE_WINDOWS] consecutive Pathologic windows, so one noisy
 *     minute can't trigger a panic. The counter resets the moment a window comes back
 *     non-Pathologic.
 *  2. **Signal gating** — if the device flagged the signal as low, or confidence is below
 *     [WombCareGatt.ALERT_MIN_CONFIDENCE], a Pathologic reading becomes [RECHECK] ("please
 *     sit still and re-check") instead of an alarm. Maternal movement makes the
 *     classification unreliable, and the device tells us when that's happening.
 *
 * This is a pure state machine — feed it readings in order, read [decisionFor]'s return.
 * It holds only a small counter so it survives being recreated with the last count.
 */
class AlertEvaluator(private var consecutivePathologic: Int = 0) {

    val pathologicStreak: Int get() = consecutivePathologic

    fun decisionFor(reading: ClinicalReading): AlertDecision {
        // A placeholder (all-zero subscribe frame) is not a clinical reading at all.
        if (reading.isPlaceholder) {
            consecutivePathologic = 0
            return AlertDecision.NONE
        }

        return when (reading.status) {
            WellnessStatus.PATHOLOGIC -> {
                consecutivePathologic++
                val trustworthy = !reading.signalLow &&
                    reading.confidencePercent >= WombCareGatt.ALERT_MIN_CONFIDENCE
                when {
                    !trustworthy -> AlertDecision.RECHECK
                    consecutivePathologic >= WombCareGatt.ALERT_CONSECUTIVE_WINDOWS ->
                        AlertDecision.ALERT
                    else -> AlertDecision.ADVISORY // first Pathologic window: not yet confirmed
                }
            }

            WellnessStatus.SUSPECT -> {
                consecutivePathologic = 0
                AlertDecision.ADVISORY
            }

            WellnessStatus.NORMAL, WellnessStatus.UNKNOWN -> {
                consecutivePathologic = 0
                AlertDecision.NONE
            }
        }
    }
}
