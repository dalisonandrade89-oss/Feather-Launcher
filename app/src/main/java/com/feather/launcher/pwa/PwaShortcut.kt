package com.feather.launcher.pwa

enum class PwaBrowser(
    val id: String,
    val label: String,
    val packageName: String,
    val installHint: String
) {
    BRAVE(
        id = "brave",
        label = "Brave",
        packageName = "com.brave.browser",
        installHint = "No Brave: menu (⋮) → \"Instalar página como app\" ou \"Adicionar à tela inicial\"."
    ),
    VIVALDI(
        id = "vivaldi",
        label = "Vivaldi",
        packageName = "com.vivaldi.browser",
        installHint = "No Vivaldi: menu → \"Instalar página como app\" ou \"Adicionar à tela inicial\"."
    ),
    CHROME(
        id = "chrome",
        label = "Chrome",
        packageName = "com.android.chrome",
        installHint = "No Chrome: menu (⋮) → \"Instalar aplicativo\" ou \"Adicionar à tela inicial\"."
    );

    companion object {
        val preferredBrowsers: List<PwaBrowser> = listOf(BRAVE, VIVALDI)

        fun fromId(id: String?): PwaBrowser =
            entries.firstOrNull { it.id == id } ?: BRAVE
    }
}

data class PwaShortcut(
    val id: String,
    val name: String,
    val url: String,
    val browser: PwaBrowser,
    val searchableName: String
)
