package com.feather.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Encapsula o ciclo de vida do AppWidgetHost, exigido para launchers
 * hospedarem AppWidgets nativos do sistema (mesmo mecanismo usado por
 * launchers como o AOSP Launcher3).
 *
 * IMPORTANTE: startListening()/stopListening() devem seguir o ciclo de
 * vida da Activity (onStart/onStop) para não vazar recursos do sistema
 * de widgets, e para não desenhar widgets quando o launcher não está visível.
 *
 * FIX (crash ao adicionar widget): toda chamada que atravessa para o
 * processo do sistema (AppWidgetHost/AppWidgetManager) agora é protegida
 * por try-catch. Provedores de widget podem ter sido desinstalados entre
 * a escolha do usuário e a criação da view, o processo do sistema de
 * widgets pode estar temporariamente indisponível, ou o dispositivo/ROM
 * pode não ter o host de widgets em um estado esperado — nenhum desses
 * cenários deve derrubar o app.
 */
class WidgetHostManager(private val context: Context) {

    companion object {
        // Qualquer inteiro fixo e único para este launcher.
        private const val HOST_ID = 1024
        private const val TAG = "WidgetHostManager"
    }

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)

    fun startListening() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao iniciar o AppWidgetHost", e)
        }
    }

    fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao parar o AppWidgetHost", e)
        }
    }

    fun allocateAppWidgetId(): Int = try {
        appWidgetHost.allocateAppWidgetId()
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao alocar appWidgetId", e)
        AppWidgetManager.INVALID_APPWIDGET_ID
    }

    fun deleteAppWidgetId(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (e: Exception) {
            // Widget já pode ter sido removido pelo sistema; seguro ignorar.
            Log.w(TAG, "Falha ao remover appWidgetId=$appWidgetId", e)
        }
    }

    /** Intent para o usuário escolher, na lista do sistema, qual widget nativo adicionar. */
    fun createPickIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /** Intent de configuração, caso o provider do widget exija (ex.: widget de calendário). */
    fun createConfigureIntentIfNeeded(appWidgetId: Int): Intent? {
        val info = getAppWidgetInfo(appWidgetId) ?: return null
        if (info.configure == null) return null
        return try {
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao montar intent de configuração para appWidgetId=$appWidgetId", e)
            null
        }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? = try {
        appWidgetManager.getAppWidgetInfo(appWidgetId)
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao obter AppWidgetProviderInfo para appWidgetId=$appWidgetId", e)
        null
    }

    /**
     * Cria (ou recria) a view nativa do widget para ser embutida via AndroidView.
     * Retorna null em qualquer falha (provider desinstalado, binder morto,
     * classe do provider ausente etc.) em vez de propagar a exceção — quem
     * chama (WidgetsPanelScreen) deve tratar o retorno nulo com uma UI de
     * fallback, nunca deixando a exceção subir até derrubar a Activity.
     */
    fun createHostView(appWidgetId: Int): AppWidgetHostView? {
        val info = getAppWidgetInfo(appWidgetId) ?: return null
        return try {
            appWidgetHost.createView(context, appWidgetId, info)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao criar AppWidgetHostView para appWidgetId=$appWidgetId", e)
            null
        }
    }

    /**
     * Verifica/solicita o vínculo (bind) do widget quando ele não foi
     * criado através do seletor padrão do sistema (ACTION_APPWIDGET_PICK
     * já vincula automaticamente). Útil como camada extra de segurança
     * caso, no futuro, o app monte um seletor de widgets próprio.
     *
     * Retorna true se já está (ou ficou) vinculado sem necessidade de
     * confirmação adicional do usuário; false se for necessário disparar
     * um Intent ACTION_APPWIDGET_BIND (o chamador deve tratar esse caso).
     */
    fun bindAppWidgetIdIfAllowed(
        appWidgetId: Int,
        providerComponentName: android.content.ComponentName
    ): Boolean = try {
        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerComponentName)
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao verificar bind automático do widget", e)
        false
    }

    fun createBindIntent(
        appWidgetId: Int,
        providerComponentName: android.content.ComponentName
    ): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerComponentName)
        }
    }

    /**
     * Lista todos os AppWidgets nativos instalados no aparelho (de
     * qualquer app), já convertidos para [WidgetProviderOption] com
     * preview/ícone pequeno e o tamanho sugerido em células — usado pelo
     * seletor visual da tela de Widgets. Chamar fora da UI thread (é
     * potencialmente custoso: percorre TODOS os providers do sistema).
     */
    fun loadAvailableWidgets(cellSizeDp: Int): List<WidgetProviderOption> {
        val density = context.resources.displayMetrics.density
        val previewSizePx = (56 * density).toInt().coerceAtLeast(1)
        val pm = context.packageManager

        val providers = try {
            appWidgetManager.installedProviders
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao listar widgets instalados", e)
            emptyList()
        }

        return providers.mapNotNull { info ->
            try {
                val label = info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val preview = loadProviderPreview(info, previewSizePx)
                // info.minWidth/minHeight já vêm em dp (não em pixels), então
                // dividimos diretamente por cellSizeDp (também em dp).
                val spanX = ((info.minWidth + cellSizeDp - 1) / cellSizeDp).coerceIn(1, WidgetPrefs.MAX_SPAN_X)
                val spanY = ((info.minHeight + cellSizeDp - 1) / cellSizeDp).coerceIn(1, WidgetPrefs.MAX_SPAN_Y)
                WidgetProviderOption(
                    provider = info.provider,
                    label = label,
                    preview = preview,
                    defaultSpanX = spanX,
                    defaultSpanY = spanY
                )
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao carregar provider de widget", e)
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    private fun loadProviderPreview(
        info: AppWidgetProviderInfo,
        sizePx: Int
    ): androidx.compose.ui.graphics.ImageBitmap? {
        val drawable = try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
            } else {
                info.loadIcon(context, 0)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            val bitmap = androidx.core.graphics.createBitmap(sizePx, sizePx)
            val canvas = android.graphics.Canvas(bitmap)
            // Preserva a proporção original do preview em vez de esticar,
            // já que previews de widgets variam bastante de formato (ex.: 4x1).
            val intrinsicW = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val intrinsicH = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val scale = minOf(sizePx.toFloat() / intrinsicW, sizePx.toFloat() / intrinsicH)
            val drawW = (intrinsicW * scale).toInt().coerceAtLeast(1)
            val drawH = (intrinsicH * scale).toInt().coerceAtLeast(1)
            val left = (sizePx - drawW) / 2
            val top = (sizePx - drawH) / 2
            drawable.setBounds(left, top, left + drawW, top + drawH)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao renderizar preview do widget", e)
            null
        }
    }
}

/** Opção exibida no seletor visual de widgets (feature #1). */
data class WidgetProviderOption(
    val provider: android.content.ComponentName,
    val label: String,
    val preview: androidx.compose.ui.graphics.ImageBitmap?,
    val defaultSpanX: Int,
    val defaultSpanY: Int
)
