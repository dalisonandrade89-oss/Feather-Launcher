package com.feather.launcher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.feather.launcher.data.ThemeMode

private val DarkColors = darkColorScheme(
    background = FeatherBackground,
    surface = FeatherSurface,
    surfaceVariant = FeatherSurfaceVariant,
    primary = FeatherPrimary,
    onPrimary = FeatherOnPrimary,
    secondary = FeatherSecondary,
    onBackground = FeatherOnBackground,
    onSurface = FeatherOnBackground,
    onSurfaceVariant = FeatherOnSurfaceVariant
)

private val AmoledColors = darkColorScheme(
    background = FeatherAmoledBackground,
    surface = FeatherAmoledSurface,
    surfaceVariant = FeatherAmoledSurfaceVariant,
    primary = FeatherPrimary,
    onPrimary = FeatherOnPrimary,
    secondary = FeatherSecondary,
    onBackground = FeatherOnBackground,
    onSurface = FeatherOnBackground,
    onSurfaceVariant = FeatherOnSurfaceVariant
)

private val LightColors = lightColorScheme(
    background = FeatherLightBackground,
    surface = FeatherLightSurface,
    surfaceVariant = FeatherLightSurfaceVariant,
    primary = FeatherPrimary,
    onPrimary = FeatherOnPrimary,
    onBackground = FeatherLightOnBackground,
    onSurface = FeatherLightOnBackground,
    onSurfaceVariant = FeatherLightOnSurfaceVariant
)

/**
 * Aplica cor de fundo e cor de destaque (accent) opcionais sobre o esquema base,
 * mantendo contraste legível e superfícies leves sem overdraw extra.
 */
@Composable
fun FeatherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when (themeMode) {
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.LIGHT -> LightColors
    }

    val withBackground = if (backgroundColor != null) {
        baseColorScheme.applyCustomBackground(backgroundColor)
    } else {
        baseColorScheme
    }

    val colorScheme = if (accentColor != null) {
        val onAccent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        withBackground.copy(
            primary = accentColor,
            onPrimary = onAccent,
            secondary = accentColor
        )
    } else {
        withBackground
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FeatherTypography,
        content = content
    )
}

private fun ColorScheme.applyCustomBackground(bg: Color): ColorScheme {
    val isLightBg = bg.luminance() > 0.42f
    return if (isLightBg) {
        copy(
            background = bg,
            surface = lerp(bg, Color.White, 0.45f),
            surfaceVariant = lerp(bg, Color.Black, 0.08f),
            onBackground = Color(0xFF1A1B1E),
            onSurface = Color(0xFF1A1B1E),
            onSurfaceVariant = Color(0xFF4A4E54)
        )
    } else {
        copy(
            background = bg,
            surface = lerp(bg, Color.White, 0.05f),
            surfaceVariant = lerp(bg, Color.White, 0.11f),
            onBackground = Color(0xFFEDEEF0),
            onSurface = Color(0xFFEDEEF0),
            onSurfaceVariant = Color(0xFFB0B6BE)
        )
    }
}
