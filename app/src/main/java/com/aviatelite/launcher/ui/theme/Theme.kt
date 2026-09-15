package com.aviatelite.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

private val LightColors = lightColorScheme(
    primary = AviatePrimary,
    onPrimary = AviateOnPrimary
)

/**
 * Um launcher passa a maior parte do tempo visível na tela, então
 * priorizamos o tema escuro por padrão (menor consumo em telas AMOLED
 * e menor "brilho" médio para leitura rápida), mas respeitamos o tema
 * do sistema quando o usuário preferir claro.
 */
@Composable
fun AviateLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AviateTypography,
        content = content
    )
}
