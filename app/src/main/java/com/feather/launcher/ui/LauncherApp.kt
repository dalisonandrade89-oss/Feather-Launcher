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
    recentApps: List<AppInfo>,
    lastNotification: LastNotificationInfo?,
    onNotificationClick: (LastNotificationInfo) -> Unit,
    notificationAccessGranted: Boolean,
    onRequestNotificationAccess: () -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onRemoveAppFromCurrentSpace: (AppInfo) -> Unit,
    onRecentAppLongClick: (AppInfo) -> Unit,
    widgetPlacements: List<WidgetPlacement>,
    createWidgetHostView: (Int) -> AppWidgetHostView?,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, Int, Int) -> Unit,
    loadAvailableWidgets: suspend () -> List<WidgetProviderOption>,
    onWidgetProviderSelected: (WidgetProviderOption, Int, Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }
    val coroutineScope = rememberCoroutineScope()

    // FIX #6 (histórico): "voltar" some retorna para a Home em vez de
    // arriscar fechar a Activity, em qualquer página que não seja a Home.
    BackHandler(enabled = pagerState.currentPage != PAGE_HOME) {
        coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> page },
            beyondViewportPageCount = 0
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
                    recentApps = recentApps,
                    lastNotification = lastNotification,
                    onNotificationClick = onNotificationClick,
                    notificationAccessGranted = notificationAccessGranted,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onAppClick = onAppClick,
                    onAppLongClick = onRemoveAppFromCurrentSpace,
                    onRecentAppLongClick = onRecentAppLongClick
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
