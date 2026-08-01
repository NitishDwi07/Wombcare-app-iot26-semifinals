package com.silicovegas.wombcare.core.device

import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus

/**
 * Plain numbers derived from a session's readings, so the UI can show a clean numeric
 * summary rather than asking the reader to eyeball a chart. All FHR figures ignore invalid
 * (no-lock) windows — a "--" minute never pulls an average or a min toward zero.
 *
 * Works off a `List<ClinicalReading>` so BOTH sides can use it: the patient from her live
 * session, and the doctor from the readings streamed over Firebase.
 */
data class SessionStats(
    val currentFhr: Int?,
    val averageFhr: Int?,
    val minFhr: Int?,
    val maxFhr: Int?,
    val averageConfidence: Int?,
    val totalKicks: Int,
    val durationMinutes: Int,
    val normalMinutes: Int,
    val suspectMinutes: Int,
    val pathologicMinutes: Int,
) {
    val hasFhr: Boolean get() = averageFhr != null
}

fun statsOf(readings: List<ClinicalReading>): SessionStats {
    val real = readings.filter { !it.isPlaceholder }
    val validFhr = real.mapNotNull { it.fhrBpm }
    val confidences = real.map { it.confidencePercent }

    return SessionStats(
        currentFhr = real.lastOrNull { it.hasValidFhr }?.fhrBpm,
        averageFhr = if (validFhr.isEmpty()) null else validFhr.average().roundToIntCompat(),
        minFhr = validFhr.minOrNull(),
        maxFhr = validFhr.maxOrNull(),
        averageConfidence = if (confidences.isEmpty()) null else confidences.average().roundToIntCompat(),
        totalKicks = real.sumOf { it.kickCountInWindow },
        durationMinutes = real.size, // one window per minute
        normalMinutes = real.count { it.status == WellnessStatus.NORMAL },
        suspectMinutes = real.count { it.status == WellnessStatus.SUSPECT },
        pathologicMinutes = real.count { it.status == WellnessStatus.PATHOLOGIC },
    )
}

fun SessionSnapshot.stats(): SessionStats = statsOf(readings)

private fun Double.roundToIntCompat(): Int = (this + 0.5).toInt()
