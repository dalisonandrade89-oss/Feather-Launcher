package com.feather.launcher.ui.screens

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.feather.launcher.widget.WidgetPrefs
import com.feather.launcher.widget.WidgetPlacement
import com.feather.launcher.widget.WidgetProviderOption

/**
 * Painel de widgets: grade redimensionável (FIX #1). Cada widget ocupa
 * `spanX` colunas (de 1 a 4) e uma altura proporcional a `spanY` (1 a 4
 * "linhas" de referência) — assim o usuário controla o tamanho sem
 * distorcer o conteúdo do widget.
 *
 * FIX #2 (fluidez): a grade NÃO usa LazyVerticalGrid. Um widget nativo
 * (AppWidgetHostView, via AndroidView) é uma interop cara — quando um
 * item lazy sai da viewport e volta, o Compose descarta e recria o nó,
 * o que force o AppWidgetHost a reinflar a RemoteViews do zero a cada
 * rolagem. Como o número de widgets num launcher é sempre pequeno
 * (dezenas, não centenas, como a lista de apps), o custo de manter
 * todos compostos o tempo todo é irrelevante perto do custo de recriar
 * hosts de widget repetidamente — por isso aqui usamos uma Column
 * rolável comum, empacotando os widgets em linhas manualmente.
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
            WidgetsGrid(
                placements = placements,
                createHostView = createHostView,
                onRemoveWidget = onRemoveWidget,
                onResizeWidget = onResizeWidget,
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 96.dp
                )
            )
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

// ---------- Grade não-lazy (FIX #2) ----------

/** Empacota os widgets em linhas, preenchendo cada linha até [columns] colunas (auto-flow simples). */
private fun packIntoRows(placements: List<WidgetPlacement>, columns: Int): List<List<WidgetPlacement>> {
    val rows = mutableListOf<MutableList<WidgetPlacement>>()
    var currentRow = mutableListOf<WidgetPlacement>()
    var currentWidth = 0
    for (placement in placements) {
        val span = placement.spanX.coerceIn(1, columns)
        if (currentWidth + span > columns && currentRow.isNotEmpty()) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentWidth = 0
        }
        currentRow.add(placement)
        currentWidth += span
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)
    return rows
}

@Composable
private fun WidgetsGrid(
    placements: List<WidgetPlacement>,
    createHostView: (Int) -> AppWidgetHostView?,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    val spacing = 8.dp
    val rows = remember(placements) { packIntoRows(placements, WidgetPrefs.GRID_COLUMNS) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        val totalSpacing = spacing * (WidgetPrefs.GRID_COLUMNS - 1)
        val cellWidth = (maxWidth - totalSpacing) / WidgetPrefs.GRID_COLUMNS

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { placement ->
                        val itemWidth = cellWidth * placement.spanX + spacing * (placement.spanX - 1)
                        Box(modifier = Modifier.width(itemWidth)) {
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
        }
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
        // Alça de ações: toque longo continua removendo (atalho extra),
        // mas o rótulo de texto foi removido — o botão de fechar já deixa
        // a ação explícita, sem precisar repetir em palavras.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .pointerInput(placement.appWidgetId) {
                    detectTapGestures(onLongPress = { onRemove() })
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
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
