package com.aviatelite.launcher.ui.screens

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aviatelite.launcher.widget.WidgetPrefs
import com.aviatelite.launcher.widget.WidgetPlacement
import com.aviatelite.launcher.widget.WidgetProviderOption

/**
 * Painel de widgets: grade redimensionável (FIX #1). Cada widget ocupa
 * `spanX` colunas (de 1 a 4, via LazyVerticalGrid) e uma altura
 * proporcional a `spanY` (1 a 4 "linhas" de referência) — assim o
 * usuário controla o tamanho sem distorcer o conteúdo do widget.
 */
@Composable
fun WidgetsPanelScreen(
    placements: List<WidgetPlacement>,
    createHostView: (Int) -> AppWidgetHostView?,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, Int, Int) -> Unit,
    loadAvailableWidgets: suspend () -> List<WidgetProviderOption>,
    onWidgetProviderSelected: (WidgetProviderOption, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar widget")
            }
        }
    ) { padding ->
        if (placements.isEmpty()) {
            EmptyWidgetsState(onAddWidgetClick = { showPicker = true }, padding = padding)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(WidgetPrefs.GRID_COLUMNS),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = placements,
                    key = { it.appWidgetId },
                    span = { placement -> GridItemSpan(placement.spanX) }
                ) { placement ->
                    WidgetCard(
                        placement = placement,
                        createHostView = createHostView,
                        onRemove = { onRemoveWidget(placement.appWidgetId) },
                        onResize = { spanX, spanY -> onResizeWidget(placement.appWidgetId, spanX, spanY) }
                    )
                }
            }
        }
    }

    if (showPicker) {
        WidgetPickerDialog(
            loadAvailableWidgets = loadAvailableWidgets,
            onDismiss = { showPicker = false },
            onProviderChosen = { option, spanX, spanY ->
                onWidgetProviderSelected(option, spanX, spanY)
                showPicker = false
            }
        )
    }
}

// ---------- Card de um widget já adicionado ----------

@Composable
private fun WidgetCard(
    placement: WidgetPlacement,
    createHostView: (Int) -> AppWidgetHostView?,
    onRemove: () -> Unit,
    onResize: (Int, Int) -> Unit
) {
    var failedToLoad by remember(placement.appWidgetId) { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        // Alça dedicada: fica FORA da área do widget para não interceptar
        // os toques/gestos do próprio conteúdo dele (ex.: rolagem interna).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .pointerInput(placement.appWidgetId) {
                    detectTapGestures(onLongPress = { onRemove() })
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${placement.spanX}x${placement.spanY} — toque e segure para remover",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1
            )
            TextButton(onClick = { showResizeDialog = true }) {
                Text("Tamanho", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remover widget",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((WidgetPrefs.CELL_SIZE_DP * placement.spanY).dp)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                factory = { ctx ->
                    try {
                        createHostView(placement.appWidgetId) ?: throw IllegalStateException("Widget indisponível")
                    } catch (e: Exception) {
                        failedToLoad = true
                        android.widget.FrameLayout(ctx)
                    }
                },
                update = { }
            )

            if (failedToLoad) {
                Text(
                    text = "Não foi possível carregar este widget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }
        }
    }

    if (showResizeDialog) {
        SizeAdjustDialog(
            title = "Tamanho do widget",
            initialSpanX = placement.spanX,
            initialSpanY = placement.spanY,
            onConfirm = { spanX, spanY ->
                onResize(spanX, spanY)
                showResizeDialog = false
            },
            onDismiss = { showResizeDialog = false }
        )
    }
}

@Composable
private fun EmptyWidgetsState(onAddWidgetClick: () -> Unit, padding: PaddingValues) {
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
            text = "Toque no botão + para escolher um widget nativo do sistema.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        TextButton(onClick = onAddWidgetClick, modifier = Modifier.padding(top = 16.dp)) {
            Text("Adicionar widget")
        }
    }
}

// ---------- Seletor visual de widgets (FIX #1) ----------

@Composable
private fun WidgetPickerDialog(
    loadAvailableWidgets: suspend () -> List<WidgetProviderOption>,
    onDismiss: () -> Unit,
    onProviderChosen: (WidgetProviderOption, Int, Int) -> Unit
) {
    var options by remember { mutableStateOf<List<WidgetProviderOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var pendingProvider by remember { mutableStateOf<WidgetProviderOption?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        options = try {
            loadAvailableWidgets()
        } catch (_: Exception) {
            emptyList()
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Escolher widget",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }

                when {
                    isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    options.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhum widget disponível neste aparelho.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(items = options, key = { it.provider.flattenToString() }) { option ->
                            WidgetProviderRow(option = option, onClick = { pendingProvider = option })
                        }
                    }
                }
            }
        }
    }

    pendingProvider?.let { option ->
        SizeAdjustDialog(
            title = option.label,
            initialSpanX = option.defaultSpanX,
            initialSpanY = option.defaultSpanY,
            onConfirm = { spanX, spanY ->
                onProviderChosen(option, spanX, spanY)
                pendingProvider = null
            },
            onDismiss = { pendingProvider = null }
        )
    }
}

@Composable
private fun WidgetProviderRow(option: WidgetProviderOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(option.provider) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (option.preview != null) {
                Image(painter = BitmapPainter(option.preview), contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = "Sugerido: ${option.defaultSpanX}x${option.defaultSpanY} células",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Diálogo de ajuste de tamanho (novo widget ou redimensionar) ----------

private val sizePresets = listOf(1 to 1, 2 to 1, 2 to 2, 4 to 1, 4 to 2, 4 to 4)

@Composable
private fun SizeAdjustDialog(
    title: String,
    initialSpanX: Int,
    initialSpanY: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var spanX by remember { mutableStateOf(initialSpanX.coerceIn(1, WidgetPrefs.MAX_SPAN_X)) }
    var spanY by remember { mutableStateOf(initialSpanY.coerceIn(1, WidgetPrefs.MAX_SPAN_Y)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Tamanho: ${spanX}x${spanY} células",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizePresets.forEach { (x, y) ->
                        FilterChip(
                            selected = spanX == x && spanY == y,
                            onClick = { spanX = x; spanY = y },
                            label = { Text("${x}x${y}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(spanX, spanY) }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
