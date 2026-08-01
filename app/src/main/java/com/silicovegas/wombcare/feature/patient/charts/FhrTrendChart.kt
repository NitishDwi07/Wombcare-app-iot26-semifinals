package com.silicovegas.wombcare.feature.patient.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WombCareGatt
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * Fetal heart-rate over the session — one point per minute, a single series.
 *
 * Design decisions that follow the data's honesty, not decoration:
 *  - **The 110–160 normal band is shaded behind the line** as context, not a verdict — the
 *    device classifies wellness; the band just orients the eye.
 *  - **The line BREAKS across gaps.** A no-lock minute (fhr == null) or a skipped window
 *    leaves a hole; we never draw a segment through it, because an interpolated line is a
 *    reading the device never took.
 *  - One series, so no legend — the card title names it. Thin 2px stroke, recessive grid.
 */
@Composable
fun FhrTrendChart(
    readings: List<ClinicalReading>,
    modifier: Modifier = Modifier,
    yMin: Int = 60,
    yMax: Int = 200,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = LocalStatusColors.current.normal.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier.fillMaxWidth().height(180.dp).padding(vertical = Spacing.sm)) {
        if (readings.none { it.hasValidFhr }) {
            Text(
                "No heart-rate reading yet",
                style = MaterialTheme.typography.bodyMedium,
                color = axisTextColor,
                modifier = Modifier.padding(Spacing.md),
            )
            return@Box
        }

        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val span = (yMax - yMin).toFloat()
            fun yFor(v: Int) = size.height * (1f - (v - yMin) / span)
            val n = readings.size
            fun xFor(i: Int) = if (n <= 1) 0f else size.width * i / (n - 1).toFloat()

            // Normal band 110-160.
            val bandTop = yFor(WombCareGatt.FHR_NORMAL_HIGH)
            val bandBottom = yFor(WombCareGatt.FHR_NORMAL_LOW)
            drawRect(
                color = bandColor,
                topLeft = Offset(0f, bandTop),
                size = androidx.compose.ui.geometry.Size(size.width, bandBottom - bandTop),
            )

            // Recessive gridlines + numeric y-axis labels at the band edges, so the chart
            // can be read as numbers (bpm), not just a shape.
            val labelPaint = android.graphics.Paint().apply {
                color = axisTextColor.toArgb()
                textSize = 11.dp.toPx()
                isAntiAlias = true
            }
            listOf(WombCareGatt.FHR_NORMAL_LOW, WombCareGatt.FHR_NORMAL_HIGH).forEach { v ->
                val y = yFor(v)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                drawContext.canvas.nativeCanvas.drawText(
                    "$v",
                    4f,
                    (y - 4f).coerceAtLeast(labelPaint.textSize),
                    labelPaint,
                )
            }

            // Build the broken line as a set of contiguous segments, THEN draw — Canvas
            // renders each drawPath immediately, so paths must be complete before drawing.
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            segmentPaths(readings, ::xFor) { v -> yFor(v.coerceIn(yMin, yMax)) }.forEach {
                drawPath(it, color = lineColor, style = stroke)
            }
            // Point markers on every valid reading.
            readings.forEachIndexed { i, r ->
                r.fhrBpm?.let { fhr ->
                    drawCircle(lineColor, 3.dp.toPx(), Offset(xFor(i), yFor(fhr.coerceIn(yMin, yMax))))
                }
            }
        }
    }
}

/** One [Path] per contiguous run of valid FHR points; nulls split the runs. */
private fun segmentPaths(
    readings: List<ClinicalReading>,
    xFor: (Int) -> Float,
    yFor: (Int) -> Float,
): List<Path> {
    val paths = mutableListOf<Path>()
    var current: Path? = null
    readings.forEachIndexed { i, r ->
        val fhr = r.fhrBpm
        if (fhr == null) {
            current = null
        } else {
            val x = xFor(i); val y = yFor(fhr)
            if (current == null) {
                current = Path().apply { moveTo(x, y) }.also { paths += it }
            } else {
                current!!.lineTo(x, y)
            }
        }
    }
    return paths
}
