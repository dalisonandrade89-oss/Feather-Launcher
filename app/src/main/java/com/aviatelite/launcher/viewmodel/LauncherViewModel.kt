package com.aviatelite.launcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aviatelite.launcher.data.AppCategory
import com.aviatelite.launcher.data.AppInfo
import com.aviatelite.launcher.data.AppPrefs
import com.aviatelite.launcher.data.AppRepository
import com.aviatelite.launcher.data.ContextMode
import com.aviatelite.launcher.data.ThemeMode
import com.aviatelite.launcher.data.toSearchNormalized
import com.aviatelite.launcher.widget.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

/**
 * Um "Space" da Home: tanto as categorias automáticas (Mídia,
 * Produtividade...) quanto o Space especial "Prioritários", formado
 * pelos apps que o usuário fixou manualmente para o modo atual.
 */
data class AppSpace(
    val id: String,
    val title: String,
    val apps: List<AppInfo>,
    val isPinnedSpace: Boolean = false
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val widgetPrefs = WidgetPrefs(application)
    private val appPrefs = AppPrefs(application)

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
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                apps
            } else {
                // Normaliza acentos/maiúsculas e também compara com o
                // packageName, então buscar "whats" encontra "WhatsApp" e
                // buscar "camera" encontra "Câmera".
                val normalizedQuery = trimmed.toSearchNormalized()
                apps.filter { app ->
                    app.searchableLabel.contains(normalizedQuery) ||
                        app.packageName.lowercase().contains(normalizedQuery)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    // ---------- Modo contextual (tela central) ----------

    private val _contextMode = MutableStateFlow(ContextMode.DIA_A_DIA)
    val contextMode: StateFlow<ContextMode> = _contextMode

    fun onContextModeChange(mode: ContextMode) {
        _contextMode.value = mode
    }

    // Apps fixados por modo (Spaces editáveis pelo usuário). Carregado
    // uma vez do AppPrefs e mantido em memória, atualizado de forma
    // otimista a cada toggle para refletir na UI instantaneamente.
    private val _pinnedByMode = MutableStateFlow(
        ContextMode.entries.associateWith { mode -> appPrefs.getPinnedPackageNames(mode) }
    )

    /** Fixa/desfixa [app] no Space "Prioritários" do modo atualmente selecionado. */
    fun onToggleAppPinned(app: AppInfo) {
        val mode = _contextMode.value
        appPrefs.togglePinned(mode, app.packageName)
        _pinnedByMode.value = _pinnedByMode.value.toMutableMap().apply {
            this[mode] = appPrefs.getPinnedPackageNames(mode)
        }
    }

    fun isAppPinnedInCurrentMode(app: AppInfo): Boolean {
        val mode = _contextMode.value
        return _pinnedByMode.value[mode]?.contains(app.packageName) == true
    }

    val spaces: StateFlow<List<AppSpace>> =
        combine(_allApps, _contextMode, _pinnedByMode) { apps, mode, pinnedByMode ->
            val pinnedNames = pinnedByMode[mode].orEmpty()
            val pinnedApps = apps.filter { it.packageName in pinnedNames }

            val grouped = apps.groupBy { it.category }
            val order = modeCategoryOrder[mode].orEmpty()
            val categorySpaces = order.mapNotNull { category ->
                // Apps já fixados não se repetem dentro da própria categoria,
                // para não duplicar visualmente o mesmo app na Home.
                val appsInCategory = grouped[category].orEmpty()
                    .filterNot { it.packageName in pinnedNames }
                if (appsInCategory.isEmpty()) null
                else AppSpace(id = category.name, title = category.displayName, apps = appsInCategory)
            }

            if (pinnedApps.isEmpty()) {
                categorySpaces
            } else {
                val pinnedSpace = AppSpace(
                    id = "pinned_${mode.name}",
                    title = "Prioritários \u2022 ${mode.label}",
                    apps = pinnedApps,
                    isPinnedSpace = true
                )
                listOf(pinnedSpace) + categorySpaces
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- Widgets (tela da esquerda) ----------

    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds

    // ---------- Aparência ----------

    private val _themeMode = MutableStateFlow(appPrefs.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun onThemeModeChange(mode: ThemeMode) {
        _themeMode.value = mode
        appPrefs.setThemeMode(mode)
    }

    // ---------- Apps usados recentemente ----------

    private val _recentPackageNames = MutableStateFlow(appPrefs.getRecentPackageNames())

    val recentApps: StateFlow<List<AppInfo>> =
        combine(_allApps, _recentPackageNames) { apps, recentNames ->
            val byPackage = apps.associateBy { it.packageName }
            recentNames.mapNotNull { byPackage[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Deve ser chamado sempre que um app é efetivamente aberto pelo usuário. */
    fun onAppLaunched(app: AppInfo) {
        appPrefs.recordAppLaunch(app.packageName)
        _recentPackageNames.value = appPrefs.getRecentPackageNames()
    }

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
