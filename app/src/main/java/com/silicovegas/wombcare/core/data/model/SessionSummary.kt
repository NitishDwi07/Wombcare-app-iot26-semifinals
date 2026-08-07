package com.silicovegas.wombcare.core.data.model

/**
 * The rolled-up record the patient's phone writes once per session (see
 * `PatientSyncRepository.pushWindow`). The doctor reads these to show history and trends
 * without pulling every individual reading — one node per session.
 */
data class SessionSummary(
    val sessionId: String,
    val startedAt: Long,
    val readingCount: Int,
    val avgFhr: Int?,
    val totalKicks: Int,
    /** 0 Normal · 1 Suspect · 2 Pathologic · 3 analysis-failed — the worst class that session. */
    val worstNsp: Int,
) {
    /** A session the doctor should look at: anything the device flagged above Normal. */
    val isAnomaly: Boolean get() = worstNsp == 1 || worstNsp == 2
}
