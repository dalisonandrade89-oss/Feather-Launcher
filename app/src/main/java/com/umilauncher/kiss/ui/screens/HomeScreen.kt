package com.umilauncher.kiss.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umilauncher.kiss.LauncherUiState
import com.umilauncher.kiss.R
import com.umilauncher.kiss.data.AppInfo
import com.umilauncher.kiss.data.IconCache

/**
 * Tela inicial no espírito do KISS Launcher: fundo limpo, lista de
 * favoritos em texto (ícones desligados aqui de propósito - é a lista
 * mais vista o tempo todo, então cada bitmap evitado aqui é RAM que fica
 * livre permanentemente, não só sob demanda). A gaveta completa (com
 * ícones) abre com swipe-up ou toque na "busca".
 */
@Composable
fun HomeScreen(
    state: LauncherUiState,
    iconCache: IconCache,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Swipe para cima abre a gaveta - gesto clássico de
                    // launcher tipo KISS/Lawnchair.
                    if (dragAmount < -12f) onOpenDrawer()
                }
            }
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            if (state.favoriteApps.isEmpty()) {
                Text(
                    text = stringResourceCompat(R.string.empty_favorites),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.favoriteApps, key = { it.key }) { app ->
                        AppListItem(
                            app = app,
                            iconCache = iconCache,
                            showIcon = false, // lista de texto puro, no espírito KISS
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }

        val drawerHintInteractionSource = remember { MutableInteractionSource() }
        Text(
            text = stringResourceCompat(R.string.all_apps),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                // Sem ripple: evita uma camada de desenho extra por toque.
                .clickable(
                    interactionSource = drawerHintInteractionSource,
                    indication = null,
                    onClick = onOpenDrawer
                )
        )
    }
}

// Pequeno wrapper para manter os arquivos de UI desacoplados de import
// direto de androidx.compose.ui.res, facilitando testes isolados.
@Composable
private fun stringResourceCompat(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
