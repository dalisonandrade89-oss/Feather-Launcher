package com.feather.launcher.ui.screens

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.feather.launcher.widget.WidgetPrefs
import com.feather.launcher.widget.WidgetPlacement
import com.feather.launcher.widget.WidgetProviderOption
import kotlinx.coroutines.withTimeoutOrNull

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

    // FIX #15 (revertido na v1.4.4): NestedScrollInteropConnection foi
    // adicionada aqui para tentar deixar listas nativas dentro de
    // widgets (ex.: a agenda do Google Calendar) rolarem sozinhas.
    // Testado em aparelho real: não resolveu o scroll da agenda E ainda
    // corria o risco de interferir no toque-e-segure da faixa de ações
    // (ver detectLongPress). Sem benefício confirmado e com risco real,
    // foi removida — ver CHANGELOG v1.4.4.
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
                        // FIX #17: sem key() aqui, o Compose identifica cada
                        // WidgetCard pela POSIÇÃO na árvore, não pelo widget
                        // que ele representa (isso só existe automaticamente
                        // em listas lazy com key). Ao remover o widget A, o
                        // slot dele passava a receber o placement de B, mas
                        // o AndroidView (cuja factory só roda uma vez por
                        // slot) continuava mostrando a view de A — resultado:
                        // o botão de remover/redimensionar acabava agindo no
                        // widget errado. key(placement.appWidgetId) resolve
                        // isso, fazendo o Compose descartar e recriar o nó
                        // certo quando a lista muda.
                        key(placement.appWidgetId) {
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
}

// ---------- Card de um widget já adicionado ----------

/** Altura da faixa de "pegada" no topo do widget — ver [WidgetHandle]. */
private val WIDGET_HANDLE_HEIGHT = 22.dp

/**
 * Tempo de toque e segure para abrir o diálogo de ações — um pouco
 * acima do padrão do sistema (~500ms), como margem extra contra toques
 * um pouco mais demorados sem soltar por engano.
 */
private const val WIDGET_HANDLE_LONG_PRESS_MS = 600L

/**
 * FIX #6: sem mais barra de "Tamanho"/X acima do widget — ela roubava
 * altura útil da grade. Agora existe uma faixa fina só de "pegada" no
 * topo do card ([WidgetHandle]); toque e segure NELA (não no widget
 * inteiro) abre o diálogo com "Alterar tamanho" e "Remover".
 *
 * FIX #13: a versão anterior detectava o toque longo sobre o card
 * inteiro, inclusive por cima do próprio widget — então qualquer toque
 * um pouco mais demorado num botão do widget (ex.: "bater ponto" de um
 * app de controle de horário, que processa a ação antes do dedo soltar)
 * também disparava o diálogo por engano. Isolar a área de toque numa
 * faixa dedicada, sem nenhum conteúdo do widget por baixo, resolve isso
 * de vez — e também simplifica a detecção do gesto, que antes precisava
 * "espiar" a fase Initial do pointer input só para não atrapalhar
 * cliques no widget (não é mais necessário: a faixa não tem nada por
 * baixo para atrapalhar).
 */
