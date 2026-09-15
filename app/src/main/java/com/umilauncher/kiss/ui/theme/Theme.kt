package com.umilauncher.kiss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta plana, sem gradientes/superfícies translúcidas: cada efeito de
// blur/transparência no Compose implica em camadas de composição extras
// (RenderNode / layer) que custam GPU - relevante numa GPU PowerVR fraca
// tentando sustentar 60fps estáveis.
private val DarkColors = darkColorScheme(
    background = Color(0xFF0E0E10),
    surface = Color(0xFF0E0E10),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    primary = Color(0xFF9ECBFF)
)

private val LightColors = lightColorScheme(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFAFAFA),
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    primary = Color(0xFF1A73E8)
)

@Composable
fun UmiLauncherTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        // Usamos a tipografia default do MaterialTheme (sem carregar fontes
        // customizadas) - fontes custom exigem I/O e memória extra para os
        // glyphs, sem ganho real de legibilidade num launcher.
        content = content
    )
}
