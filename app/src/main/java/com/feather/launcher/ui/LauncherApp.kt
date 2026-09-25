package com.feather.launcher.ui

import android.appwidget.AppWidgetHostView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feather.launcher.data.AppDrawerViewMode
import com.feather.launcher.data.AppInfo
import com.feather.launcher.data.SpaceDef
import com.feather.launcher.data.ThemeMode
import com.feather.launcher.notification.LastNotificationInfo
import com.feather.launcher.ui.screens.AppDrawerScreen
import com.feather.launcher.ui.screens.HomeScreen
import com.feather.launcher.ui.screens.WidgetsPanelScreen
import com.feather.launcher.widget.WidgetPlacement
import com.feather.launcher.widget.WidgetProviderOption
import kotlinx.coroutines.launch

private const val PAGE_WIDGETS = 0
private const val PAGE_HOME = 1
private const val PAGE_DRAWER = 2
private const val PAGE_COUNT = 3

/**
 * Composable raiz do launcher: 3 páginas navegáveis por swipe horizontal
 * (HorizontalPager é uma API estável do Compose Foundation, sem
 * necessidade de @OptIn experimental nas versões usadas aqui).
 */
@Composable
fun LauncherApp(
    filteredApps: List<AppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    drawerViewMode: AppDrawerViewMode,
    onToggleDrawerViewMode: () -> Unit,
    spaces: List<SpaceDef>,
    currentSpaceId: String,
    onSpaceSelected: (String) -> Unit,
    onAddSpace: (String) -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onDeleteSpace: (String) -> Unit,
    currentSpaceApps: List<AppInfo>,
    assignments: Map<String, Set<String>>,
    onToggleAppInSpace: (String, AppInfo) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentColor: Color?,
    onAccentColorChange: (Color?) -> Unit,
    appsWithNotifications: Set<String>,
    lastNotification: LastNotificationInfo?,
    onNotificationClick: (LastNotificationInfo) -> Unit,
    notificationAccessGranted: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onRemoveAppFromCurrentSpace: (AppInfo) -> Unit,
    widgetPlacements: List<WidgetPlacement>,
    createWidgetHostView: (Int) -> AppWidgetHostView?,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, Int, Int) -> Unit,
    loadAvailableWidgets: suspend () -> List<WidgetProviderOption>,
    onWidgetProviderSelected: (WidgetProviderOption, Int, Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }
    val coroutineScope = rememberCoroutineScope()

    // FIX #18: o BackHandler ficava DESLIGADO (enabled = false) na Home,
    // deixando o botão "voltar" para o comportamento padrão do sistema —
    // que, numa Activity HOME/launchMode singleTask, é encerrar a
    // Activity. O Android então a recria na hora (é a launcher padrão,
    // precisa reaparecer), o que na prática o usuário vê como um
    // "pisca": a tela reconstrói a grade de apps do zero. Agora o
    // BackHandler fica sempre ativo; na Home ele simplesmente não faz
    // nada, consumindo o evento em vez de deixá-lo derrubar a Activity.
    BackHandler(enabled = true) {
        if (pagerState.currentPage != PAGE_HOME) {
            coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
        }
        // Na Home: de propósito, não faz nada.
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> page },
            // FIX #19 (P1): com beyondViewportPageCount = 0, a página de
            // Widgets era DESCARTADA inteira sempre que você não estava
            // nela — cada volta recriava todos os AppWidgetHostView do
            // zero (reinflando os RemoteViews), exatamente o custo que a
            // troca do LazyVerticalGrid por grade manual já tinha tentado
            // evitar (só que aquela correção resolveu a recriação AO
            // ROLAR dentro do painel, não a recriação ao TROCAR de
            // página). Com 3 páginas fixas, manter Widgets e Home sempre
            // compostas (1 página além da visível) cobre o caminho mais
            // comum de uso (Home ↔ Widgets) sem manter a Gaveta (a
            // página mais pesada, com a lista completa de apps) presa em
            // memória o tempo todo sem necessidade.
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                PAGE_WIDGETS -> WidgetsPanelScreen(
                    placements = widgetPlacements,
                    createHostView = createWidgetHostView,
                    onRemoveWidget = onRemoveWidget,
                    onResizeWidget = onResizeWidget,
                    loadAvailableWidgets = loadAvailableWidgets,
                    onWidgetProviderSelected = onWidgetProviderSelected
                )

                PAGE_HOME -> HomeScreen(
                    spaces = spaces,
                    currentSpaceId = currentSpaceId,
                    onSpaceSelected = onSpaceSelected,
                    onAddSpace = onAddSpace,
                    onRenameSpace = onRenameSpace,
                    onDeleteSpace = onDeleteSpace,
                    spaceApps = currentSpaceApps,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    accentColor = accentColor,
                    onAccentColorChange = onAccentColorChange,
                    appsWithNotifications = appsWithNotifications,
                    lastNotification = lastNotification,
                    onNotificationClick = onNotificationClick,
                    notificationAccessGranted = notificationAccessGranted,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onAppClick = onAppClick,
                    onAppLongClick = onRemoveAppFromCurrentSpace
                )

                PAGE_DRAWER -> AppDrawerScreen(
                    apps = filteredApps,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    viewMode = drawerViewMode,
                    onToggleViewMode = onToggleDrawerViewMode,
                    spaces = spaces,
                    assignments = assignments,
                    onToggleAppInSpace = onToggleAppInSpace,
                    onAppClick = onAppClick
                )
            }
        }

        PageIndicator(
            pageCount = PAGE_COUNT,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}
