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
    /**
     * Number of windows in the session. The Wombcare_8PreFinal firmware pushes ONE window
     * per SECOND (the DSP trigger fires each second, sliding a 60 s buffer — see app.c), so
     * this count is effectively **seconds monitored**, not minutes. Named `...Windows` to stop
     * anyone re-introducing the old "one window = one minute" assumption.
     */
    val durationWindows: Int,
    val normalWindows: Int,
    val suspectWindows: Int,
    val pathologicWindows: Int,
) {
    val hasFhr: Boolean get() = averageFhr != null
}

/**
 * Elapsed time as a rolling clock: seconds until a minute, then "M min S sec", then past
 * 59 min 59 sec, "H hr M min". Shared by the patient dashboard and the session summary so a
 * duration reads the same everywhere.
 */
fun formatDurationSeconds(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "$h hr $m min"
        m > 0 -> "$m min $s sec"
        else -> "$s sec"
    }
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
        durationWindows = real.size, // one window per SECOND on current firmware
        normalWindows = real.count { it.status == WellnessStatus.NORMAL },
        suspectWindows = real.count { it.status == WellnessStatus.SUSPECT },
        pathologicWindows = real.count { it.status == WellnessStatus.PATHOLOGIC },
    )
}

fun SessionSnapshot.stats(): SessionStats = statsOf(readings)

private fun Double.roundToIntCompat(): Int = (this + 0.5).toInt()
