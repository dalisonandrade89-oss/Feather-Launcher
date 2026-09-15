package com.aviatelite.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
 * Tema do launcher. Recebe o [ThemeMode] escolhido pelo usuário
 * (persistido via AppPrefs/LauncherViewModel) em vez de depender apenas
 * do tema do sistema — é a nova opção de personalização de cor de fundo.
 */
@Composable
fun AviateLiteTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.LIGHT -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AviateTypography,
        content = content
    )
}
