package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryUnknown
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * Battery as a compact, purposeful card — an icon that reflects the level, the percentage,
 * and a thin fill bar — instead of a big empty tile showing "--". When the device doesn't
 * send battery (payload v1), it states that plainly rather than looking broken.
 *
 * Colour follows the level: calm below-full is neutral, amber under 20%, red under 10% — so
 * a low battery is noticeable without being alarming about the wrong thing.
 */
@Composable
fun BatteryCard(percent: Int?, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val accent: Color = when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        percent < 10 -> status.pathologic
        percent < 20 -> status.suspect
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        percent == null -> Icons.Rounded.BatteryUnknown
        percent < 10 -> Icons.Rounded.BatteryAlert
        else -> Icons.Rounded.BatteryFull
    }
    val spoken = if (percent == null) "Battery, not sent by this device" else "Battery, $percent percent"

    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        shape = RoundedCornerShape(Radii.tile),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Sizes.iconMd))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Battery", style = MaterialTheme.typography.titleMedium)
                }
                if (percent != null) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        color = accent,
                    )
                } else {
                    Text(
                        "Not sent by this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (percent != null) {
                Spacer(Modifier.height(Spacing.md))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}
