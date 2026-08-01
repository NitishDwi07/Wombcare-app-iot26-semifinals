package com.silicovegas.wombcare.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * A measurement, rendered as the hero of its tile.
 *
 * Two deliberate behaviours:
 *  - `value == null` renders `--`, never `0`. The firmware sends 0 for "no lock", and a
 *    displayed 0 bpm would be both alarming and false.
 *  - [dimmed] mutes the figure without hiding it, for readings the device itself flagged
 *    as low-confidence. Hiding a value the device reported would be dishonest; presenting
 *    it at full strength would be over-confident.
 */
@Composable
fun BigNumber(
    value: String?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    dimmed: Boolean = false,
) {
    val hasValue = value != null
    val resolved = if (hasValue) color else MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = if (dimmed) 0.55f else 1f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value ?: stringResource(R.string.value_placeholder),
            style = style,
            color = resolved.copy(alpha = alpha),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (unit != null && hasValue) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                // Optical nudge: sits the unit on the digits' baseline.
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Preview
@Composable
private fun BigNumberPreview() {
    WombCareTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            BigNumber(value = "142", unit = "BPM")
            BigNumber(value = null, unit = "BPM")
            BigNumber(value = "41", unit = "%", dimmed = true)
        }
    }
}
