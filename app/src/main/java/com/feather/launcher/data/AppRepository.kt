package com.feather.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Responsável por listar os apps instalados e converter seus ícones
 * para bitmaps pequenos (48dp), tudo fora da thread principal.
 *
 * Em um dispositivo de 4GB de RAM com muitos apps instalados, evitar
 * segurar Drawables "cheios" (algumas launcher icons chegam a 512x512px)
 * é essencial: aqui cada ícone ocupa no máximo 48x48px em memória.
 */
class AppRepository(private val context: Context) {

    private val iconSizePx: Int by lazy {
        val density = context.resources.displayMetrics.density
        (48 * density).toInt().coerceAtLeast(1)
    }

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        @Suppress("DEPRECATION")
        val resolvedActivities: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)

        resolvedActivities
            .asSequence()
            .mapNotNull { resolveInfo -> toAppInfoOrNull(pm, resolveInfo) }
            .distinctBy { it.packageName + it.activityName }
            .sortedBy { it.searchableLabel }
            .toList()
    }

    private fun toAppInfoOrNull(pm: PackageManager, resolveInfo: ResolveInfo): AppInfo? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val label = resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty()
        if (label.isEmpty()) return null

        val drawable: Drawable = try {
            resolveInfo.loadIcon(pm)
        } catch (_: Throwable) {
            return null
        }

        val bitmap = drawableToSmallBitmap(drawable, iconSizePx)

        return AppInfo(
            label = label,
            packageName = activityInfo.packageName,
            activityName = activityInfo.name,
            icon = bitmap?.asImageBitmap(),
            searchableLabel = label.toSearchNormalized()
        )
    }

    /** Desenha o Drawable diretamente em um bitmap pequeno, sem alocar buffers grandes. */
    private fun drawableToSmallBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
        return try {
            val bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }
}
