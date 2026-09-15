package com.umilauncher.kiss.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umilauncher.kiss.data.AppInfo
import com.umilauncher.kiss.data.IconCache

/**
 * Item de lista deliberadamente simples: Row + Text/Image, sem Card
 * (Card no M3 adiciona elevação/sombra -> shadow layer extra na GPU).
 * O ícone só é decodificado quando o item entra em composição, e reaproveita
 * o bitmap do cache se já existir (comum ao alternar drawer <-> favoritos).
 */
@Composable
fun AppListItem(
    app: AppInfo,
    iconCache: IconCache,
    showIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(app.key) { mutableStateOf(iconCache.getCached(app.key)) }

    if (showIcon) {
        LaunchedEffect(app.key) {
            if (bitmap == null) {
                bitmap = iconCache.loadIcon(app)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon) {
            AppIcon(bitmap)
            androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RowScope.AppIcon(bitmap: Bitmap?) {
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        // Placeholder vazio em vez de um Box colorido: evita alocar/():
        // desenhar algo só para "preencher espaço" enquanto o ícone real
        // carrega custa tempo de composição sem benefício visual real,
        // já que o carregamento é da ordem de poucos ms a partir do cache.
        androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
    }
}
