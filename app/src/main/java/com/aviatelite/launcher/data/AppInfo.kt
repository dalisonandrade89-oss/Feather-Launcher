package com.aviatelite.launcher.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Representa um app instalado, já com o ícone pré-convertido e
 * reduzido para [ImageBitmap] em resolução pequena, evitando
 * conversões repetidas de Drawable em cada recomposição
 * (importante para RAM/CPU no Helio G25).
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap?,
    val category: AppCategory
) {
    /** Chave estável e única para uso em LazyColumn/LazyRow. */
    val key: String get() = packageName + "/" + activityName
}

/**
 * Categorias simplificadas usadas para agrupar os apps nos "Spaces"
 * da tela central, derivadas de ApplicationInfo.category (API 26+).
 */
enum class AppCategory(val displayName: String) {
    MIDIA("Mídia"),
    PRODUTIVIDADE("Produtividade"),
    UTILITARIOS("Utilitários"),
    JOGOS("Jogos"),
    COMUNICACAO("Comunicação"),
    OUTROS("Outros")
}

/** Modo contextual manual (sem GPS/geofencing, troca é sempre explícita pelo usuário). */
enum class ContextMode(val label: String) {
    DIA_A_DIA("Dia a Dia"),
    TRABALHO("Trabalho"),
    NOITE("Noite")
}
