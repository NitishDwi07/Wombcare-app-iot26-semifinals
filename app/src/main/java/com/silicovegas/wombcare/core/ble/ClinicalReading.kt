package com.silicovegas.wombcare.core.ble

/** Device classification. `UNKNOWN` guards against a value the firmware shouldn't send. */
enum class WellnessStatus { NORMAL, SUSPECT, PATHOLOGIC, UNKNOWN }

/** Maternal motion. Only transmitted by payload v2; see docs/BLE_CONTRACT.md §4. */
enum class MotionState { RESTING, SITTING, WALKING, UNKNOWN }

/**
 * What the UI is allowed to say about motion.
 *
 * Payload v1 carries no motion byte — only a single `ACTIVE` flag bit — so the honest
 * v1 answer is `RESTING` or `ACTIVE`, never `SITTING`/`WALKING`.
 */
enum class MotionDisplay { RESTING, SITTING, WALKING, ACTIVE, UNKNOWN }

/**
 * One decoded 60-second clinical window.
 *
 * Field-by-field provenance is in docs/BLE_CONTRACT.md §3. Two rules are encoded in the
 * types themselves rather than left to callers:
 *  - [fhrBpm] is nullable because the firmware sends `0` to mean *no lock*, not zero bpm.
 *  - [motionState] / [batteryPercent] are nullable because payload v1 does not send them.
 */
data class ClinicalReading(
    val payloadVersion: Int,
    val status: WellnessStatus,
    val confidencePercent: Int,
    /** Baseline fetal heart rate in bpm, or `null` when the device reported no valid lock. */
    val fhrBpm: Int?,
    /** Kicks in *this window only*. The session total is the app's running sum. */
    val kickCountInWindow: Int,
    val alert: Boolean,
    val signalLow: Boolean,
    val motherActive: Boolean,
    /** Device minutes since boot (payload bytes 6-7). Ordering and gap detection only. */
    val deviceMinute: Int,
    val motionState: MotionState?,
    val batteryPercent: Int?,
    /** Phone wall clock at receipt — authoritative for display and storage. */
    val receivedAtEpochMillis: Long,
) {
    val hasValidFhr: Boolean get() = fhrBpm != null

    /**
     * True for the all-zero frame the firmware pushes the instant a client subscribes
     * before any window has completed (`wombcare_ble.c` sends `s_last_payload` on CCCD
     * enable). Render "waiting for first reading" — never "FHR 0, Normal".
     */
    val isPlaceholder: Boolean
        get() = fhrBpm == null &&
            kickCountInWindow == 0 &&
            confidencePercent == 0 &&
            deviceMinute == 0

    /** Confidence is untrustworthy while the mother is moving; the UI must say so. */
    val confidenceIsTrustworthy: Boolean get() = !signalLow

    val motionDisplay: MotionDisplay
        get() = when (motionState) {
            MotionState.RESTING -> MotionDisplay.RESTING
            MotionState.SITTING -> MotionDisplay.SITTING
            MotionState.WALKING -> MotionDisplay.WALKING
            MotionState.UNKNOWN -> MotionDisplay.UNKNOWN
            null -> if (motherActive) MotionDisplay.ACTIVE else MotionDisplay.RESTING
        }
}
