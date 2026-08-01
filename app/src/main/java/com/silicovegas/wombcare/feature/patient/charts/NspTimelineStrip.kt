package com.silicovegas.wombcare.feature.patient.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ble.ClinicalReading
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors

/**
 * One cell per minute, coloured by wellness status — the "read the whole session in two
 * seconds" row that sits directly under the FHR chart so the eye lines them up.
 *
 * Uses the reserved status palette (never a categorical hue). A 2px surface gap separates
 * cells so adjacent same-status minutes stay countable. Colour here is backed by the word +
 * icon in the status hero and pills elsewhere on the screen, so status is never conveyed by
 * colour alone across the surface as a whole.
 */
@Composable
fun NspTimelineStrip(
    readings: List<ClinicalReading>,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val gap = 2.dp

    fun colorFor(s: WellnessStatus): Color = when (s) {
        WellnessStatus.NORMAL -> status.normal
        WellnessStatus.SUSPECT -> status.suspect
        WellnessStatus.PATHOLOGIC -> status.pathologic
        WellnessStatus.UNKNOWN -> status.unknown
    }

    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val n = readings.size
        if (n == 0) return@Canvas
        val gapPx = gap.toPx()
        val cellWidth = (size.width - gapPx * (n - 1)) / n
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        readings.forEachIndexed { i, r ->
            val x = i * (cellWidth + gapPx)
            drawRoundRect(
                color = colorFor(r.status),
                topLeft = Offset(x, 0f),
                size = Size(cellWidth.coerceAtLeast(1f), size.height),
                cornerRadius = radius,
            )
        }
    }
}
