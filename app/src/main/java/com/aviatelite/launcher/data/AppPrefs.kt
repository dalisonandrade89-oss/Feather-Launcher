package com.aviatelite.launcher.data

import android.content.Context

/**
 * Persiste preferências gerais e leves do launcher: ThemeMode, apps
 * usados recentemente e apps "fixados" por modo contextual. Usa
 * SharedPreferences por ser síncrono, simples e suficiente para estes
 * valores pequenos — não justifica o custo de inicialização do
 * DataStore para este caso de uso.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- Aparência ----------

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

    // ---------- Apps usados recentemente (feature: alternar apps) ----------

    /**
     * Registra [packageName] como o app mais recentemente aberto. Mantém
     * no máximo [MAX_RECENT_APPS] entradas, sem duplicatas, mais recente
     * primeiro. Isso alimenta a lista local de "Usados recentemente" —
     * a alternativa leve e permitida a desenhar a tela nativa de Recentes
     * do Android (exclusiva do SystemUI, indisponível para launchers de
     * terceiros).
     */
    fun recordAppLaunch(packageName: String) {
        val current = getRecentPackageNames().toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        val trimmed = current.take(MAX_RECENT_APPS)
        prefs.edit().putString(KEY_RECENT_APPS, trimmed.joinToString(",")).apply()
    }

    fun getRecentPackageNames(): List<String> {
        val raw = prefs.getString(KEY_RECENT_APPS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ---------- Apps fixados por modo contextual (Spaces editáveis) ----------

    /**
     * Cada [ContextMode] tem sua própria lista de apps "fixados" no topo
     * (Space "Prioritários"). É o mecanismo que permite ao usuário
     * personalizar o conteúdo de cada modo (ex.: fixar E-mail/Agenda no
     * modo Trabalho, fixar Música/Alarme no modo Noite).
     */
    fun getPinnedPackageNames(mode: ContextMode): Set<String> {
        val raw = prefs.getString(pinnedKey(mode), null) ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun togglePinned(mode: ContextMode, packageName: String) {
        val current = getPinnedPackageNames(mode).toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putString(pinnedKey(mode), current.joinToString(",")).apply()
    }

    private fun pinnedKey(mode: ContextMode) = KEY_PINNED_PREFIX + mode.name

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_RECENT_APPS = "recent_apps"
        private const val KEY_PINNED_PREFIX = "pinned_apps_"
        private const val MAX_RECENT_APPS = 8
    }
}
