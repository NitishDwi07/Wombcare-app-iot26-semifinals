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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.silicovegas.wombcare.core.ble.ConnectionState
import com.silicovegas.wombcare.core.ui.format.accent
import com.silicovegas.wombcare.core.ui.format.isPulsing
import com.silicovegas.wombcare.core.ui.format.label
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * Link state as a dot plus words. The dot breathes only while something is genuinely in
 * progress — a static dot on a stalled connection is the honest rendering, and it is why
 * `Monitoring` and `SignalLost` can never look alike at a glance.
 */
@Composable
fun ConnectionChip(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val accent = state.accent()
    val text = state.label()

    val pulse by rememberInfiniteTransition(label = "connection-pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "dot-alpha",
    )
    val dotAlpha = if (state.isPulsing) pulse else 1f

    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        shape = RoundedCornerShape(Radii.pill),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            Sizes.hairline,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.statusDot)
                    .background(accent.copy(alpha = dotAlpha), CircleShape),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview
@Composable
private fun ConnectionChipPreview() {
    WombCareTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            ConnectionChip(ConnectionState.Scanning)
            ConnectionChip(ConnectionState.Connected)
            ConnectionChip(ConnectionState.Monitoring)
            ConnectionChip(ConnectionState.SignalLost)
            ConnectionChip(ConnectionState.BluetoothOff)
        }
    }
}
