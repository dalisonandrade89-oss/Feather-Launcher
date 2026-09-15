package com.aviatelite.launcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aviatelite.launcher.data.AppCategory
import com.aviatelite.launcher.data.AppInfo
import com.aviatelite.launcher.data.AppRepository
import com.aviatelite.launcher.data.ContextMode
import com.aviatelite.launcher.widget.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ordem de categorias mostrada em cada modo contextual. Simples "mapa"
 * fixo (sem heurísticas pesadas), que já entrega a sensação de
 * "Spaces" do Aviate a um custo de CPU praticamente nulo.
 */
private val modeCategoryOrder: Map<ContextMode, List<AppCategory>> = mapOf(
    ContextMode.DIA_A_DIA to listOf(
        AppCategory.COMUNICACAO, AppCategory.MIDIA, AppCategory.UTILITARIOS,
        AppCategory.PRODUTIVIDADE, AppCategory.JOGOS, AppCategory.OUTROS
    ),
    ContextMode.TRABALHO to listOf(
        AppCategory.PRODUTIVIDADE, AppCategory.COMUNICACAO, AppCategory.UTILITARIOS,
        AppCategory.MIDIA, AppCategory.OUTROS, AppCategory.JOGOS
    ),
    ContextMode.NOITE to listOf(
        AppCategory.MIDIA, AppCategory.JOGOS, AppCategory.COMUNICACAO,
        AppCategory.UTILITARIOS, AppCategory.PRODUTIVIDADE, AppCategory.OUTROS
    )
)

data class AppSpace(
    val category: AppCategory,
    val apps: List<AppInfo>
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val widgetPrefs = WidgetPrefs(application)

    // ---------- Apps ----------

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps

    // ---------- Gaveta de apps (tela da direita) ----------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredApps: StateFlow<List<AppInfo>> =
        combine(_allApps, _searchQuery) { apps, query ->
            if (query.isBlank()) {
                apps
            } else {
                val q = query.trim()
                apps.filter { it.label.contains(q, ignoreCase = true) }
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    // ---------- Modo contextual (tela central) ----------

    private val _contextMode = MutableStateFlow(ContextMode.DIA_A_DIA)
    val contextMode: StateFlow<ContextMode> = _contextMode

    fun onContextModeChange(mode: ContextMode) {
        _contextMode.value = mode
    }

    val spaces: StateFlow<List<AppSpace>> =
        combine(_allApps, _contextMode) { apps, mode ->
            val grouped = apps.groupBy { it.category }
            val order = modeCategoryOrder[mode].orEmpty()
            order.mapNotNull { category ->
                val appsInCategory = grouped[category].orEmpty()
                if (appsInCategory.isEmpty()) null else AppSpace(category, appsInCategory)
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    // ---------- Widgets (tela da esquerda) ----------

    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds

    init {
        _widgetIds.value = widgetPrefs.getWidgetIds()
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _isLoadingApps.value = true
            val apps = appRepository.loadInstalledApps()
            _allApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun onWidgetAdded(appWidgetId: Int) {
        widgetPrefs.addWidgetId(appWidgetId)
        _widgetIds.value = widgetPrefs.getWidgetIds()
    }

    fun onWidgetRemoved(appWidgetId: Int) {
        widgetPrefs.removeWidgetId(appWidgetId)
        _widgetIds.value = widgetPrefs.getWidgetIds()
    }
}
