package com.silicovegas.wombcare.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ble.WellnessStatus
import com.silicovegas.wombcare.core.ui.format.visual
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * The dashboard hero — the one thing a mother should be able to read from across the room.
 *
 * `waiting = true` is a first-class state, not an edge case: the firmware pushes an all-zero
 * frame the instant the app subscribes, and the first genuine window is a full minute away.
 * Rendering that as a confident "Normal" would be inventing a reassurance the device never
 * gave.
 */
@Composable
fun StatusHeroCard(
    status: WellnessStatus,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    waiting: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val v = status.visual()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.card),
        color = if (waiting) MaterialTheme.colorScheme.surface else v.container,
        contentColor = if (waiting) MaterialTheme.colorScheme.onSurface else v.accent,
        border = androidx.compose.foundation.BorderStroke(
            Sizes.hairline,
            if (waiting) MaterialTheme.colorScheme.outlineVariant else v.container,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!waiting) {
                    Icon(
                        imageVector = v.icon,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconLg),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.label_status).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (waiting) {
                            stringResource(R.string.note_waiting_first_reading)
                        } else {
                            v.label
                        },
                        style = if (waiting) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            trailing?.invoke()
        }
    }
}

@Preview(widthDp = 400)
@Composable
private fun StatusHeroPreview() {
    WombCareTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            StatusHeroCard(WellnessStatus.NORMAL, subtitle = "Updated 1 min ago")
            StatusHeroCard(WellnessStatus.SUSPECT, subtitle = "2 windows")
            StatusHeroCard(WellnessStatus.PATHOLOGIC, subtitle = "Contact your doctor")
            StatusHeroCard(WellnessStatus.NORMAL, waiting = true)
        }
    }
}
