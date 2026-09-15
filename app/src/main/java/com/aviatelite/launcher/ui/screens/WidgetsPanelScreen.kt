package com.aviatelite.launcher.ui.screens

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Painel vertical com os AppWidgets nativos adicionados pelo usuário.
 *
 * A view do widget (AppWidgetHostView) é criada apenas uma vez por
 * item, fora da árvore de recomposição do Compose, via `factory` do
 * AndroidView — isso evita recriações caras do widget a cada
 * recomposição do LazyColumn.
 */
@Composable
fun WidgetsPanelScreen(
    widgetIds: List<Int>,
    createHostView: (Int) -> AppWidgetHostView?,
    onAddWidgetClick: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWidgetClick) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar widget")
            }
        }
    ) { padding ->
        if (widgetIds.isEmpty()) {
            EmptyWidgetsState(onAddWidgetClick = onAddWidgetClick, padding = padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = widgetIds, key = { it }) { appWidgetId ->
                    WidgetCard(
                        appWidgetId = appWidgetId,
                        createHostView = createHostView,
                        onRemove = { onRemoveWidget(appWidgetId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(
    appWidgetId: Int,
    createHostView: (Int) -> AppWidgetHostView?,
    onRemove: () -> Unit
) {
    // FIX (crash ao adicionar widget): a `factory` do AndroidView roda
    // fora do try-catch normal do Compose — uma exceção aqui (provider
    // desinstalado, binder morto, classe do widget ausente) derrubava o
    // app inteiro. Agora qualquer falha vira um FrameLayout vazio e uma
    // flag de erro, sem propagar a exceção.
    var failedToLoad by remember(appWidgetId) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            factory = { ctx ->
                try {
                    createHostView(appWidgetId) ?: throw IllegalStateException("Widget indisponível")
                } catch (e: Exception) {
                    failedToLoad = true
                    android.widget.FrameLayout(ctx)
                }
            },
            // update() é chamado em recomposições, mas como a view já
            // existe (factory roda 1x), aqui não fazemos trabalho pesado.
            update = { }
        )

        if (failedToLoad) {
            Text(
                text = "Não foi possível carregar este widget",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remover widget",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun EmptyWidgetsState(
    onAddWidgetClick: () -> Unit,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nenhum widget adicionado",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Toque no botão + para adicionar um widget nativo do sistema.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        TextButton(
            onClick = onAddWidgetClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Adicionar widget")
        }
    }
}
