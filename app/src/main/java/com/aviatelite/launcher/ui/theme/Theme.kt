package com.aviatelite.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aviatelite.launcher.data.ThemeMode

private val DarkColors = darkColorScheme(
    background = AviateBackground,
    surface = AviateSurface,
    surfaceVariant = AviateSurfaceVariant,
    primary = AviatePrimary,
    onPrimary = AviateOnPrimary,
    secondary = AviateSecondary,
    onBackground = AviateOnBackground,
    onSurface = AviateOnBackground,
    onSurfaceVariant = AviateOnSurfaceVariant
)

// AMOLED reaproveita a maior parte da paleta escura, trocando apenas
// background/surface para preto puro (economia de bateria em telas OLED).
private val AmoledColors = darkColorScheme(
    background = AviateAmoledBackground,
    surface = AviateAmoledSurface,
    surfaceVariant = AviateAmoledSurfaceVariant,
    primary = AviatePrimary,
    onPrimary = AviateOnPrimary,
    secondary = AviateSecondary,
    onBackground = AviateOnBackground,
    onSurface = AviateOnBackground,
    onSurfaceVariant = AviateOnSurfaceVariant
)

private val LightColors = lightColorScheme(
    background = AviateLightBackground,
    surface = AviateLightSurface,
    surfaceVariant = AviateLightSurfaceVariant,
    primary = AviatePrimary,
    onPrimary = AviateOnPrimary,
    onBackground = AviateLightOnBackground,
    onSurface = AviateLightOnBackground,
    onSurfaceVariant = AviateLightOnSurfaceVariant
)

/**
 * Tema do launcher. Recebe o [ThemeMode] e, opcionalmente, uma cor de
 * destaque (accent) personalizada escolhida pelo usuário no seletor de
 * cores — quando presente, ela substitui `primary`/`onPrimary` do
 * esquema base, mantendo o resto da paleta (fundo, superfícies) igual.
 */
@Composable
fun AviateLiteTheme(
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
        typography = AviateTypography,
        content = content
    )
}
