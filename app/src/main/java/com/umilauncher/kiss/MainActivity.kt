package com.umilauncher.kiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umilauncher.kiss.ui.screens.AppDrawerScreen
import com.umilauncher.kiss.ui.screens.HomeScreen
import com.umilauncher.kiss.ui.theme.UmiLauncherTheme

/**
 * Activity única (padrão de launcher). Evitamos Navigation Compose de
 * propósito: para 2 telas, a lib de navegação (com seu back stack,
 * NavHost, argumentos serializados) é overhead de memória e de APK que
 * não se paga aqui. Alternamos as duas telas com um "if" simples sobre
 * um StateFlow - o Compose já faz o diffing de recomposição por nós.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels {
        val app = application as LauncherApp
        LauncherViewModel.Factory(app.appRepository, app.favoritesStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LauncherApp

        setContent {
            UmiLauncherTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                BackHandler(enabled = state.isDrawerOpen) {
                    viewModel.onDrawerVisibilityChange(false)
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    // Sem Crossfade/animação de transição entre as telas:
                    // troca instantânea = zero frames de composição extra
                    // gastos só em efeito visual, o que ajuda a manter os
                    // 60fps consistentes num SoC de entrada como o Helio G25.
                    if (state.isDrawerOpen) {
                        AppDrawerScreen(
                            state = state,
                            iconCache = app.iconCache,
                            onQueryChange = viewModel::onQueryChange,
                            onAppClick = { launchAndClose(it) },
                            onAppLongClick = { viewModel.onToggleFavorite(it) }
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            iconCache = app.iconCache,
                            onAppClick = { launchAndClose(it) },
                            onAppLongClick = { viewModel.onToggleFavorite(it) },
                            onOpenDrawer = { viewModel.onDrawerVisibilityChange(true) }
                        )
                    }
                }
            }
        }
    }

    private fun launchAndClose(app: com.umilauncher.kiss.data.AppInfo) {
        viewModel.onAppClick(app)
        viewModel.onDrawerVisibilityChange(false)
    }
}
