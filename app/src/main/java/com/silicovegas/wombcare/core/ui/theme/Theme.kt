package com.silicovegas.wombcare.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Which half of the app is on screen. Drives the palette, nothing else. */
enum class AppRole { PATIENT, DOCTOR }

/**
 * Status colours live outside [MaterialTheme.colorScheme] because they are semantic
 * (clinical state), not chrome — and they must not shift with the role palette.
 */
@Immutable
data class StatusColors(
    val normal: Color,
    val normalContainer: Color,
    val suspect: Color,
    val suspectContainer: Color,
    val pathologic: Color,
    val pathologicContainer: Color,
    val unknown: Color,
    val unknownContainer: Color,
)

val LocalStatusColors: ProvidableCompositionLocal<StatusColors> =
    compositionLocalOf { lightStatusColors }

private val lightStatusColors = StatusColors(
    normal = StatusNormal, normalContainer = StatusNormalContainer,
    suspect = StatusSuspect, suspectContainer = StatusSuspectContainer,
    pathologic = StatusPathologic, pathologicContainer = StatusPathologicContainer,
    unknown = StatusUnknown, unknownContainer = StatusUnknownContainer,
)

private val darkStatusColors = StatusColors(
    normal = Color(0xFF54C3A0), normalContainer = Color(0xFF14342B),
    suspect = Color(0xFFEDBB63), suspectContainer = Color(0xFF3A2D12),
    pathologic = Color(0xFFEE7272), pathologicContainer = Color(0xFF3B1717),
    unknown = InkMutedDark, unknownContainer = Color(0xFF2C313C),
)

private fun lightScheme(role: AppRole) = lightColorScheme(
    primary = if (role == AppRole.PATIENT) RosePrimary else SkyPrimary,
    onPrimary = if (role == AppRole.PATIENT) RoseOnPrimary else SkyOnPrimary,
    primaryContainer = if (role == AppRole.PATIENT) RoseContainer else SkyContainer,
    onPrimaryContainer = if (role == AppRole.PATIENT) RoseOnContainer else SkyOnContainer,
    background = Background,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = if (role == AppRole.PATIENT) RoseContainer else SkyContainer,
    onSurfaceVariant = InkMuted,
    outlineVariant = Hairline,
    error = StatusPathologic,
)

private fun darkScheme(role: AppRole) = darkColorScheme(
    primary = if (role == AppRole.PATIENT) Color(0xFFEE93A8) else Color(0xFF7FB6E8),
    onPrimary = Color(0xFF3A1520),
    primaryContainer = if (role == AppRole.PATIENT) Color(0xFF4E2530) else Color(0xFF1B3344),
    onPrimaryContainer = InkDark,
    background = BackgroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = HairlineDark,
    onSurfaceVariant = InkMutedDark,
    outlineVariant = HairlineDark,
    error = Color(0xFFEE7272),
)

/**
 * Dynamic colour is deliberately **not** used. The wallpaper-derived palette would move
 * clinical status hues around per device, and "what colour is Suspect" must be constant.
 */
@Composable
fun WombCareTheme(
    role: AppRole = AppRole.PATIENT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) darkStatusColors else lightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(role) else lightScheme(role),
            typography = WombCareTypography,
            content = content,
        )
    }
}
