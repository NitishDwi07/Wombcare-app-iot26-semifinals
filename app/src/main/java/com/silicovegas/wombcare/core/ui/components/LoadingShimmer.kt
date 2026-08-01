package com.silicovegas.wombcare.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * Placeholder block for content in flight.
 *
 * A calm alpha breath rather than a sweeping gradient — this app shows loading skeletons
 * while pulling a *pregnancy's* worth of history, and a glinting animation across the whole
 * screen reads as urgency the situation does not warrant.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radii.tile),
) {
    val alpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1100), repeatMode = RepeatMode.Reverse),
        label = "shimmer-alpha",
    )

    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
            shape = shape,
        ),
    )
}

/** Skeleton shaped like a [StatTile], for the dashboard's first paint. */
@Composable
fun StatTileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.75f).height(34.dp))
    }
}

@Preview(widthDp = 360)
@Composable
private fun ShimmerPreview() {
    WombCareTheme {
        Column(Modifier.padding(Spacing.lg)) {
            StatTileSkeleton()
        }
    }
}