@Composable
private fun WidgetCard(
    placement: WidgetPlacement,
    createHostView: (Int) -> AppWidgetHostView?,
    onRemove: () -> Unit,
    onResize: (Int, Int) -> Unit
) {
    var failedToLoad by remember(placement.appWidgetId) { mutableStateOf(false) }
    var showActionsDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        WidgetHandle(onLongPress = { showActionsDialog = true })

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

    if (showActionsDialog) {
        WidgetActionsDialog(
            onDismiss = { showActionsDialog = false },
            onResizeClick = {
                showActionsDialog = false
                showResizeDialog = true
            },
            onRemoveClick = {
                showActionsDialog = false
                onRemove()
            }
        )
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

/**
 * Faixa fina no topo do card. Só o cantinho esquerdo (não a largura
 * toda) responde a toque e segure — o resto da faixa (e do card) fica
 * livre para o gesto de arrastar entre páginas (Widgets ↔ Home) passar
 * por cima sem risco de ser confundido com um toque e segure.
 *
 * FIX #14: a versão anterior usava a faixa inteira como área sensível.
 * Isso resolveu o problema de cliques no próprio widget (FIX #13), mas
 * criou um novo: ao arrastar da aba de Widgets para a Home, o dedo
 * desliza horizontalmente bem em cima dessa faixa — e se o toque
 * começasse ali, o HorizontalPager e o detector de toque longo
 * disputavam o mesmo gesto, às vezes abrindo o diálogo no meio da
 * transição. Restringir a área a um cantinho fixo reduz drasticamente
 * a chance de o gesto de arrastar começar bem ali — ainda pode
 * acontecer se o arrasto começar bem dentro do cantinho, mas é um alvo
 * bem menor. Uma tentativa de fechar esse último caso residual
 * (cancelar o long-press por deslocamento do dedo, ver FIX #16 em
 * [detectLongPress]) quebrou o toque-e-segure por completo em teste
 * real e foi revertida na v1.4.4 — o cantinho pequeno sozinho é a
 * proteção que temos por enquanto.
 */
@Composable
private fun WidgetHandle(onLongPress: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(WIDGET_HANDLE_HEIGHT),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectLongPress(durationMs = WIDGET_HANDLE_LONG_PRESS_MS, onLongPress = onLongPress)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * Toque e segure com duração customizável.
 *
 * FIX #16 (revertido na v1.4.4): tentei adicionar aqui um
 * cancelamento manual por deslocamento do dedo (checando
 * `touchSlop`), para o diálogo parar de abrir se o swipe entre
 * páginas começasse bem em cima da faixa. Essa versão, testada em
 * aparelho real, quebrou o toque-e-segure por completo (o diálogo
 * simplesmente parou de abrir). Como o cantinho de 40dp (FIX #14) já
 * reduz bastante a chance de o swipe começar exatamente ali, voltamos
 * para esta versão mais simples — que é a mesma lógica interna do
 * `waitForUpOrCancellation` que o próprio `detectTapGestures` do
 * Compose usa, só que com duração customizável (ver
 * [WIDGET_HANDLE_LONG_PRESS_MS]).
 */
private suspend fun PointerInputScope.detectLongPress(durationMs: Long, onLongPress: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown()
        val longPressConfirmed = withTimeoutOrNull(durationMs) {
            waitForUpOrCancellation()
        } == null
        if (longPressConfirmed) {
            onLongPress()
            waitForUpOrCancellation()
        }
    }
}

/** Diálogo compacto com as duas ações de um widget já adicionado. */
@Composable
private fun WidgetActionsDialog(
    onDismiss: () -> Unit,
    onResizeClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            WidgetActionRow(label = "Alterar tamanho", onClick = onResizeClick)
            WidgetActionRow(
                label = "Remover",
                onClick = onRemoveClick,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun WidgetActionRow(label: String, onClick: () -> Unit, color: Color = Color.Unspecified) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    )
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
                    else -> {
                        // FIX #7: agrupa por app (a lista já vem ordenada
                        // assim do WidgetHostManager) e insere um
                        // cabeçalho com o nome do app antes de cada grupo,
                        // deixando explícito visualmente que os widgets
                        // daquele app estão todos juntos em seguida.
                        val groupedByApp = remember(options) {
                            options.groupBy { it.appLabel }.toList()
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            groupedByApp.forEach { (appLabel, widgetsOfApp) ->
                                item(key = "header_$appLabel") {
                                    WidgetAppHeader(appLabel)
                                }
                                items(
                                    items = widgetsOfApp,
                                    key = { it.provider.flattenToString() }
                                ) { option ->
                                    WidgetProviderRow(option = option, onClick = { pendingProvider = option })
                                }
                            }
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
private fun WidgetAppHeader(appLabel: String) {
    Text(
        text = appLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * FIX #7: o resumo (2ª linha pequena) diferencia widgets do mesmo app
 * que, sem isso, ficariam praticamente idênticos na lista (mesmo app,
 * mesmo preview parecido, nomes genéricos como "Widget grande"). Usa a
 * descrição declarada pelo próprio provider quando disponível (Android
 * 12+); caso contrário, cai no tamanho sugerido em células, que também
 * já diferencia (ex.: um widget 4x1 do outro 2x2 do mesmo app).
 */
@Composable
private fun WidgetProviderRow(option: WidgetProviderOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                Image(bitmap = option.preview, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            val summary = option.description
                ?: "Sugerido: ${option.defaultSpanX}x${option.defaultSpanY} células"
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
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
