package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.device.SessionStats
import com.silicovegas.wombcare.core.ui.components.SectionCard
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Spacing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * The mother's "Real-time inference status" — the share of this session's minutes the device
 * classified Normal / Suspect / Pathologic, as three ring gauges (matching the summary
 * dashboard design). These are REAL numbers: [SessionStats.normalMinutes] etc. over the total,
 * so the rings always sum to 100% and reflect only what the device actually reported.
 */
@Composable
fun InferenceStatusCard(stats: SessionStats, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val total = (stats.normalMinutes + stats.suspectMinutes + stats.pathologicMinutes)
        .coerceAtLeast(1)

    SectionCard(title = "Real-time inference status", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InferenceRing(
                caption = "Normal",
                index = 1,
                percent = pct(stats.normalMinutes, total),
                color = status.normal,
                track = status.normalContainer,
                modifier = Modifier.weight(1f),
            )
            InferenceRing(
                caption = "Suspect",
                index = 2,
                percent = pct(stats.suspectMinutes, total),
                color = status.suspect,
                track = status.suspectContainer,
                modifier = Modifier.weight(1f),
            )
            InferenceRing(
                caption = "Pathological",
                index = 3,
                percent = pct(stats.pathologicMinutes, total),
                color = status.pathologic,
                track = status.pathologicContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun pct(part: Int, total: Int): Int = ((part.toFloat() / total) * 100f + 0.5f).toInt()

@Composable
private fun InferenceRing(
    caption: String,
    index: Int,
    percent: Int,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    val sweep by animateFloatAsState(
        targetValue = percent / 100f,
        animationSpec = tween(700),
        label = "ring-$caption",
    )
    Column(
        modifier = modifier
            .padding(horizontal = Spacing.xs)
            .clearAndSetSemantics {
                contentDescription = "$caption status $percent percent"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - stroke, size.height - stroke,
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    "($index)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
