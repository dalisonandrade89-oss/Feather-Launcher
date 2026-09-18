package com.feather.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

// AMOLED reaproveita a maior parte da paleta escura, trocando apenas
// background/surface para preto puro (economia de bateria em telas OLED).
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
 * Tema do launcher. Recebe o [ThemeMode] e, opcionalmente, uma cor de
 * destaque (accent) personalizada escolhida pelo usuário no seletor de
 * cores — quando presente, ela substitui `primary`/`onPrimary` do
 * esquema base, mantendo o resto da paleta (fundo, superfícies) igual.
 */
@Composable
fun FeatherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when (themeMode) {
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.LIGHT -> LightColors
    }

    val colorScheme = if (accentColor != null) {
        // Escolhe texto preto ou branco sobre a cor de destaque conforme
        // sua luminância, para manter contraste legível automaticamente.
        val onAccent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        baseColorScheme.copy(
            primary = accentColor,
            onPrimary = onAccent,
            secondary = accentColor
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FeatherTypography,
        content = content
    )
}
