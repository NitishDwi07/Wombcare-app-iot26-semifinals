package com.silicovegas.wombcare.feature.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ble.WombCareGatt
import com.silicovegas.wombcare.core.device.statsOf
import com.silicovegas.wombcare.core.ui.components.BigNumber
import com.silicovegas.wombcare.core.ui.components.DisclaimerFootnote
import com.silicovegas.wombcare.core.ui.components.EmptyState
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.components.StatTile
import com.silicovegas.wombcare.core.ui.components.StatusHeroCard
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.feature.patient.charts.FhrTrendChart
import com.silicovegas.wombcare.feature.patient.charts.NspTimelineStrip

/**
 * The doctor's live view of a patient's session — the same charts the mother sees (P3),
 * reused verbatim, at clinical density. The header says "LIVE" only when the last reading
 * is under the dropout threshold; otherwise it reports "last seen", so a frozen chart is
 * never mistaken for a live one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorPatientDetailScreen(
    onBack: () -> Unit,
    vm: DoctorDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val live = ui.live
    val isLive = live != null && live.lastReadingAt > 0 &&
        (now - live.lastReadingAt) <= WombCareGatt.DROPOUT_MILLIS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        val readings = ui.readings
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StatusHeroCard(
                status = statusOf(live?.nsp ?: 0),
                subtitle = when {
                    live == null -> "No readings shared yet"
                    isLive -> "LIVE · updated just now"
                    else -> lastSeen(live.lastReadingAt, now)
                },
                waiting = live == null,
            )

            if (readings.isEmpty()) {
                EmptyState(
                    title = "No readings in this session yet",
                    message = "When the patient is monitoring, her readings will appear here " +
                        "each minute.",
                )
                DisclaimerFootnote()
                return@Column
            }

            val latestReading = readings.lastOrNull()
            val stats = statsOf(readings)

            // LB — Baseline Fetal Heart Rate, with the live waveform + NSP timeline beneath.
            SectionCard(title = "Baseline fetal heart rate (LB)") {
                BigNumber(
                    value = (latestReading?.fhrBpm ?: stats.averageFhr)?.toString(),
                    unit = "BPM",
                )
                Spacer(Modifier.height(Spacing.sm))
                FhrTrendChart(readings)
                Spacer(Modifier.height(Spacing.sm))
                NspTimelineStrip(readings)
            }

            // The values the device actually transmits: FHR, kicks (FM), AI confidence.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "Fetal heart rate",
                    value = latestReading?.fhrBpm?.toString(),
                    unit = "BPM",
                    icon = Icons.Rounded.MonitorHeart,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Fetal movements",
                    value = (live?.kickTotal ?: stats.totalKicks).toString(),
                    unit = "kicks",
                    icon = Icons.Rounded.SportsSoccer,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "AI confidence",
                    value = latestReading?.confidencePercent?.toString(),
                    unit = "%",
                    icon = Icons.Rounded.Speed,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Device battery",
                    value = latestReading?.batteryPercent?.toString(),
                    unit = "%",
                    icon = Icons.Rounded.BatteryFull,
                    modifier = Modifier.weight(1f),
                )
            }

            // CTG analytics — transmitted as of payload v3. "--" on older devices, or on a
            // window the device flagged as poor signal.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "MSTV",
                    value = fmt1(latestReading?.mstvBpm),
                    unit = "bpm",
                    icon = Icons.Rounded.ShowChart,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "MLTV",
                    value = fmt1(latestReading?.mltvBpm),
                    unit = "bpm",
                    icon = Icons.Rounded.ShowChart,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "Accelerations",
                    value = fmt1(latestReading?.accelPerMin),
                    unit = "/min",
                    icon = Icons.Rounded.TrendingUp,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Decelerations",
                    value = fmt1(latestReading?.decelPerMin),
                    unit = "/min",
                    icon = Icons.Rounded.TrendingDown,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(
                    label = "Mean HR",
                    value = latestReading?.meanHrBpm?.toString() ?: stats.averageFhr?.toString(),
                    unit = "BPM",
                    icon = Icons.Rounded.MonitorHeart,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "HR variability (SD)",
                    value = fmt1(latestReading?.hrSdBpm),
                    unit = "bpm",
                    icon = Icons.Rounded.Speed,
                    valueStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            com.silicovegas.wombcare.feature.patient.SessionSummaryCard(readings)
            com.silicovegas.wombcare.feature.patient.KickSummaryCard(readings)

            DisclaimerFootnote()
        }
    }
}

/** One-decimal string for a CTG figure, or null so the tile shows "--". */
private fun fmt1(d: Double?): String? = d?.let { String.format(java.util.Locale.US, "%.1f", it) }

private fun statusOf(nsp: Int) = when (nsp) {
    1 -> WellnessStatus.SUSPECT
    2 -> WellnessStatus.PATHOLOGIC
    3 -> WellnessStatus.UNKNOWN
    else -> WellnessStatus.NORMAL
}

private fun lastSeen(last: Long, now: Long): String {
    val mins = ((now - last) / 60_000L).toInt()
    return when {
        mins < 1 -> "Last seen just now"
        mins < 60 -> "Last seen $mins min ago"
        else -> "Last seen ${mins / 60} h ago"
    }
}
