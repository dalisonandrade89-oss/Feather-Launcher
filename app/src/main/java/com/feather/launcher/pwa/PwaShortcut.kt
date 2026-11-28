package com.feather.launcher.pwa

enum class PwaBrowser(
    val id: String,
    val label: String,
    val packageName: String
) {
    BRAVE("brave", "Brave", "com.brave.browser"),
    VIVALDI("vivaldi", "Vivaldi", "com.vivaldi.browser"),
    CHROME("chrome", "Chrome", "com.android.chrome");

    companion object {
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
