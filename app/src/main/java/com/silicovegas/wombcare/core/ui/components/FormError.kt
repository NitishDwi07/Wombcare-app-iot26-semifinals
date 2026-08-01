package com.silicovegas.wombcare.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Radii
import com.silicovegas.wombcare.core.ui.theme.Sizes
import com.silicovegas.wombcare.core.ui.theme.Spacing

/**
 * Form-level error banner — the "email or password is incorrect" kind, distinct from the
 * per-field errors under each input. Animates in so it doesn't jump the layout, and uses
 * the pathologic red so error colour means the same thing everywhere in the app.
 */
@Composable
fun FormError(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message != null) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(top = Spacing.md),
            shape = RoundedCornerShape(Radii.chip),
            color = LocalStatusColors.current.pathologicContainer,
            contentColor = LocalStatusColors.current.pathologic,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconMd),
                )
                Text(message ?: "", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
