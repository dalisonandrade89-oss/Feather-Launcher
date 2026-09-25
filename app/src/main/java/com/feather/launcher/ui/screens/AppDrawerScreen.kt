package com.feather.launcher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
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
    apps: List<AppInfo>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    viewMode: AppDrawerViewMode,
    onToggleViewMode: () -> Unit,
    spaces: List<SpaceDef>,
    assignments: Map<String, Set<String>>,
    onToggleAppInSpace: (String, AppInfo) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var appForSpaceDialog by remember { mutableStateOf<AppInfo?>(null) }

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

        if (apps.isEmpty()) {
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

    appForSpaceDialog?.let { app ->
        SpaceAssignmentDialog(
            app = app,
            spaces = spaces,
            assignments = assignments,
            onToggle = { spaceId -> onToggleAppInSpace(spaceId, app) },
            onDismiss = { appForSpaceDialog = null }
        )
    }
}

// ---------- Visualização em lista ----------

@Composable
private fun AppListView(
    apps: List<AppInfo>,
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
    apps: List<AppInfo>,
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

@Composable
private fun AppGridTile(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(width = 76.dp, height = 92.dp)
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
    spaces: List<SpaceDef>,
    assignments: Map<String, Set<String>>,
    onToggle: (String) -> Unit,
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

@Composable
private fun AppRow(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                painter = BitmapPainter(app.icon),
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
