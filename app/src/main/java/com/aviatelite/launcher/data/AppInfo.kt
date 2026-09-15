package com.aviatelite.launcher.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Representa um app instalado, já com o ícone pré-convertido e
 * reduzido para [ImageBitmap] em resolução pequena, evitando
 * conversões repetidas de Drawable em cada recomposição
 * (importante para RAM/CPU no Helio G25).
 *
 * [searchableLabel] é o label sem acentos e em minúsculas, pré-computado
 * uma única vez no [AppRepository] — assim a busca em tempo real na
 * gaveta de apps não recalcula normalização de texto a cada tecla digitada.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap?,
    val category: AppCategory,
    val searchableLabel: String
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

/**
 * Modo de aparência do launcher (nova funcionalidade de personalização).
 * AMOLED usa preto puro (#000000) nos planos de fundo, reduzindo o
 * consumo de energia em telas AMOLED (pixels pretos ficam desligados).
 */
enum class ThemeMode(val label: String) {
    DARK("Escuro"),
    LIGHT("Claro"),
    AMOLED("Preto AMOLED")
}

