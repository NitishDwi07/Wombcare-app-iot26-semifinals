package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.device.formatDurationSeconds
import com.silicovegas.wombcare.core.device.statsOf
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors

/**
 * A clean numeric summary of the session — average and range of FHR, total kicks, and time
 * monitored, with the session's overall status shown as one small chip beside the title.
 * Every figure uses `stats`, which excludes invalid FHR windows, so nothing here is dragged
 * toward zero by a "--" reading.
 */
@Composable
fun SessionSummaryCard(readings: List<ClinicalReading>, modifier: Modifier = Modifier) {
    val s = statsOf(readings)
    val status = LocalStatusColors.current

    val (word, color) = when {
        s.pathologicWindows > 0 -> stringResource(R.string.status_pathologic) to status.pathologic
        s.suspectWindows > 0 -> stringResource(R.string.status_suspect) to status.suspect
        else -> stringResource(R.string.status_normal) to status.normal
    }

    SectionCard(
        title = "Session summary",
        trailing = { StatusChip(word, color) },
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Avg heart rate", s.averageFhr?.let { "$it" } ?: "--", "BPM", Modifier.weight(1f))
            Metric(
                "Range",
                if (s.minFhr != null && s.maxFhr != null) "${s.minFhr}–${s.maxFhr}" else "--",
                "BPM",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Total kicks", "${s.totalKicks}", "this session", Modifier.weight(1f))
            // One window per SECOND on current firmware, so this is seconds, rolled up.
            Metric("Time monitored", formatDurationSeconds(s.durationWindows.toLong()), "", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusChip(word: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(word, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}
