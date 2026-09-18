package com.feather.launcher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feather.launcher.data.AppInfo
import com.feather.launcher.data.SpaceDef
import com.feather.launcher.data.ThemeMode
import com.feather.launcher.notification.LastNotificationInfo
import com.feather.launcher.ui.components.ColorPickerDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    spaces: List<SpaceDef>,
    currentSpaceId: String,
    onSpaceSelected: (String) -> Unit,
    onAddSpace: (String) -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onDeleteSpace: (String) -> Unit,
    spaceApps: List<AppInfo>,
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
    onAppLongClick: (AppInfo) -> Unit,
    onRecentAppLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAppearancePanel by remember { mutableStateOf(false) }
    var spaceDialog by remember { mutableStateOf<SpaceDialogState>(SpaceDialogState.None) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // FIX #5: botão de Aparência movido para o topo (TopAppBar),
            // aproveitando a área de status bar/topo sem "roubar" espaço
            // do conteúdo abaixo do relógio.
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { showAppearancePanel = !showAppearancePanel }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Personalizar aparência",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopClockPanel()

            if (!notificationAccessGranted) {
                NotificationAccessBanner(onClick = onRequestNotificationAccess)
            }

            // FIX #4 (fluidez): sem AnimatedVisibility aqui de propósito.
            // Cada fade cria uma camada de composição extra (graphicsLayer)
            // para o blending de alpha — na GPU PowerVR GE8320 (fill-rate
            // baixo), isso é overdraw evitável para um elemento que já
            // aparece/some rápido o suficiente sem precisar de transição.
            lastNotification?.let {
                NotificationSummaryRow(info = it, onClick = { onNotificationClick(it) })
            }

            if (showAppearancePanel) {
                AppearancePanel(
                    currentTheme = themeMode,
                    onThemeSelected = onThemeModeChange,
                    accentColor = accentColor,
                    onAccentColorChange = onAccentColorChange
                )
            }

            SpaceTabsRow(
                spaces = spaces,
                currentSpaceId = currentSpaceId,
                onSpaceSelected = onSpaceSelected,
                onAddClick = { spaceDialog = SpaceDialogState.Add },
                onSpaceLongClick = { space -> spaceDialog = SpaceDialogState.Actions(space) },
                modifier = Modifier.padding(top = 8.dp)
            )

            if (recentApps.isNotEmpty()) {
                RecentAppsRow(
                    apps = recentApps,
                    onAppClick = onAppClick,
                    onAppLongClick = onRecentAppLongClick
                )
            }

            if (spaceApps.isEmpty()) {
                EmptySpaceHint()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 76.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    items(items = spaceApps, key = { it.key }) { app ->
                        AppTile(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }
    }

    SpaceDialogs(
        state = spaceDialog,
        onDismiss = { spaceDialog = SpaceDialogState.None },
        onAdd = { name -> onAddSpace(name); spaceDialog = SpaceDialogState.None },
        onRename = { id, name -> onRenameSpace(id, name); spaceDialog = SpaceDialogState.None },
        onDelete = { id -> onDeleteSpace(id); spaceDialog = SpaceDialogState.None },
        onRequestRename = { space -> spaceDialog = SpaceDialogState.Rename(space) },
        onRequestDelete = { space -> spaceDialog = SpaceDialogState.DeleteConfirm(space) }
    )
}

// ---------- Diálogos de gerenciamento de Spaces ----------

private sealed class SpaceDialogState {
    data object None : SpaceDialogState()
    data object Add : SpaceDialogState()
    data class Actions(val space: SpaceDef) : SpaceDialogState()
    data class Rename(val space: SpaceDef) : SpaceDialogState()
    data class DeleteConfirm(val space: SpaceDef) : SpaceDialogState()
}

@Composable
private fun SpaceDialogs(
    state: SpaceDialogState,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRequestRename: (SpaceDef) -> Unit,
    onRequestDelete: (SpaceDef) -> Unit
) {
    when (state) {
        is SpaceDialogState.None -> Unit

        is SpaceDialogState.Add -> {
            TextInputDialog(
                title = "Nova aba",
                placeholder = "Ex.: Bancos, Redes Sociais...",
                confirmLabel = "Criar",
                onConfirm = onAdd,
                onDismiss = onDismiss
            )
        }

        is SpaceDialogState.Actions -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(state.space.name) },
                text = {
                    Column {
                        TextButton(onClick = { onRequestRename(state.space) }) { Text("Renomear") }
                        if (state.space.isCustom) {
                            TextButton(onClick = { onRequestDelete(state.space) }) { Text("Excluir") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
            )
        }

        is SpaceDialogState.Rename -> {
            TextInputDialog(
                title = "Renomear aba",
                placeholder = state.space.name,
                initialValue = state.space.name,
                confirmLabel = "Salvar",
                onConfirm = { newName -> onRename(state.space.id, newName) },
                onDismiss = onDismiss
            )
        }

        is SpaceDialogState.DeleteConfirm -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Excluir \"${state.space.name}\"?") },
                text = { Text("Os apps vinculados a esta aba deixarão de aparecer aqui. Essa ação não pode ser desfeita.") },
                confirmButton = {
                    TextButton(onClick = { onDelete(state.space.id) }) { Text("Excluir") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ---------- Linha de abas (Spaces) ----------

@Composable
private fun SpaceTabsRow(
    spaces: List<SpaceDef>,
    currentSpaceId: String,
    onSpaceSelected: (String) -> Unit,
    onAddClick: () -> Unit,
    onSpaceLongClick: (SpaceDef) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = spaces, key = { it.id }) { space ->
            val isSelected = space.id == currentSpaceId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .pointerInput(space.id) {
                        detectTapGestures(
                            onTap = { onSpaceSelected(space.id) },
                            onLongPress = { onSpaceLongClick(space) }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "add_space") {
            IconButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Nova aba")
            }
        }
    }
}

@Composable
private fun EmptySpaceHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nenhum app nesta aba ainda.\nNa Gaveta, toque e segure um app para vinculá-lo aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

/**
 * Relógio atualizado por minuto (não por segundo) para minimizar
 * recomposições e uso de CPU.
 */
@Composable
private fun TopClockPanel() {
    var now by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            val secondsToNextMinute = 60 - now.get(Calendar.SECOND)
            delay(secondsToNextMinute * 1000L)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeFormat.format(now.time),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = dateFormat.format(now.time)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Banner discreto lembrando que o acesso a notificações precisa ser
 * concedido manualmente pelo usuário (nenhum app pode ganhar essa
 * permissão automaticamente, por design do próprio Android).
 */
@Composable
private fun NotificationAccessBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Toque para ativar o resumo de notificações na Home",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * FIX #4: resumo discreto da última notificação, abaixo do relógio.
 * Some imediatamente (sem fade, ver FIX #4 no chamador) assim que a
 * notificação é lida/descartada no sistema, ou quando tocada aqui.
 */
@Composable
private fun NotificationSummaryRow(info: LastNotificationInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (info.appIcon != null) {
            Image(
                painter = BitmapPainter(info.appIcon),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Box(modifier = Modifier.padding(start = 6.dp))
        }
        val summary = listOf(info.appLabel, info.title, info.text)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- Painel de aparência (tema + cor de destaque) ----------

@Composable
private fun AppearancePanel(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    accentColor: Color?,
    onAccentColorChange: (Color?) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = "Aparência",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == currentTheme,
                    onClick = { onThemeSelected(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = accentColor ?: MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clickable { showColorPicker = true }
            )
            TextButton(onClick = { showColorPicker = true }) {
                Text("Cor de destaque personalizada")
            }
            if (accentColor != null) {
                TextButton(onClick = { onAccentColorChange(null) }) {
                    Text("Redefinir")
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = accentColor ?: MaterialTheme.colorScheme.primary,
            onConfirm = { color ->
                onAccentColorChange(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

// ---------- Usados recentemente ----------

@Composable
private fun RecentAppsRow(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = "Usados recentemente",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = apps, key = { "recent_" + it.key }) { app ->
                AppTile(
                    app = app,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}

/**
 * Toque abre o app; toque longo remove o app do Space atual (a
 * vinculação a outros Spaces é feita pela Gaveta, com o menu completo).
 */
@Composable
private fun AppTile(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = 72.dp, height = 88.dp)
            .background(Color.Transparent)
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (app.icon != null) {
            Image(
                painter = BitmapPainter(app.icon),
                contentDescription = app.label,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            )
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
