package com.aviatelite.launcher.ui

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
import androidx.compose.ui.unit.dp
import com.aviatelite.launcher.data.AppInfo
import com.aviatelite.launcher.data.ContextMode
import com.aviatelite.launcher.data.ThemeMode
import com.aviatelite.launcher.ui.screens.AppDrawerScreen
import com.aviatelite.launcher.ui.screens.HomeScreen
import com.aviatelite.launcher.ui.screens.WidgetsPanelScreen
import com.aviatelite.launcher.viewmodel.AppSpace
import kotlinx.coroutines.launch

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
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    recentApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onRecentAppLongClick: (AppInfo) -> Unit,
    widgetIds: List<Int>,
    createWidgetHostView: (Int) -> AppWidgetHostView?,
    onAddWidgetClick: () -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }
    val coroutineScope = rememberCoroutineScope()

    // FIX #6 (gesto de voltar causando instabilidade/crash): em vez de
    // deixar o sistema tratar o "voltar" (que, numa Activity de HOME,
    // pode se comportar de forma inconsistente entre fabricantes/OEMs
    // — parte do relato original em dispositivos MediaTek), interceptamos
    // aqui: se o usuário está em Widgets ou na Gaveta, "voltar" apenas
    // retorna para a Home central. Só fica DESATIVADO (deixando o
    // sistema seguir seu fluxo padrão, que para uma launcher é não fazer
    // nada) quando já estamos na Home — nunca finalizamos a MainActivity.
    BackHandler(enabled = pagerState.currentPage != PAGE_HOME) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(PAGE_HOME)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // FIX #4 (performance do Pager): key estável por página evita
            // que o Compose recrie/realoque o slot de composição de uma
            // página ao reordenar/recompor o pai — no Helio G25 isso
            // reduz claramente o "engasgo" percebido durante o swipe.
            key = { page -> page },
            // Só a página atual + 1 vizinha de cada lado ficam compostas
            // (valor padrão), suficiente para o swipe ficar fluido sem
            // manter 3 telas inteiras sempre "quentes" em memória.
            beyondViewportPageCount = 0
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
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    recentApps = recentApps,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick,
                    onRecentAppLongClick = onRecentAppLongClick
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
