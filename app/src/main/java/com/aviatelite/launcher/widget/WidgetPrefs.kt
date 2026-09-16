package com.aviatelite.launcher.widget

import android.content.Context

/**
 * Guarda a lista de appWidgetIds adicionados pelo usuário para que os
 * widgets sobrevivam a reinícios do launcher. Leve o suficiente para
 * não precisar de um banco de dados (Room) só para isso.
 */
class WidgetPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    fun getWidgetIds(): List<Int> {
        val raw = prefs.getString(KEY_IDS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun addWidgetId(id: Int) {
        val current = getWidgetIds().toMutableList()
        if (!current.contains(id)) {
            current.add(id)
            saveWidgetIds(current)
        }
    }

    fun removeWidgetId(id: Int) {
        val current = getWidgetIds().toMutableList()
        current.remove(id)
        saveWidgetIds(current)
    }

    private fun saveWidgetIds(ids: List<Int>) {
        prefs.edit().putString(KEY_IDS, ids.joinToString(",")).apply()
    }

    companion object {
        private const val KEY_IDS = "widget_ids"
    }
}
