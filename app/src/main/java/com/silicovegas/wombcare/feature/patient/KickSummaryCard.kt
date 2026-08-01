package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.feature.patient.charts.KickBars

/**
 * Kicks, numbers first. A big cumulative total (the figure a mother actually cares about),
 * then the busiest minute and a per-minute average, then the per-minute bars — which now
 * carry their own value labels. So the section reads as numbers with a chart, not a chart
 * you have to interpret.
 */
@Composable
fun KickSummaryCard(readings: List<ClinicalReading>, modifier: Modifier = Modifier) {
    val real = readings.filter { !it.isPlaceholder }
    val total = real.sumOf { it.kickCountInWindow }
    val peak = real.maxOfOrNull { it.kickCountInWindow } ?: 0
    val minutes = real.size.coerceAtLeast(1)
    val avgPerMin = total.toDouble() / minutes

    SectionCard(title = "Kicks", modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.SportsSoccer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.iconLg),
            )
            Spacer(Modifier.width(Spacing.md))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$total",
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "kicks this session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniMetric("Busiest minute", "$peak", "kicks", Modifier.weight(1f))
            MiniMetric("Average", trimTrailingZero(avgPerMin), "per min", Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.md))
        KickBars(real)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** "1.5" not "1.50", "2" not "2.0" — the least noisy way to show an average. */
private fun trimTrailingZero(v: Double): String {
    val oneDp = (Math.round(v * 10.0) / 10.0)
    return if (oneDp % 1.0 == 0.0) oneDp.toInt().toString() else oneDp.toString()
}
