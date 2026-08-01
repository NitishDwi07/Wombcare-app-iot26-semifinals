package com.silicovegas.wombcare.feature.patient.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * Kicks per minute as bars. Magnitude-over-time, so bars (not a line): each minute's count
 * stands on its own and zero reads as an empty slot, not a dip in a curve.
 *
 * Every non-zero bar is DIRECTLY LABELLED with its count, so the chart reads as numbers, not
 * just heights — you can see "3 kicks that minute" without a y-axis. Data-ends are
 * 4px-rounded and sit on a baseline, per the mark spec.
 */
@Composable
fun KickBars(
    readings: List<ClinicalReading>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxKicks = (readings.maxOfOrNull { it.kickCountInWindow } ?: 0).coerceAtLeast(1)

    Canvas(modifier.fillMaxWidth().height(96.dp).padding(vertical = Spacing.sm)) {
        val n = readings.size
        if (n == 0) return@Canvas

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        val labelBand = 14.dp.toPx()          // reserved space at top for the value labels
        val baselineY = size.height - 1.dp.toPx()
        val plotHeight = baselineY - labelBand
        val gapPx = 3.dp.toPx()
        val slot = size.width / n
        val barWidth = (slot - gapPx).coerceIn(2.dp.toPx(), 22.dp.toPx())
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Baseline, so zero-kick minutes still read as "on the floor", not missing.
        drawLine(baselineColor, Offset(0f, baselineY), Offset(size.width, baselineY), 1f)

        readings.forEachIndexed { i, r ->
            val kicks = r.kickCountInWindow
            val cx = i * slot + slot / 2f
            if (kicks <= 0) return@forEachIndexed

            val h = plotHeight * (kicks / maxKicks.toFloat())
            drawRoundRect(
                color = barColor,
                topLeft = Offset(cx - barWidth / 2f, baselineY - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
            // Value label above the bar.
            drawContext.canvas.nativeCanvas.drawText(
                "$kicks",
                cx,
                (baselineY - h - 4.dp.toPx()).coerceAtLeast(labelPaint.textSize),
                labelPaint,
            )
        }
    }
}
