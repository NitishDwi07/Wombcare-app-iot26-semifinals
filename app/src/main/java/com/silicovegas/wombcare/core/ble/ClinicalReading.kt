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
    /**
     * Extended CTG analytics — TRANSMITTED as of payload v3 (firmware Wombcare_8PreFinal),
     * decoded to their real units here. Null on v1/v2 devices, and null on any window the
     * device flagged as a sensor fault (the values would be meaningless zeros). See
     * docs/BLE_CONTRACT.md §5.
     */
    val meanHrBpm: Int? = null,
    /** Mean short-term variability, bpm. */
    val mstvBpm: Double? = null,
    /** Mean long-term variability, bpm. */
    val mltvBpm: Double? = null,
    /** Accelerations per minute. */
    val accelPerMin: Double? = null,
    /** Decelerations per minute. */
    val decelPerMin: Double? = null,
    /** Standard deviation of FHR, bpm (firmware sends SD, not variance). */
    val hrSdBpm: Double? = null,
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

    /**
     * Motion shown to the user.
     *
     * - If the device sends an explicit motion byte (payload v2), trust it.
     * - Otherwise (v1, the current firmware) DERIVE it from [confidencePercent].
     *   Confidence = ML confidence × IMU trust, and maternal movement pulls IMU trust — and
     *   therefore confidence — down (this is also stated in the BLE spec: "if motion is
     *   WALKING, confidence will be low because the signal is unreliable during movement").
     *   So high confidence ⇒ still, lower ⇒ movement. This keeps the payload at 8 bytes —
     *   no extra IMU bytes on the wire — and does the contextualising on the phone, which is
     *   the only place with the thresholds. The [signalLow] flag, when the firmware sets it,
     *   is a direct "movement/unreliable" signal and forces WALKING.
     *
     *   NOTE: this is an approximation — confidence also drops on genuine ML uncertainty, not
     *   only movement. A dedicated motion byte (v2) is more precise; until then this is the
     *   honest best-effort from the bytes we have.
     */
    val motionDisplay: MotionDisplay
        get() = when (motionState) {
            MotionState.RESTING -> MotionDisplay.RESTING
            MotionState.SITTING -> MotionDisplay.SITTING
            MotionState.WALKING -> MotionDisplay.WALKING
            MotionState.UNKNOWN -> MotionDisplay.UNKNOWN
            null -> when {
                isPlaceholder -> MotionDisplay.UNKNOWN
                signalLow -> MotionDisplay.WALKING
                confidencePercent >= MOTION_RESTING_MIN_CONFIDENCE -> MotionDisplay.RESTING
                confidencePercent >= MOTION_SITTING_MIN_CONFIDENCE -> MotionDisplay.SITTING
                else -> MotionDisplay.WALKING
            }
        }

    private companion object {
        /** Confidence at/above this ⇒ still (Resting). */
        const val MOTION_RESTING_MIN_CONFIDENCE = 85
        /** Confidence in [SITTING_MIN, RESTING_MIN) ⇒ slight movement (Sitting). Below ⇒ Walking. */
        const val MOTION_SITTING_MIN_CONFIDENCE = 65
    }
}
