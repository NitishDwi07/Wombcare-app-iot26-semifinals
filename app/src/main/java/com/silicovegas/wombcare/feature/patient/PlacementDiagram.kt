package com.silicovegas.wombcare.feature.patient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * The electrode-placement figure, drawn natively so it stays crisp and theme-independent —
 * a soft rose torso works on both the light and dark app grounds. Coordinates are authored
 * in a 360×600 space and scaled to fit; [highlight] is the set of electrode numbers (1–6) to
 * emphasise (the rest dim), and [showSeparation] draws the 10–15 cm rule between pads 4 and 5.
 */
@Composable
fun PlacementDiagram(
    highlight: Set<Int>,
    modifier: Modifier = Modifier,
    showSeparation: Boolean = false,
) {
    val bodyFill = Color(0xFFF0E2E5)
    val bodyStroke = Color(0xFFB0838F)
    val womb = Color(0xFFEAC7D0)
    val wombStroke = Color(0xFFCF97A6)
    val fetus = Color(0xFFC67689)
    val rose = Color(0xFFC0526E)
    val muted = Color(0xFF8A7E86)
    val red = Color(0xFFDE3C41)
    val amber = Color(0xFFCF8A12)
    val green = Color(0xFF1F9B74)

    data class Elec(val id: Int, val x: Float, val y: Float, val color: Color, val darkNum: Boolean)
    val electrodes = listOf(
        Elec(1, 137f, 184f, red, false),
        Elec(2, 223f, 184f, amber, true),
        Elec(3, 236f, 242f, green, false),
        Elec(4, 180f, 300f, red, false),
        Elec(5, 180f, 420f, amber, true),
        Elec(6, 112f, 406f, green, false),
    )

    Canvas(modifier.aspectRatio(360f / 600f)) {
        val k = size.width / 360f
        fun sx(v: Float) = v * k
        fun off(x: Float, y: Float) = Offset(x * k, y * k)

        // ---- body ----
        val body = Path().apply {
            moveTo(sx(128f), sx(120f))
            cubicTo(sx(118f), sx(146f), sx(116f), sx(170f), sx(120f), sx(202f))
            cubicTo(sx(124f), sx(234f), sx(122f), sx(270f), sx(104f), sx(322f))
            cubicTo(sx(92f), sx(358f), sx(92f), sx(374f), sx(100f), sx(412f))
            cubicTo(sx(108f), sx(448f), sx(112f), sx(474f), sx(128f), sx(504f))
            cubicTo(sx(137f), sx(524f), sx(147f), sx(538f), sx(158f), sx(548f))
            lineTo(sx(202f), sx(548f))
            cubicTo(sx(213f), sx(538f), sx(223f), sx(524f), sx(232f), sx(504f))
            cubicTo(sx(248f), sx(474f), sx(252f), sx(448f), sx(260f), sx(412f))
            cubicTo(sx(268f), sx(374f), sx(268f), sx(358f), sx(256f), sx(322f))
            cubicTo(sx(238f), sx(270f), sx(236f), sx(234f), sx(240f), sx(202f))
            cubicTo(sx(244f), sx(170f), sx(242f), sx(146f), sx(232f), sx(120f))
            cubicTo(sx(214f), sx(106f), sx(200f), sx(100f), sx(196f), sx(98f))
            lineTo(sx(164f), sx(98f))
            cubicTo(sx(160f), sx(100f), sx(146f), sx(106f), sx(128f), sx(120f))
            close()
        }
        drawPath(body, bodyFill)
        drawPath(body, bodyStroke, style = Stroke(width = 2.4f * k))
        // head
        drawCircle(bodyFill, radius = 30f * k, center = off(180f, 52f))
        drawCircle(bodyStroke, radius = 30f * k, center = off(180f, 52f), style = Stroke(2.4f * k))

        // ---- uterus + fetus ----
        drawOval(womb, topLeft = off(116f, 292f), size = Size(128f * k, 164f * k))
        drawOval(wombStroke, topLeft = off(116f, 292f), size = Size(128f * k, 164f * k), style = Stroke(2f * k))
        drawCircle(fetus, radius = 15f * k, center = off(196f, 350f))
        val fbody = Path().apply {
            moveTo(sx(190f), sx(364f))
            cubicTo(sx(172f), sx(368f), sx(160f), sx(388f), sx(172f), sx(406f))
            cubicTo(sx(180f), sx(418f), sx(202f), sx(420f), sx(214f), sx(408f))
            cubicTo(sx(222f), sx(400f), sx(220f), sx(386f), sx(210f), sx(384f))
            cubicTo(sx(200f), sx(382f), sx(196f), sx(372f), sx(190f), sx(364f))
            close()
        }
        drawPath(fbody, fetus)
        drawCircle(bodyStroke, radius = 2.6f * k, center = off(180f, 356f))

        // ---- separation rule ----
        if (showSeparation) {
            drawLine(
                rose, off(180f, 314f), off(180f, 406f), strokeWidth = 1.8f * k,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 4f * k)),
            )
        }

        // ---- native text (numbers, landmarks, cm chip) ----
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            fun paint(sizePx: Float, argb: Int, bold: Boolean) = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = sizePx
                color = argb
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
                )
            }
            // landmark labels
            val lp = paint(11f * k, muted.toArgb(), false)
            nc.drawText("fundus", sx(180f), sx(285f), lp)
            nc.drawText("suprapubic", sx(180f), sx(447f), lp)
            nc.drawText("R hip", sx(88f), sx(392f), lp)

            // cm chip
            if (showSeparation) {
                val chip = RoundRect(
                    Rect(off(196f, 349f), Size(74f * k, 20f * k)), cornerRadius(5f * k),
                )
                drawPath(Path().apply { addRoundRect(chip) }, Color.White)
                drawPath(Path().apply { addRoundRect(chip) }, Color(0xFFE0D3D8), style = Stroke(1f * k))
                nc.drawText("10–15 cm", sx(233f), sx(363f), paint(12f * k, Color(0xFF242024).toArgb(), true))
            }

            // electrodes
            electrodes.forEach { e ->
                val on = e.id in highlight
                val a = if (on) 1f else 0.4f
                drawCircle(e.color.copy(alpha = a), radius = 14f * k, center = off(e.x, e.y))
                drawCircle(Color.White.copy(alpha = a), radius = 14f * k, center = off(e.x, e.y), style = Stroke(2.5f * k))
                if (on) {
                    drawCircle(e.color, radius = 19f * k, center = off(e.x, e.y), style = Stroke(2.5f * k))
                }
                val np = paint(13f * k, (if (e.darkNum) Color(0xFF3A2A08) else Color.White).toArgb(), true)
                np.alpha = (a * 255).toInt()
                nc.drawText(e.id.toString(), sx(e.x), sx(e.y) + 4.6f * k, np)
            }
        }
    }
}

private fun cornerRadius(r: Float) = androidx.compose.ui.geometry.CornerRadius(r, r)
