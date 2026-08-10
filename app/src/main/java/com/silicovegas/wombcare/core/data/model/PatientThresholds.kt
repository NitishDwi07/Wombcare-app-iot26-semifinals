package com.silicovegas.wombcare.core.data.model

/**
 * The per-patient alert thresholds a doctor can tune. Every value defaults to a
 * literature-based figure so a doctor who changes nothing still gets clinically sensible
 * behaviour, and can move any of them for a specific patient.
 *
 * Default provenance (all standard antenatal CTG references):
 *  - [fhrLowBpm] = 110, [fhrHighBpm] = 160 — the FIGO 2015 consensus and NICHD define a
 *    NORMAL baseline fetal heart rate as 110–160 bpm; below 110 is bradycardia, above 160 is
 *    tachycardia.
 *  - [minVariabilityBpm] = 5 — FIGO/NICHD call normal baseline variability 5–25 bpm and
 *    "reduced" (a warning sign) below 5 bpm. The device reports variability (MSTV) in bpm, so
 *    the threshold is applied directly to it.
 *  - [minConfidencePct] = 40 — matches the app's existing `ALERT_MIN_CONFIDENCE`: below this,
 *    a reading is treated as too unreliable (usually maternal movement) to alarm on.
 *
 * These are decision-support defaults for a wellness wearable, NOT a diagnostic standard.
 */
data class PatientThresholds(
    val fhrLowBpm: Int = 110,
    val fhrHighBpm: Int = 160,
    val minVariabilityBpm: Int = 5,
    val minConfidencePct: Int = 40,
) {
    companion object {
        val DEFAULT = PatientThresholds()
    }
}
