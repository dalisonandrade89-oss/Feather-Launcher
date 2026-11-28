package com.feather.launcher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feather.launcher.BuildConfig
import com.feather.launcher.R
import com.feather.launcher.data.AppDrawerViewMode
import com.feather.launcher.data.AppInfo
import com.feather.launcher.data.SpaceDef
import com.feather.launcher.data.toSearchNormalized
import com.feather.launcher.pwa.PwaBrowser
import com.feather.launcher.pwa.PwaShortcut
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 * Gaveta de apps: campo de busca fixo (fora da lista, então nunca rola
 * junto) e a lista completa dos apps instalados, alfabética, com key
 * estável por app para manter o scroll fluido a 60Hz mesmo com
 * centenas de itens.
 *
 * FIX #3 (histórico): toque longo num app abre o diálogo de vínculo por
 * aba (Spaces), implementando a regra de exclusividade.
 *
 * Visualização em lista ou grade (alternada pelo ícone dentro do campo
 * de busca, ver [SearchField]): a preferência é lembrada entre sessões
 * via [AppDrawerViewMode] em AppPrefs. Ambas as visualizações continuam
 * lazy (LazyColumn / LazyVerticalGrid) — diferente do painel de widgets,
 * a gaveta pode ter centenas de itens, então manter a lazy-ness aqui é
 * o que garante scroll leve.
 */
