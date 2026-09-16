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
    val searchableLabel: String
) {
    /** Chave estável e única para uso em LazyColumn/LazyRow. */
    val key: String get() = packageName + "/" + activityName
}

/**
 * Uma aba/Space da Home. Substitui o antigo enum fixo `ContextMode`:
 * agora existem 3 Spaces padrão (não podem ser excluídos, só renomeados)
 * e quantos Spaces personalizados o usuário quiser criar.
 *
 * Um app só aparece num Space se tiver sido explicitamente vinculado a
 * ele (regra de exclusividade) — ver [AppPrefs.getSpaceAssignments].
 */
data class SpaceDef(
    val id: String,
    val name: String,
    val isCustom: Boolean
)

/** Os 3 Spaces padrão do launcher, criados na primeira execução. */
val defaultSpaces: List<SpaceDef> = listOf(
    SpaceDef(id = "dia_a_dia", name = "Dia a Dia", isCustom = false),
    SpaceDef(id = "trabalho", name = "Trabalho", isCustom = false),
    SpaceDef(id = "noite", name = "Noite", isCustom = false)
)

/**
 * Modo de aparência do launcher. Além dos 3 fixos, o usuário pode
 * escolher uma cor de destaque (accent color) personalizada — ver
 * [AppPrefs.getAccentColorHex].
 */
enum class ThemeMode(val label: String) {
    DARK("Escuro"),
    LIGHT("Claro"),
    AMOLED("Preto AMOLED")
}
