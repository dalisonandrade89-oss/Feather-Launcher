package com.umilauncher.kiss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umilauncher.kiss.data.AppInfo
import com.umilauncher.kiss.data.AppRepository
import com.umilauncher.kiss.data.FavoritesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LauncherUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppInfo> = emptyList(),
    val favoriteApps: List<AppInfo> = emptyList(),
    val query: String = "",
    val filteredApps: List<AppInfo> = emptyList(),
    val isDrawerOpen: Boolean = false
)

class LauncherViewModel(
    private val appRepository: AppRepository,
    private val favoritesStore: FavoritesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    // Flow de busca separado de propósito: combinar um Flow de entrada com
    // o PRÓPRIO StateFlow de saída (_uiState) é um padrão frágil (a cada
    // escrita em _uiState o combine dispara de novo). Isolar a query aqui
    // deixa o fluxo de dados unidirecional e fácil de raciocinar.
    private val queryFlow = MutableStateFlow("")

    // Mantidos fora do StateFlow principal para não recriar a lista inteira
    // a cada emissão - só a derivação (filteredApps) muda.
    private var allAppsCache: List<AppInfo> = emptyList()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = appRepository.loadInstalledApps()
            allAppsCache = apps

            favoritesStore.favoriteKeys.combine(queryFlow) { favKeys, query ->
                favKeys to query
            }.collect { (favKeys, query) ->
                val favorites = allAppsCache.filter { it.key in favKeys }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allApps = allAppsCache,
                    favoriteApps = favorites,
                    query = query,
                    filteredApps = filterApps(allAppsCache, query)
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    fun onDrawerVisibilityChange(open: Boolean) {
        _uiState.value = _uiState.value.copy(isDrawerOpen = open)
        if (!open) {
            // Limpa a busca ao fechar a gaveta; isso propaga por queryFlow
            // e o collect em loadApps() atualiza filteredApps/query juntos.
            queryFlow.value = ""
        }
    }

    fun onAppClick(app: AppInfo) {
        appRepository.launch(app)
    }

    fun onToggleFavorite(app: AppInfo) {
        viewModelScope.launch {
            favoritesStore.toggle(app.key)
        }
    }

    private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        val q = query.trim().lowercase()
        // Filtro simples "contains" - suficiente para o volume de apps de um
        // celular pessoal (dezenas a poucas centenas) e muito mais barato em
        // CPU do que um matching fuzzy/Levenshtein a cada tecla digitada.
        return apps.filter { it.label.lowercase().contains(q) }
    }

    class Factory(
        private val appRepository: AppRepository,
        private val favoritesStore: FavoritesStore
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LauncherViewModel(appRepository, favoritesStore) as T
        }
    }
}
