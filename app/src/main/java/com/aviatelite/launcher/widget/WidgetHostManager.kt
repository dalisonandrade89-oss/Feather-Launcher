package com.aviatelite.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/**
 * Encapsula o ciclo de vida do AppWidgetHost, exigido para launchers
 * hospedarem AppWidgets nativos do sistema (mesmo mecanismo usado por
 * launchers como o AOSP Launcher3).
 *
 * IMPORTANTE: startListening()/stopListening() devem seguir o ciclo de
 * vida da Activity (onStart/onStop) para não vazar recursos do sistema
 * de widgets, e para não desenhar widgets quando o launcher não está visível.
 */
class WidgetHostManager(private val context: Context) {

    companion object {
        // Qualquer inteiro fixo e único para este launcher.
        private const val HOST_ID = 1024
        const val REQUEST_PICK_APPWIDGET = 9001
        const val REQUEST_CREATE_APPWIDGET = 9002
        const val REQUEST_BIND_APPWIDGET = 9003
    }

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    val appWidgetHost: AppWidgetHost = AppWidgetHost(context, HOST_ID)

    fun startListening() = appWidgetHost.startListening()

    fun stopListening() = appWidgetHost.stopListening()

    fun allocateAppWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun deleteAppWidgetId(appWidgetId: Int) = appWidgetHost.deleteAppWidgetId(appWidgetId)

    /** Intent para o usuário escolher, na lista do sistema, qual widget nativo adicionar. */
    fun createPickIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /** Intent de configuração, caso o provider do widget exija (ex.: widget de calendário). */
    fun createConfigureIntentIfNeeded(appWidgetId: Int): Intent? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        if (info.configure == null) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    /** Cria (ou recria) a view nativa do widget para ser embutida via AndroidView. */
    fun createHostView(appWidgetId: Int): AppWidgetHostView? {
        val info = getAppWidgetInfo(appWidgetId) ?: return null
        return appWidgetHost.createView(context, appWidgetId, info)
    }
}
