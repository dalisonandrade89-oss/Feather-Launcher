package com.aviatelite.launcher.widget

import android.content.Context

/** Um widget adicionado, com seu tamanho em células da grade (colunas x linhas). */
data class WidgetPlacement(
    val appWidgetId: Int,
    val spanX: Int,
    val spanY: Int
)

/**
 * Guarda a lista de widgets adicionados pelo usuário (e seu tamanho em
 * células) para que sobrevivam a reinícios do launcher. Leve o
 * suficiente para não precisar de um banco de dados (Room) só para isso.
 */
class WidgetPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    fun getPlacements(): List<WidgetPlacement> {
        val raw = prefs.getString(KEY_PLACEMENTS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val spanX = parts[1].toIntOrNull() ?: DEFAULT_SPAN_X
            val spanY = parts[2].toIntOrNull() ?: DEFAULT_SPAN_Y
            WidgetPlacement(id, spanX.coerceIn(1, MAX_SPAN_X), spanY.coerceIn(1, MAX_SPAN_Y))
        }
    }

    fun addPlacement(appWidgetId: Int, spanX: Int, spanY: Int) {
        val current = getPlacements().filterNot { it.appWidgetId == appWidgetId }.toMutableList()
        current.add(WidgetPlacement(appWidgetId, spanX.coerceIn(1, MAX_SPAN_X), spanY.coerceIn(1, MAX_SPAN_Y)))
        savePlacements(current)
    }

    fun updateSpan(appWidgetId: Int, spanX: Int, spanY: Int) {
        val current = getPlacements().map {
            if (it.appWidgetId == appWidgetId) {
                it.copy(spanX = spanX.coerceIn(1, MAX_SPAN_X), spanY = spanY.coerceIn(1, MAX_SPAN_Y))
            } else {
                it
            }
        }
        savePlacements(current)
    }

    fun removePlacement(appWidgetId: Int) {
        savePlacements(getPlacements().filterNot { it.appWidgetId == appWidgetId })
    }

    private fun savePlacements(placements: List<WidgetPlacement>) {
        val serialized = placements.joinToString(",") { "${it.appWidgetId}:${it.spanX}:${it.spanY}" }
        prefs.edit().putString(KEY_PLACEMENTS, serialized).apply()
    }

    companion object {
        private const val KEY_PLACEMENTS = "widget_placements"
        const val GRID_COLUMNS = 4
        const val MAX_SPAN_X = GRID_COLUMNS
        const val MAX_SPAN_Y = 4
        const val DEFAULT_SPAN_X = 2
        const val DEFAULT_SPAN_Y = 2
        /** Tamanho de referência (dp) de uma célula da grade, usado tanto para
         *  desenhar a grade quanto para sugerir o tamanho de um widget a partir
         *  do seu minWidth/minHeight declarado pelo provider. */
        const val CELL_SIZE_DP = 90
    }
}
