package com.umilauncher.kiss.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.umilauncher.kiss.LauncherUiState
import com.umilauncher.kiss.R
import com.umilauncher.kiss.data.AppInfo
import com.umilauncher.kiss.data.IconCache

/**
 * Gaveta de apps completa. Diferente da Home, aqui MOSTRAMOS ícones
 * (showIcon = true) porque essa tela é acessada sob demanda e fechada
 * logo depois - o custo de memória dos bitmaps é temporário e o
 * IconCache já limita e recicla automaticamente (ver IconCache.kt).
 *
 * Performance da lista em 60Hz:
 *  - LazyColumn só compõe os itens visíveis + um pequeno buffer.
 *  - key = app.key evita recomposição/relayout de itens que não mudaram
 *    de posição ao filtrar (Compose reaproveita o slot).
 *  - Sem animações de entrada/saída de item (AnimatedVisibility) na
 *    lista: são baratas isoladamente, mas em listas longas com scroll
 *    rápido em GPU fraca (PowerVR GE8320 no Helio G25) somam jank.
 */
@Composable
fun AppDrawerScreen(
    state: LauncherUiState,
    iconCache: IconCache,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors()
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(state.filteredApps, key = { it.key }) { app ->
                AppListItem(
                    app = app,
                    iconCache = iconCache,
                    showIcon = true,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }
    }
}
