package com.silicovegas.wombcare.core.ui.theme

import androidx.compose.ui.unit.dp

/** 8 dp grid. 4 dp is allowed *inside* a tile and nowhere else. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screen = 20.dp
}

object Radii {
    val card = 20.dp
    val tile = 16.dp
    val pill = 999.dp
    val chip = 12.dp
}

object Sizes {
    /** Minimum interactive target. Material says 48; 44 is our floor for dense doctor rows. */
    val minTouch = 44.dp
    val hairline = 1.dp
    val statusDot = 10.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 28.dp
}
