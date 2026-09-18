package com.feather.launcher.data

import android.content.Context
import java.util.UUID

/**
 * Persiste preferências gerais e leves do launcher: ThemeMode + cor de
 * destaque personalizada, apps usados recentemente, a lista de Spaces
 * (padrão + personalizados) e o vínculo (exclusividade) de apps por
 * Space. Usa SharedPreferences por ser síncrono, simples e suficiente
 * para estes volumes pequenos de dados — não justifica o custo de
 * inicialização do DataStore aqui.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- Aparência ----------

    fun getThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.DARK
        return try {
            ThemeMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            ThemeMode.DARK
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /** Cor de destaque (accent) personalizada em hex "#RRGGBB", ou null = usar a cor padrão do tema. */
    fun getAccentColorHex(): String? = prefs.getString(KEY_ACCENT_COLOR, null)

    fun setAccentColorHex(hex: String?) {
        prefs.edit().putString(KEY_ACCENT_COLOR, hex).apply()
    }

    /** Visualização da Gaveta de apps (lista ou grade), lembrada entre sessões. */
    fun getDrawerViewMode(): AppDrawerViewMode {
        val stored = prefs.getString(KEY_DRAWER_VIEW_MODE, null) ?: return AppDrawerViewMode.LIST
        return try {
            AppDrawerViewMode.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            AppDrawerViewMode.LIST
        }
    }

    fun setDrawerViewMode(mode: AppDrawerViewMode) {
        prefs.edit().putString(KEY_DRAWER_VIEW_MODE, mode.name).apply()
    }

    // ---------- Apps usados recentemente ----------

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

    // ---------- Spaces (abas contextuais, padrão + personalizados) ----------

    /**
     * Retorna os Spaces salvos. Na primeira execução, semeia com os 3
     * padrão ("Dia a Dia", "Trabalho", "Noite") e persiste imediatamente,
     * para que renomeações futuras desses 3 também sejam salvas.
     */
    fun getSpaces(): List<SpaceDef> {
        val raw = prefs.getString(KEY_SPACES, null)
        if (raw.isNullOrBlank()) {
            saveSpaces(defaultSpaces)
            return defaultSpaces
        }
        val parsed = raw.split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD_SEPARATOR)
            if (fields.size != 3) return@mapNotNull null
            SpaceDef(id = fields[0], name = fields[1], isCustom = fields[2] == "1")
        }
        return parsed.ifEmpty { defaultSpaces }
    }

    private fun saveSpaces(spaces: List<SpaceDef>) {
        val serialized = spaces.joinToString(ENTRY_SEPARATOR) { space ->
            listOf(space.id, space.name, if (space.isCustom) "1" else "0").joinToString(FIELD_SEPARATOR)
        }
        prefs.edit().putString(KEY_SPACES, serialized).apply()
    }

    /** Cria um novo Space personalizado e retorna a lista atualizada. */
    fun addSpace(name: String): List<SpaceDef> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return getSpaces()
        val newSpace = SpaceDef(id = "custom_" + UUID.randomUUID().toString().take(8), name = trimmed, isCustom = true)
        val updated = getSpaces() + newSpace
        saveSpaces(updated)
        return updated
    }

    fun renameSpace(id: String, newName: String): List<SpaceDef> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return getSpaces()
        val updated = getSpaces().map { if (it.id == id) it.copy(name = trimmed) else it }
        saveSpaces(updated)
        return updated
    }

    /** Exclui um Space personalizado (Spaces padrão nunca são excluídos) e limpa seus vínculos. */
    fun deleteSpace(id: String): List<SpaceDef> {
        val current = getSpaces()
        val target = current.firstOrNull { it.id == id } ?: return current
        if (!target.isCustom) return current
        val updated = current.filterNot { it.id == id }
        saveSpaces(updated)
        prefs.edit().remove(assignmentKey(id)).apply()
        return updated
    }

    // ---------- Vínculo de apps por Space (regra de exclusividade) ----------

    /**
     * Um app só aparece em um Space se seu packageName estiver
     * explicitamente na lista daquele Space. Não há herança/exibição
     * automática cruzada entre Spaces.
     */
    fun getAssignedPackages(spaceId: String): Set<String> {
        val raw = prefs.getString(assignmentKey(spaceId), null) ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun getAllAssignments(spaceIds: List<String>): Map<String, Set<String>> =
        spaceIds.associateWith { getAssignedPackages(it) }

    /** Vincula/desvincula [packageName] ao Space [spaceId] (toggle). */
    fun toggleAppInSpace(spaceId: String, packageName: String) {
        val current = getAssignedPackages(spaceId).toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putString(assignmentKey(spaceId), current.joinToString(",")).apply()
    }

    fun setAppInSpace(spaceId: String, packageName: String, present: Boolean) {
        val current = getAssignedPackages(spaceId).toMutableSet()
        if (present) current.add(packageName) else current.remove(packageName)
        prefs.edit().putString(assignmentKey(spaceId), current.joinToString(",")).apply()
    }

    private fun assignmentKey(spaceId: String) = KEY_SPACE_APPS_PREFIX + spaceId

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_DRAWER_VIEW_MODE = "drawer_view_mode"
        private const val KEY_RECENT_APPS = "recent_apps"
        private const val KEY_SPACES = "spaces_v2"
        private const val KEY_SPACE_APPS_PREFIX = "space_apps_"
        private const val MAX_RECENT_APPS = 8

        // Separadores improváveis em nomes de app/Space, evitando colisão sem precisar de JSON.
        private const val ENTRY_SEPARATOR = "\u0002"
        private const val FIELD_SEPARATOR = "\u0001"
    }
}
