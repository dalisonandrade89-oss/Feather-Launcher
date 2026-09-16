package com.aviatelite.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.util.Log

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
}
