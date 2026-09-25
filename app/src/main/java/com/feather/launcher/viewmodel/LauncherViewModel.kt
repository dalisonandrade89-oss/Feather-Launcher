package com.feather.launcher.viewmodel

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserHandle
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
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * FIX #26 (P2 da auditoria): os StateFlow expostos aqui para a UI usam
 * `PersistentList`/`PersistentSet`/`PersistentMap`
 * (kotlinx.collections.immutable) em vez de `List`/`Set`/`Map` puros do
 * Kotlin. Sem Kotlin 2.0 (que traz "strong skipping" por padrão), o
 * compilador do Compose trata qualquer List/Map/Set como "instável"
 * (podem ser mutáveis por baixo), então qualquer composable que recebe
 * esses tipos como parâmetro NUNCA pula recomposição — cada notificação
 * postada recompunha Home, Gaveta e Widgets inteiras, mesmo as duas
 * últimas não tendo nada de fato relevante mudado. Os tipos
 * `Persistent*` são reconhecidos como estáveis pelo compilador do
 * Compose mesmo sem strong skipping, e ainda suportam os operadores
 * `+`/`-`/`put` que o código já usava — troca de tipo, não de lógica.
 */
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

    val filteredApps: StateFlow<PersistentList<AppInfo>> =
        combine(_allApps, _searchQuery) { apps, query ->
            val trimmed = query.trim()
            val filtered = if (trimmed.isBlank()) {
                apps
            } else {
                val normalizedQuery = trimmed.toSearchNormalized()
                apps.filter { app ->
                    app.searchableLabel.contains(normalizedQuery) ||
                        app.packageName.lowercase().contains(normalizedQuery)
                }
            }
            filtered.toPersistentList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, persistentListOf())

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

    // FIX #23 (P8 da auditoria): getSpaces() lê e faz parsing das prefs
    // — chamar duas vezes para a mesma leitura é trabalho em dobro à toa
    // no construtor do ViewModel (roda na Main thread).
    private val initialSpaces = appPrefs.getSpaces().toPersistentList()

    private val _spaces = MutableStateFlow(initialSpaces)
    val spaces: StateFlow<PersistentList<SpaceDef>> = _spaces

    private val _currentSpaceId = MutableStateFlow(initialSpaces.firstOrNull()?.id.orEmpty())
    val currentSpaceId: StateFlow<String> = _currentSpaceId

    fun onSpaceSelected(spaceId: String) {
        _currentSpaceId.value = spaceId
    }

    fun addSpace(name: String) {
        _spaces.value = appPrefs.addSpace(name).toPersistentList()
    }

    fun renameSpace(spaceId: String, newName: String) {
        _spaces.value = appPrefs.renameSpace(spaceId, newName).toPersistentList()
    }

    fun deleteSpace(spaceId: String) {
        _spaces.value = appPrefs.deleteSpace(spaceId).toPersistentList()
        _assignments.value = _assignments.value.remove(spaceId)
        if (_currentSpaceId.value == spaceId) {
            _currentSpaceId.value = _spaces.value.firstOrNull()?.id.orEmpty()
        }
    }

    // ---------- Vínculo de apps por Space (regra de exclusividade) ----------

    private val _assignments = MutableStateFlow(
        appPrefs.getAllAssignments(initialSpaces.map { it.id })
            .mapValues { (_, pkgs) -> pkgs.toPersistentSet() }
            .toPersistentMap()
    )
    val assignments: StateFlow<PersistentMap<String, PersistentSet<String>>> = _assignments

    /** Apps visíveis no Space atualmente selecionado (só os explicitamente vinculados a ele). */
    val currentSpaceApps: StateFlow<PersistentList<AppInfo>> =
        combine(_allApps, _currentSpaceId, _assignments) { apps, spaceId, assignmentsMap ->
            val pkgs = assignmentsMap[spaceId].orEmpty()
            apps.filter { it.packageName in pkgs }.toPersistentList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, persistentListOf())

    /** Vincula/desvincula [app] ao Space [spaceId] — chamado pelo menu de contexto na Gaveta. */
    fun toggleAppInSpace(spaceId: String, app: AppInfo) {
        appPrefs.toggleAppInSpace(spaceId, app.packageName)
        val current: Set<String> = _assignments.value[spaceId].orEmpty()
        val updated = if (app.packageName in current) current - app.packageName else current + app.packageName
        _assignments.value = _assignments.value.put(spaceId, updated.toPersistentSet())
    }

    /** Remove [app] apenas do Space atual — atalho de toque longo na própria Home. */
    fun removeAppFromCurrentSpace(app: AppInfo) {
        toggleAppInSpace(_currentSpaceId.value, app)
    }

    // ---------- Widgets (tela da esquerda) ----------

    private val _widgetPlacements = MutableStateFlow<PersistentList<WidgetPlacement>>(persistentListOf())
    val widgetPlacements: StateFlow<PersistentList<WidgetPlacement>> = _widgetPlacements

    fun onWidgetAdded(appWidgetId: Int, spanX: Int, spanY: Int) {
        widgetPrefs.addPlacement(appWidgetId, spanX, spanY)
        _widgetPlacements.value = widgetPrefs.getPlacements().toPersistentList()
    }

    fun onWidgetRemoved(appWidgetId: Int) {
        widgetPrefs.removePlacement(appWidgetId)
        _widgetPlacements.value = widgetPrefs.getPlacements().toPersistentList()
    }

    fun onWidgetResized(appWidgetId: Int, spanX: Int, spanY: Int) {
        widgetPrefs.updateSpan(appWidgetId, spanX, spanY)
        _widgetPlacements.value = widgetPrefs.getPlacements().toPersistentList()
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

    // ---------- Notificações ----------

    /** Repassa diretamente o singleton do serviço de notificações para a UI observar. */
    val lastNotification: StateFlow<LastNotificationInfo?> = NotificationRepository.lastNotification

    /** Pacotes com notificação ativa no momento — alimenta o ponto nos ícones da Home. */
    val appsWithNotifications: StateFlow<PersistentSet<String>> =
        NotificationRepository.activeNotificationPackages
            .map { it.toPersistentSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, persistentSetOf())

    fun dismissLastNotification(key: String) {
        com.feather.launcher.notification.FeatherNotificationListenerService.dismiss(key)
    }

    // ---------- Atualização automática da lista (instalar/desinstalar/atualizar) ----------

    private val launcherApps: LauncherApps? =
        application.getSystemService(LauncherApps::class.java)

    private var reloadJob: Job? = null

    /** Reagenda o reload com um pequeno debounce — instalar um app costuma disparar
     *  mais de um callback em sequência (removed+added numa atualização, por exemplo). */
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(300)
            loadApps()
        }
    }

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            scheduleReload()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            scheduleReload()
    }

    init {
        _widgetPlacements.value = widgetPrefs.getPlacements().toPersistentList()
        loadApps()
        try {
            launcherApps?.registerCallback(launcherAppsCallback)
        } catch (_: Exception) {
            // Sem essa permissão o launcher não funcionaria mesmo; se
            // falhar aqui, a lista só deixa de se atualizar sozinha.
        }
    }

    override fun onCleared() {
        try {
            launcherApps?.unregisterCallback(launcherAppsCallback)
        } catch (_: Exception) {
        }
        super.onCleared()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _isLoadingApps.value = true
            val apps = appRepository.loadInstalledApps()
            _allApps.value = apps

            // Limpa vínculos de Spaces para apps que não existem mais,
            // para não acumular lixo nas prefs indefinidamente.
            val installedPackageNames = apps.map { it.packageName }.toSet()
            appPrefs.pruneAssignments(installedPackageNames)
            _assignments.value = appPrefs.getAllAssignments(_spaces.value.map { it.id })
                .mapValues { (_, pkgs) -> pkgs.toPersistentSet() }
                .toPersistentMap()

            _isLoadingApps.value = false
        }
    }
}
