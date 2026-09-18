package com.feather.launcher.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feather.launcher.data.AppDrawerViewMode
import com.feather.launcher.data.AppInfo
import com.feather.launcher.data.AppPrefs
import com.feather.launcher.data.AppRepository
import com.feather.launcher.data.SpaceDef
import com.feather.launcher.data.ThemeMode
import com.feather.launcher.data.hexToColorOrNull
import com.feather.launcher.data.toHexString
import com.feather.launcher.data.toSearchNormalized
import com.feather.launcher.notification.LastNotificationInfo
import com.feather.launcher.notification.NotificationRepository
import com.feather.launcher.widget.WidgetPlacement
import com.feather.launcher.widget.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** Lista ou grade na Gaveta — alternado pelo ícone dentro do campo de busca. */
    private val _drawerViewMode = MutableStateFlow(appPrefs.getDrawerViewMode())
    val drawerViewMode: StateFlow<AppDrawerViewMode> = _drawerViewMode

    fun toggleDrawerViewMode() {
        val newMode = if (_drawerViewMode.value == AppDrawerViewMode.LIST) {
            AppDrawerViewMode.GRID
        } else {
            AppDrawerViewMode.LIST
        }
        _drawerViewMode.value = newMode
        appPrefs.setDrawerViewMode(newMode)
    }

    // ---------- Spaces (abas contextuais dinâmicas) ----------

    private val _spaces = MutableStateFlow(appPrefs.getSpaces())
    val spaces: StateFlow<List<SpaceDef>> = _spaces

    private val _currentSpaceId = MutableStateFlow(appPrefs.getSpaces().firstOrNull()?.id.orEmpty())
    val currentSpaceId: StateFlow<String> = _currentSpaceId

    fun onSpaceSelected(spaceId: String) {
        _currentSpaceId.value = spaceId
    }

    fun addSpace(name: String) {
        _spaces.value = appPrefs.addSpace(name)
    }

    fun renameSpace(spaceId: String, newName: String) {
        _spaces.value = appPrefs.renameSpace(spaceId, newName)
    }

    fun deleteSpace(spaceId: String) {
        _spaces.value = appPrefs.deleteSpace(spaceId)
        _assignments.value = _assignments.value - spaceId
        if (_currentSpaceId.value == spaceId) {
            _currentSpaceId.value = _spaces.value.firstOrNull()?.id.orEmpty()
        }
    }

    // ---------- Vínculo de apps por Space (regra de exclusividade) ----------

    private val _assignments = MutableStateFlow(
        appPrefs.getAllAssignments(_spaces.value.map { it.id })
    )
    val assignments: StateFlow<Map<String, Set<String>>> = _assignments

    /** Apps visíveis no Space atualmente selecionado (só os explicitamente vinculados a ele). */
    val currentSpaceApps: StateFlow<List<AppInfo>> =
        combine(_allApps, _currentSpaceId, _assignments) { apps, spaceId, assignmentsMap ->
            val pkgs = assignmentsMap[spaceId].orEmpty()
            apps.filter { it.packageName in pkgs }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Vincula/desvincula [app] ao Space [spaceId] — chamado pelo menu de contexto na Gaveta. */
    fun toggleAppInSpace(spaceId: String, app: AppInfo) {
        appPrefs.toggleAppInSpace(spaceId, app.packageName)
        val current = _assignments.value[spaceId].orEmpty()
        val updated = if (app.packageName in current) current - app.packageName else current + app.packageName
        _assignments.value = _assignments.value + (spaceId to updated)
    }

    /** Remove [app] apenas do Space atual — atalho de toque longo na própria Home. */
    fun removeAppFromCurrentSpace(app: AppInfo) {
        toggleAppInSpace(_currentSpaceId.value, app)
    }

    // ---------- Widgets (tela da esquerda) ----------

    private val _widgetPlacements = MutableStateFlow<List<WidgetPlacement>>(emptyList())
    val widgetPlacements: StateFlow<List<WidgetPlacement>> = _widgetPlacements

    fun onWidgetAdded(appWidgetId: Int, spanX: Int, spanY: Int) {
        widgetPrefs.addPlacement(appWidgetId, spanX, spanY)
        _widgetPlacements.value = widgetPrefs.getPlacements()
    }

    fun onWidgetRemoved(appWidgetId: Int) {
        widgetPrefs.removePlacement(appWidgetId)
        _widgetPlacements.value = widgetPrefs.getPlacements()
    }

    fun onWidgetResized(appWidgetId: Int, spanX: Int, spanY: Int) {
        widgetPrefs.updateSpan(appWidgetId, spanX, spanY)
        _widgetPlacements.value = widgetPrefs.getPlacements()
    }

    // ---------- Aparência ----------

    private val _themeMode = MutableStateFlow(appPrefs.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun onThemeModeChange(mode: ThemeMode) {
        _themeMode.value = mode
        appPrefs.setThemeMode(mode)
    }

    private val _accentColor = MutableStateFlow(appPrefs.getAccentColorHex()?.hexToColorOrNull())
    val accentColor: StateFlow<Color?> = _accentColor

    fun onAccentColorChange(color: Color?) {
        _accentColor.value = color
        appPrefs.setAccentColorHex(color?.toHexString())
    }

    // ---------- Apps usados recentemente ----------

    private val _recentPackageNames = MutableStateFlow(appPrefs.getRecentPackageNames())

    val recentApps: StateFlow<List<AppInfo>> =
        combine(_allApps, _recentPackageNames) { apps, recentNames ->
            val byPackage = apps.associateBy { it.packageName }
            recentNames.mapNotNull { byPackage[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onAppLaunched(app: AppInfo) {
        appPrefs.recordAppLaunch(app.packageName)
        _recentPackageNames.value = appPrefs.getRecentPackageNames()
    }

    // ---------- Notificações ----------

    /** Repassa diretamente o singleton do serviço de notificações para a UI observar. */
    val lastNotification: StateFlow<LastNotificationInfo?> = NotificationRepository.lastNotification

    fun dismissLastNotification(key: String) {
        com.feather.launcher.notification.FeatherNotificationListenerService.dismiss(key)
    }

    init {
        _widgetPlacements.value = widgetPrefs.getPlacements()
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
}
