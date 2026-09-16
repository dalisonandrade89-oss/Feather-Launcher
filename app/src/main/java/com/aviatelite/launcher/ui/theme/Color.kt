package com.aviatelite.launcher.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta escura, minimalista, inspirada no Aviate: fundo quase preto,
// destaque em azul/ciano suave. Poucas cores = menos overdraw perceptível.
val AviateBackground = Color(0xFF0E0F12)
val AviateSurface = Color(0xFF17181C)
val AviateSurfaceVariant = Color(0xFF1F2126)
val AviatePrimary = Color(0xFF5AC8FA)
val AviateOnPrimary = Color(0xFF00232E)
val AviateSecondary = Color(0xFF9AA5B1)
val AviateOnBackground = Color(0xFFEDEEF0)
val AviateOnSurfaceVariant = Color(0xFFB0B6BE)

// AMOLED: preto absoluto no background/surface — em telas AMOLED, pixels
// pretos ficam literalmente desligados, reduzindo consumo de bateria.
val AviateAmoledBackground = Color(0xFF000000)
val AviateAmoledSurface = Color(0xFF000000)
val AviateAmoledSurfaceVariant = Color(0xFF121212)

// Tema claro
val AviateLightBackground = Color(0xFFF7F8FA)
val AviateLightSurface = Color(0xFFFFFFFF)
val AviateLightSurfaceVariant = Color(0xFFE7E9EC)
val AviateLightOnBackground = Color(0xFF1A1B1E)
val AviateLightOnSurfaceVariant = Color(0xFF4A4E54)
