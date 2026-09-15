package com.aviatelite.launcher.data

import android.content.Context

/**
 * Persiste preferências gerais e leves do launcher (hoje, apenas o
 * ThemeMode). Usa SharedPreferences por ser síncrono, simples e
 * suficiente para um único valor pequeno — não justifica o custo de
 * inicialização do DataStore para este caso de uso.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.DARK
        return try {
            ThemeMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            // Valor desatualizado/corrompido (ex.: de uma versão antiga do app).
            ThemeMode.DARK
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
