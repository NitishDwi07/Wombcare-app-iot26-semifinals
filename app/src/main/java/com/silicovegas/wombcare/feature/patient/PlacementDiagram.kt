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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * The electrode-placement figure — a full front-view (anterior) body with arms, hands and
 * legs, so left/right is unambiguous: the hands are labelled, and everything is drawn "as if
 * facing the mother", meaning her RIGHT is on the viewer's LEFT. Authored in a 420×780 space
 * and scaled to fit. [highlight] is the set of electrode numbers (1–6) to emphasise; the rest
 * dim. [showSeparation] draws the 10–15 cm rule between the two belly pads (4 and 5).
 */
@Composable
fun PlacementDiagram(
    highlight: Set<Int>,
    modifier: Modifier = Modifier,
    showSeparation: Boolean = false,
) {
    val bodyFill = Color(0xFFF3DEE3)
    val bodyStroke = Color(0xFFBE94A0)
    val womb = Color(0xFFE9C6CF)
    val wombStroke = Color(0xFFD3A0AE)
    val fetus = Color(0xFFC67689)
    val rose = Color(0xFFC0526E)
    val muted = Color(0xFF8A7E86)
    val red = Color(0xFFDE3C41)
    val amber = Color(0xFFCF8A12)
    val green = Color(0xFF1F9B74)

    data class Elec(val id: Int, val x: Float, val y: Float, val color: Color, val darkNum: Boolean)
    val electrodes = listOf(
        Elec(1, 178f, 168f, red, false),
        Elec(2, 242f, 168f, amber, true),
        Elec(3, 256f, 232f, green, false),
        Elec(4, 210f, 300f, red, false),
        Elec(5, 210f, 420f, amber, true),
        Elec(6, 152f, 402f, green, false),
    )

    Canvas(modifier.aspectRatio(420f / 780f)) {
        val k = size.width / 420f
        fun sx(v: Float) = v * k
        fun off(x: Float, y: Float) = Offset(x * k, y * k)
        fun poly(vararg pts: Pair<Float, Float>) = Path().apply {
            moveTo(sx(pts[0].first), sx(pts[0].second))
            pts.drop(1).forEach { lineTo(sx(it.first), sx(it.second)) }
        }
        fun limb(path: Path, outer: Float, inner: Float) {
            drawPath(path, bodyStroke, style = Stroke(outer * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, bodyFill, style = Stroke(inner * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // ---- legs (behind) ----
        limb(poly(194f to 480f, 190f to 732f), 40f, 34f)
        limb(poly(226f to 480f, 232f to 732f), 40f, 34f)

        // ---- torso ----
        val torso = Path().apply {
            moveTo(sx(158f), sx(104f))
            cubicTo(sx(150f), sx(130f), sx(148f), sx(152f), sx(152f), sx(192f))
            cubicTo(sx(156f), sx(232f), sx(150f), sx(272f), sx(140f), sx(322f))
            cubicTo(sx(133f), sx(358f), sx(135f), sx(374f), sx(146f), sx(414f))
            cubicTo(sx(152f), sx(442f), sx(158f), sx(462f), sx(172f), sx(482f))
            lineTo(sx(248f), sx(482f))
            cubicTo(sx(262f), sx(462f), sx(268f), sx(442f), sx(274f), sx(414f))
            cubicTo(sx(285f), sx(374f), sx(287f), sx(358f), sx(280f), sx(322f))
            cubicTo(sx(270f), sx(272f), sx(264f), sx(232f), sx(268f), sx(192f))
            cubicTo(sx(272f), sx(152f), sx(270f), sx(130f), sx(262f), sx(104f))
            cubicTo(sx(244f), sx(92f), sx(226f), sx(88f), sx(210f), sx(88f))
            cubicTo(sx(194f), sx(88f), sx(176f), sx(92f), sx(158f), sx(104f))
            close()
        }
        drawPath(torso, bodyFill)
        drawPath(torso, bodyStroke, style = Stroke(2.4f * k))

        // ---- arms + hands (in front of torso sides) ----
        limb(poly(164f to 118f, 146f to 300f, 132f to 420f), 30f, 25f)
        limb(poly(256f to 118f, 274f to 300f, 288f to 420f), 30f, 25f)
        for (hx in listOf(132f, 288f)) {
            drawCircle(bodyFill, radius = 16f * k, center = off(hx, 424f))
            drawCircle(bodyStroke, radius = 16f * k, center = off(hx, 424f), style = Stroke(2.2f * k))
        }

        // ---- head ----
        drawCircle(bodyFill, radius = 32f * k, center = off(210f, 54f))
        drawCircle(bodyStroke, radius = 32f * k, center = off(210f, 54f), style = Stroke(2.4f * k))

        // collarbone hint
        val clav = Path().apply {
            moveTo(sx(178f), sx(138f)); quadraticBezierTo(sx(210f), sx(150f), sx(242f), sx(138f))
        }
        drawPath(clav, bodyStroke, style = Stroke(1.6f * k, cap = StrokeCap.Round))

        // ---- uterus + fetus ----
        drawOval(womb, topLeft = off(150f, 294f), size = Size(120f * k, 160f * k))
        drawOval(wombStroke, topLeft = off(150f, 294f), size = Size(120f * k, 160f * k), style = Stroke(2f * k))
        drawCircle(fetus, radius = 15f * k, center = off(226f, 350f))
        val fbody = Path().apply {
            moveTo(sx(220f), sx(364f))
            cubicTo(sx(202f), sx(368f), sx(190f), sx(388f), sx(202f), sx(406f))
            cubicTo(sx(210f), sx(418f), sx(232f), sx(420f), sx(244f), sx(408f))
            cubicTo(sx(252f), sx(400f), sx(250f), sx(386f), sx(240f), sx(384f))
            cubicTo(sx(230f), sx(382f), sx(226f), sx(372f), sx(220f), sx(364f))
            close()
        }
        drawPath(fbody, fetus)
        drawCircle(bodyStroke, radius = 2.6f * k, center = off(210f, 354f))

        // ---- separation rule ----
        if (showSeparation) {
            drawLine(
                rose, off(210f, 314f), off(210f, 406f), strokeWidth = 1.8f * k,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 4f * k)),
            )
        }

        // ---- text: hand labels, landmarks, cm chip, electrode numbers ----
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

            // hand labels (mother's own hands): her RIGHT is on the viewer's LEFT
            val hp = paint(11.5f * k, rose.toArgb(), true)
            nc.drawText("RIGHT hand", sx(132f), sx(456f), hp)
            nc.drawText("LEFT hand", sx(288f), sx(456f), hp)

            // landmarks
            val lp = paint(11f * k, muted.toArgb(), false)
            nc.drawText("fundus", sx(210f), sx(286f), lp)
            nc.drawText("suprapubic", sx(210f), sx(448f), lp)
            nc.drawText("hip", sx(150f), sx(430f), lp)

            // cm chip
            if (showSeparation) {
                val chip = RoundRect(Rect(off(226f, 350f), Size(74f * k, 20f * k)), cornerRadius(5f * k))
                drawPath(Path().apply { addRoundRect(chip) }, Color.White)
                drawPath(Path().apply { addRoundRect(chip) }, Color(0xFFE0D3D8), style = Stroke(1f * k))
                nc.drawText("10–15 cm", sx(263f), sx(364f), paint(12f * k, Color(0xFF242024).toArgb(), true))
            }

            // electrodes
            electrodes.forEach { e ->
                val on = e.id in highlight
                val a = if (on) 1f else 0.4f
                drawCircle(e.color.copy(alpha = a), radius = 14f * k, center = off(e.x, e.y))
                drawCircle(Color.White.copy(alpha = a), radius = 14f * k, center = off(e.x, e.y), style = Stroke(2.5f * k))
                if (on) drawCircle(e.color, radius = 19f * k, center = off(e.x, e.y), style = Stroke(2.5f * k))
                val np = paint(13f * k, (if (e.darkNum) Color(0xFF3A2A08) else Color.White).toArgb(), true)
                np.alpha = (a * 255).toInt()
                nc.drawText(e.id.toString(), sx(e.x), sx(e.y) + 4.6f * k, np)
            }
        }
    }
}

private fun cornerRadius(r: Float) = androidx.compose.ui.geometry.CornerRadius(r, r)
