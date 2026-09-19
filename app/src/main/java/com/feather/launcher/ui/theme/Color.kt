package com.feather.launcher.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta escura, minimalista, inspirada no antigo Aviate Launcher: fundo quase preto,
// destaque em azul/ciano suave. Poucas cores = menos overdraw perceptível.
val FeatherBackground = Color(0xFF0E0F12)
val FeatherSurface = Color(0xFF17181C)
val FeatherSurfaceVariant = Color(0xFF1F2126)
val FeatherPrimary = Color(0xFF5AC8FA)
val FeatherOnPrimary = Color(0xFF00232E)
val FeatherSecondary = Color(0xFF9AA5B1)
val FeatherOnBackground = Color(0xFFEDEEF0)
val FeatherOnSurfaceVariant = Color(0xFFB0B6BE)

// AMOLED: preto absoluto no background/surface — em telas AMOLED, pixels
// pretos ficam literalmente desligados, reduzindo consumo de bateria.
val FeatherAmoledBackground = Color(0xFF000000)
val FeatherAmoledSurface = Color(0xFF000000)
val FeatherAmoledSurfaceVariant = Color(0xFF121212)

// Tema claro
val FeatherLightBackground = Color(0xFFF7F8FA)
val FeatherLightSurface = Color(0xFFFFFFFF)
val FeatherLightSurfaceVariant = Color(0xFFE7E9EC)
val FeatherLightOnBackground = Color(0xFF1A1B1E)
val FeatherLightOnSurfaceVariant = Color(0xFF4A4E54)
