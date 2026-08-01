package com.silicovegas.wombcare.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing
import com.silicovegas.wombcare.core.ui.theme.WombCareTheme

/**
 * One consent, one checkbox — never a single "I agree to everything".
 *
 * The whole row is the toggle target (`Role.Checkbox`), so TalkBack announces one checkable
 * element instead of a box and an unrelated paragraph. `onRead` is separate and optional:
 * agreeing and reading the document are different acts, and the reading path must not
 * accidentally tick the box.
 */
@Composable
fun ConsentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    readLabel: String? = null,
    onRead: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.minTouch)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        if (readLabel != null && onRead != null) {
            TextButton(
                onClick = onRead,
                modifier = Modifier.padding(start = Spacing.xxl),
            ) {
                Text(readLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun ConsentPreview() {
    WombCareTheme {
        Column(Modifier.padding(Spacing.lg)) {
            ConsentCheckbox(
                checked = true,
                onCheckedChange = {},
                text = "I have read and accept the Terms of Service",
                readLabel = "Read Terms",
                onRead = {},
            )
            ConsentCheckbox(
                checked = false,
                onCheckedChange = {},
                text = "I understand WombCare is not a medical device and does not " +
                    "replace clinical care",
            )
        }
    }
}