@Composable
fun AppDrawerScreen(
    apps: PersistentList<AppInfo>,
    pwaShortcuts: PersistentList<PwaShortcut>,
    onAddPwaShortcut: (String, String, PwaBrowser) -> Unit,
    onOpenBrowserToInstallPwa: (String, PwaBrowser) -> Unit,
    onRemovePwaShortcut: (String) -> Unit,
    onLaunchPwaShortcut: (PwaShortcut) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    viewMode: AppDrawerViewMode,
    onToggleViewMode: () -> Unit,
    spaces: PersistentList<SpaceDef>,
    assignments: PersistentMap<String, PersistentSet<String>>,
    onToggleAppInSpace: (String, AppInfo) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var appForSpaceDialog by remember { mutableStateOf<AppInfo?>(null) }
    var showAddPwaDialog by remember { mutableStateOf(false) }
    var pwaForDeleteDialog by remember { mutableStateOf<PwaShortcut?>(null) }

    val visiblePwas = remember(pwaShortcuts, searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) {
            pwaShortcuts
        } else {
            val norm = trimmed.toSearchNormalized()
            pwaShortcuts.filter {
                it.searchableName.contains(norm) ||
                    it.url.lowercase().contains(trimmed.lowercase()) ||
                    it.browser.label.lowercase().contains(trimmed.lowercase())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            viewMode = viewMode,
            onToggleViewMode = onToggleViewMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        PwaShelf(
            shortcuts = visiblePwas,
            onAddClick = { showAddPwaDialog = true },
            onPwaClick = onLaunchPwaShortcut,
            onPwaLongClick = { pwaForDeleteDialog = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (apps.isEmpty() && visiblePwas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhum app encontrado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else when (viewMode) {
            AppDrawerViewMode.LIST -> AppListView(
                apps = apps,
                onAppClick = onAppClick,
                onAppLongClick = { appForSpaceDialog = it }
            )

            AppDrawerViewMode.GRID -> AppGridView(
                apps = apps,
                onAppClick = onAppClick,
                onAppLongClick = { appForSpaceDialog = it }
            )
        }
    }

    if (showAddPwaDialog) {
        AddPwaShortcutDialog(
            onConfirm = { name, url, browser ->
                onAddPwaShortcut(name, url, browser)
                showAddPwaDialog = false
            },
            onOpenInBrowserToInstall = { rawUrl, browser ->
                val normalized = com.feather.launcher.pwa.PwaPrefs.normalizeUrl(rawUrl)
                if (normalized.isNotEmpty()) {
                    onOpenBrowserToInstallPwa(normalized, browser)
                    showAddPwaDialog = false
                }
            },
            onDismiss = { showAddPwaDialog = false }
        )
    }

    pwaForDeleteDialog?.let { pwa ->
        AlertDialog(
            onDismissRequest = { pwaForDeleteDialog = null },
            title = { Text("Remover atalho \"${pwa.name}\"?") },
            text = { Text("Navegador: ${pwa.browser.label} · ${pwa.url}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemovePwaShortcut(pwa.id)
                        pwaForDeleteDialog = null
                    }
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { pwaForDeleteDialog = null }) { Text("Cancelar") }
            }
        )
    }

    appForSpaceDialog?.let { app ->
        SpaceAssignmentDialog(
            app = app,
            spaces = spaces,
            assignments = assignments,
            onToggle = { spaceId -> onToggleAppInSpace(spaceId, app) },
            onOpenAppInfo = {
                appForSpaceDialog = null
                onOpenAppInfo(app)
            },
            onDismiss = { appForSpaceDialog = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PwaShelf(
    shortcuts: List<PwaShortcut>,
    onAddClick: () -> Unit,
    onPwaClick: (PwaShortcut) -> Unit,
    onPwaLongClick: (PwaShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PWAs (Brave · Vivaldi)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable(onClick = onAddClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add PWA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (shortcuts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = shortcuts, key = { it.id }) { pwa ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .combinedClickable(
                                onClick = { onPwaClick(pwa) },
                                onLongClick = { onPwaLongClick(pwa) }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = pwa.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = pwa.browser.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPwaShortcutDialog(
    onConfirm: (name: String, url: String, browser: PwaBrowser) -> Unit,
    onOpenInBrowserToInstall: (rawUrl: String, browser: PwaBrowser) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedBrowser by remember { mutableStateOf(PwaBrowser.BRAVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PWA via Brave ou Vivaldi") },
        text = {
            Column {
                Text(
                    text = "Para rodar como app real (sem barra de URL ou botões do navegador), abra a página no Brave ou Vivaldi e use \"Instalar página como app\" no menu do navegador.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do app") },
                    placeholder = { Text("Ex.: YouTube Music") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL do site / PWA") },
                    placeholder = { Text("music.youtube.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Navegador",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PwaBrowser.preferredBrowsers.forEach { browser ->
                        FilterChip(
                            selected = browser == selectedBrowser,
                            onClick = { selectedBrowser = browser },
                            label = { Text(browser.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                if (url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onOpenInBrowserToInstall(url, selectedBrowser) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir no ${selectedBrowser.label} para instalar como app")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(name, url, selectedBrowser)
                    }
                }
            ) { Text("Salvar atalho") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ---------- Visualização em lista ----------

@Composable
private fun AppListView(
    apps: PersistentList<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(items = apps, key = { app -> app.key }) { app ->
            AppRow(
                app = app,
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            )
        }

        item(key = "version_footer") {
            VersionFooter()
        }
    }
}

// ---------- Visualização em grade ----------

@Composable
private fun AppGridView(
    apps: PersistentList<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 76.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        items(items = apps, key = { app -> app.key }) { app ->
            AppGridTile(
                app = app,
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            )
        }

        item(key = "version_footer", span = { GridItemSpan(maxLineSpan) }) {
            VersionFooter()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridTile(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(width = 76.dp, height = 92.dp)
            // FIX #21 (item #10 da auditoria): combinedClickable troca o
            // pointerInput(app.key) + detectTapGestures cru por duas
            // razões: (1) o pointerInput só reinicia sua coroutine quando
            // a KEY muda — como app.key não muda entre recomposições,
            // qualquer estado capturado ficava "congelado" na primeira
            // composição; combinedClickable lê onClick/onLongClick
            // sempre atualizados, a cada recomposição, sem precisar de
            // key nenhuma. (2) detectTapGestures não gera nenhuma
            // semântica de acessibilidade — o TalkBack não conseguia
            // nem "ver" que isto era clicável, muito menos abrir o app.
            // combinedClickable expõe a ação de clique/toque longo
            // corretamente para leitores de tela.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
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

@Composable
private fun SpaceAssignmentDialog(
    app: AppInfo,
    spaces: PersistentList<SpaceDef>,
    assignments: PersistentMap<String, PersistentSet<String>>,
    onToggle: (String) -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exibir \"${app.label}\" em:") },
        text = {
            Column {
                spaces.forEach { space ->
                    val checked = assignments[space.id]?.contains(app.packageName) == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(space.id) })
                        Text(space.name, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TextButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Dados do app",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Concluído") }
        }
    )
}

/**
 * Campo de busca com dois ícones à direita:
 * - "Limpar" (X), só quando há texto digitado;
 * - alternador de visualização, sempre visível. O ÍCONE É DINÂMICO: ele
 *   sempre mostra o modo PARA O QUAL o toque vai mudar (não o atual) —
 *   ou seja, em lista aparece o ícone de grade (convite a mudar pra
 *   grade) e em grade aparece o de lista. É o mesmo padrão usado por
 *   apps como Google Fotos/Play Store, intuitivo por já ser familiar.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    viewMode: AppDrawerViewMode,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Buscar apps...") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpar busca")
                    }
                }
                ViewModeToggleButton(viewMode = viewMode, onToggle = onToggleViewMode)
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ViewModeToggleButton(viewMode: AppDrawerViewMode, onToggle: () -> Unit) {
    val (icon, description) = when (viewMode) {
        // Em lista: o ícone mostrado é o de grade (é para lá que o toque leva).
        AppDrawerViewMode.LIST -> R.drawable.ic_view_grid to "Ver em grade"
        // Em grade: o ícone mostrado é o de lista.
        AppDrawerViewMode.GRID -> R.drawable.ic_view_list to "Ver em lista"
    }
    IconButton(onClick = onToggle) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VersionFooter() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "Feather Launcher v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            )
        }

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
