package com.aviatelite.launcher.ui

import android.appwidget.AppWidgetHostView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aviatelite.launcher.data.AppInfo
import com.aviatelite.launcher.data.ContextMode
import com.aviatelite.launcher.ui.screens.AppDrawerScreen
import com.aviatelite.launcher.ui.screens.HomeScreen
import com.aviatelite.launcher.ui.screens.WidgetsPanelScreen
import com.aviatelite.launcher.viewmodel.AppSpace

private const val PAGE_WIDGETS = 0
private const val PAGE_HOME = 1
private const val PAGE_DRAWER = 2
private const val PAGE_COUNT = 3

/**
 * Composable raiz do launcher: 3 páginas navegáveis por swipe horizontal
 * (HorizontalPager é uma API estável do Compose Foundation, sem
 * necessidade de @OptIn experimental nas versões usadas aqui).
 *
 * A página inicial é sempre a Home (centro), como em qualquer launcher
 * estilo Aviate/Nova.
 */
@Composable
fun LauncherApp(
    apps: List<AppInfo>,
    filteredApps: List<AppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    spaces: List<AppSpace>,
    contextMode: ContextMode,
    onContextModeChange: (ContextMode) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    widgetIds: List<Int>,
    createWidgetHostView: (Int) -> AppWidgetHostView?,
    onAddWidgetClick: () -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                PAGE_WIDGETS -> WidgetsPanelScreen(
                    widgetIds = widgetIds,
                    createHostView = createWidgetHostView,
                    onAddWidgetClick = onAddWidgetClick,
                    onRemoveWidget = onRemoveWidget
                )

                PAGE_HOME -> HomeScreen(
                    spaces = spaces,
                    currentMode = contextMode,
                    onModeSelected = onContextModeChange,
                    onAppClick = onAppClick
                )

                PAGE_DRAWER -> AppDrawerScreen(
                    apps = filteredApps,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onAppClick = onAppClick
                )
            }
        }

        PageIndicator(
            pageCount = PAGE_COUNT,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
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
