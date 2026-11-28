package com.feather.launcher.pwa

import android.content.Context
import com.feather.launcher.data.toSearchNormalized
import java.util.UUID

class PwaPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShortcuts(): List<PwaShortcut> {
        val raw = prefs.getString(KEY_PWA_SHORTCUTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD_SEPARATOR)
            if (fields.size != 4) return@mapNotNull null
            val id = fields[0].trim()
            val name = fields[1].trim()
            val url = normalizeUrl(fields[2])
            val browser = PwaBrowser.fromId(fields[3].trim())
            if (id.isEmpty() || name.isEmpty() || url.isEmpty()) return@mapNotNull null
            PwaShortcut(
                id = id,
                name = name,
                url = url,
                browser = browser,
                searchableName = name.toSearchNormalized()
            )
        }
    }

    fun addShortcut(name: String, rawUrl: String, browser: PwaBrowser): List<PwaShortcut> {
        val trimmedName = name.trim()
        val normalizedUrl = normalizeUrl(rawUrl)
        if (trimmedName.isEmpty() || normalizedUrl.isEmpty()) return getShortcuts()
        val shortcut = PwaShortcut(
            id = "pwa_" + UUID.randomUUID().toString().take(8),
            name = trimmedName,
            url = normalizedUrl,
            browser = browser,
            searchableName = trimmedName.toSearchNormalized()
        )
        val updated = (getShortcuts() + shortcut).sortedBy { it.searchableName }
        saveShortcuts(updated)
        return updated
    }

    fun removeShortcut(id: String): List<PwaShortcut> {
        val updated = getShortcuts().filterNot { it.id == id }
        saveShortcuts(updated)
        return updated
    }

    private fun saveShortcuts(list: List<PwaShortcut>) {
        val serialized = list.joinToString(ENTRY_SEPARATOR) { item ->
            listOf(item.id, item.name, item.url, item.browser.id).joinToString(FIELD_SEPARATOR)
        }
        prefs.edit().putString(KEY_PWA_SHORTCUTS, serialized).apply()
    }

    companion object {
        private const val PREFS_NAME = "feather_pwa_prefs"
        private const val KEY_PWA_SHORTCUTS = "pwa_shortcuts_v1"
        private const val ENTRY_SEPARATOR = "\u0002"
        private const val FIELD_SEPARATOR = "\u0001"

        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }
}
