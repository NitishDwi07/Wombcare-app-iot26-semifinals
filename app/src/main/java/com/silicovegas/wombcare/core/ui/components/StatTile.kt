package com.silicovegas.wombcare.core.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * One measurement: caption, number, unit, optional note.
 *
 * Accessibility is the reason this exists as a component rather than a layout copied per
 * screen. Left to itself TalkBack would announce "Fetal heart rate" then "142" then "BPM"
 * as three unrelated nodes; [clearAndSetSemantics] collapses the tile into one spoken
 * phrase — and a missing value is announced as "no reading", not as the literal "--".
 */
@Composable
fun StatTile(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    note: String? = null,
    dimmed: Boolean = false,
    /** Word values (e.g. "Resting") need a smaller style than a big number, or they wrap. */
    valueStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    val spoken = if (value == null) {
        stringResource(R.string.a11y_no_value, label)
    } else {
        stringResource(R.string.a11y_stat, label, listOfNotNull(value, unit).joinToString(" "))
    }
    val description = listOfNotNull(spoken, note).joinToString(". ")

    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(Radii.tile),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSm),
                    )
                }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            BigNumber(
                value = value,
                unit = unit,
                style = valueStyle ?: MaterialTheme.typography.displayMedium,
                dimmed = dimmed,
            )

            if (note != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(widthDp = 400)
@Composable
private fun StatTilePreview() {
    WombCareTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            StatTile(
                label = "Fetal heart rate",
                value = "142",
                unit = "BPM",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Confidence",
                value = "41",
                unit = "%",
                note = "Signal quality low",
                dimmed = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
