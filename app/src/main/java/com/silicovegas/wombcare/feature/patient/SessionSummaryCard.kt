package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.device.statsOf
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * A clean numeric summary of the session — the numbers the charts only imply. Deliberately
 * text/number-first (no plots): average and range of FHR, total kicks, minutes monitored,
 * and how those minutes split across Normal / Suspect / Pathologic.
 *
 * Every figure uses `stats`, which excludes invalid FHR windows, so nothing here is dragged
 * toward zero by a "--" minute.
 */
@Composable
fun SessionSummaryCard(readings: List<ClinicalReading>, modifier: Modifier = Modifier) {
    val s = statsOf(readings)
    val status = LocalStatusColors.current

    SectionCard(title = "This session", modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Avg heart rate", s.averageFhr?.let { "$it" } ?: "--", "BPM", Modifier.weight(1f))
            Metric(
                "Range",
                if (s.minFhr != null && s.maxFhr != null) "${s.minFhr}–${s.maxFhr}" else "--",
                "BPM",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Total kicks", "${s.totalKicks}", "this session", Modifier.weight(1f))
            Metric("Monitored", "${s.durationMinutes}", "min", Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            "TIME BY STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatusMinutes("Normal", s.normalMinutes, status.normal, Modifier.weight(1f))
            StatusMinutes("Suspect", s.suspectMinutes, status.suspect, Modifier.weight(1f))
            StatusMinutes("Pathologic", s.pathologicMinutes, status.pathologic, Modifier.weight(1f))
        }
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFeatureSettings = "tnum",
                ),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun StatusMinutes(
    label: String,
    minutes: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = androidx.compose.ui.Alignment.Start) {
        Text(
            "$minutes",
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = color,
        )
        Text(
            "$label · min",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
