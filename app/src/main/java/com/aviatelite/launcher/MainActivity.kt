package com.aviatelite.launcher

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import com.aviatelite.launcher.data.AppInfo
import com.aviatelite.launcher.ui.LauncherApp
import com.aviatelite.launcher.ui.theme.AviateLiteTheme
import com.aviatelite.launcher.viewmodel.LauncherViewModel
import com.aviatelite.launcher.widget.WidgetHostManager
import com.aviatelite.launcher.widget.WidgetPrefs
import com.aviatelite.launcher.widget.WidgetProviderOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private lateinit var widgetHostManager: WidgetHostManager

    /** Estado lido pela UI Compose; atualizado também fora da composição (onResume). */
    private var notificationAccessGranted by mutableStateOf(false)

    /** ID + tamanho do widget em processo de criação (alocar -> bind -> configurar opcional -> salvar). */
    private var pendingAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingSpanX: Int = WidgetPrefs.DEFAULT_SPAN_X
    private var pendingSpanY: Int = WidgetPrefs.DEFAULT_SPAN_Y

    // Fluxo: ACTION_APPWIDGET_BIND, disparado quando bindAppWidgetIdIfAllowed retorna false
    // (o provider exige confirmação explícita do usuário para o vínculo).
    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = pendingAppWidgetId
        if (result.resultCode == RESULT_OK && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            proceedAfterBind(appWidgetId)
        } else {
            cancelPendingWidget(appWidgetId)
        }
    }

    // Fluxo opcional: configuração exigida por alguns widgets (ex.: calendário).
    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = pendingAppWidgetId
        if (result.resultCode == RESULT_OK && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            finalizeWidget(appWidgetId)
        } else {
            cancelPendingWidget(appWidgetId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetHostManager = WidgetHostManager(applicationContext)
        notificationAccessGranted = isNotificationAccessGranted()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()

            AviateLiteTheme(themeMode = themeMode, accentColor = accentColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val filteredApps by viewModel.filteredApps.collectAsState()
                    val searchQuery by viewModel.searchQuery.collectAsState()
                    val spaces by viewModel.spaces.collectAsState()
                    val currentSpaceId by viewModel.currentSpaceId.collectAsState()
                    val currentSpaceApps by viewModel.currentSpaceApps.collectAsState()
                    val assignments by viewModel.assignments.collectAsState()
                    val recentApps by viewModel.recentApps.collectAsState()
                    val lastNotification by viewModel.lastNotification.collectAsState()
                    val widgetPlacements by viewModel.widgetPlacements.collectAsState()

                    LauncherApp(
                        filteredApps = filteredApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        spaces = spaces,
                        currentSpaceId = currentSpaceId,
                        onSpaceSelected = viewModel::onSpaceSelected,
                        onAddSpace = viewModel::addSpace,
                        onRenameSpace = viewModel::renameSpace,
                        onDeleteSpace = viewModel::deleteSpace,
                        currentSpaceApps = currentSpaceApps,
                        assignments = assignments,
                        onToggleAppInSpace = viewModel::toggleAppInSpace,
                        themeMode = themeMode,
                        onThemeModeChange = viewModel::onThemeModeChange,
                        accentColor = accentColor,
                        onAccentColorChange = viewModel::onAccentColorChange,
                        recentApps = recentApps,
                        lastNotification = lastNotification,
                        onNotificationClick = ::onNotificationSummaryClick,
                        notificationAccessGranted = notificationAccessGranted,
                        onRequestNotificationAccess = ::openNotificationAccessSettings,
                        onAppClick = ::launchApp,
                        onRemoveAppFromCurrentSpace = viewModel::removeAppFromCurrentSpace,
                        onRecentAppLongClick = ::openAppInfoScreen,
                        widgetPlacements = widgetPlacements,
                        createWidgetHostView = widgetHostManager::createHostView,
                        onRemoveWidget = ::removeWidget,
                        onResizeWidget = { id, spanX, spanY -> viewModel.onWidgetResized(id, spanX, spanY) },
                        loadAvailableWidgets = ::loadAvailableWidgets,
                        onWidgetProviderSelected = ::onWidgetProviderSelected
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHostManager.startListening()
    }

    override fun onResume() {
        super.onResume()
        // O usuário pode ter concedido/revogado o acesso a notificações
        // em Configurações enquanto o app estava em segundo plano.
        notificationAccessGranted = isNotificationAccessGranted()
    }

    override fun onStop() {
        widgetHostManager.stopListening()
        super.onStop()
    }

    // ---------- Widgets: seletor visual + bind + configuração ----------

    private suspend fun loadAvailableWidgets(): List<WidgetProviderOption> =
        withContext(Dispatchers.Default) {
            widgetHostManager.loadAvailableWidgets(WidgetPrefs.CELL_SIZE_DP)
        }

    private fun onWidgetProviderSelected(option: WidgetProviderOption, spanX: Int, spanY: Int) {
        val appWidgetId = widgetHostManager.allocateAppWidgetId()
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showToast("Não foi possível iniciar a criação do widget.")
            return
        }
        pendingAppWidgetId = appWidgetId
        pendingSpanX = spanX
        pendingSpanY = spanY

        try {
            val alreadyBound = widgetHostManager.bindAppWidgetIdIfAllowed(appWidgetId, option.provider)
            if (alreadyBound) {
                proceedAfterBind(appWidgetId)
            } else {
                bindWidgetLauncher.launch(widgetHostManager.createBindIntent(appWidgetId, option.provider))
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Tela de vínculo de widget não encontrada", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível vincular este widget neste aparelho.")
        } catch (e: Exception) {
            Log.w(TAG, "Erro inesperado ao vincular widget", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível adicionar este widget.")
        }
    }

    private fun proceedAfterBind(appWidgetId: Int) {
        try {
            val configureIntent = widgetHostManager.createConfigureIntentIfNeeded(appWidgetId)
            if (configureIntent != null) {
                configureWidgetLauncher.launch(configureIntent)
            } else {
                finalizeWidget(appWidgetId)
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Activity de configuração do widget não encontrada", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível configurar este widget.")
        } catch (e: Exception) {
            Log.w(TAG, "Erro inesperado ao processar widget", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível adicionar este widget.")
        }
    }

    private fun finalizeWidget(appWidgetId: Int) {
        try {
            viewModel.onWidgetAdded(appWidgetId, pendingSpanX, pendingSpanY)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar o widget appWidgetId=$appWidgetId", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível salvar o widget.")
            return
        }
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun cancelPendingWidget(appWidgetId: Int) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetHostManager.deleteAppWidgetId(appWidgetId)
        }
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun removeWidget(appWidgetId: Int) {
        widgetHostManager.deleteAppWidgetId(appWidgetId)
        viewModel.onWidgetRemoved(appWidgetId)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---------- Apps ----------

    private fun launchApp(app: AppInfo) {
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            val componentName = ComponentName(app.packageName, app.activityName)
            launcherApps?.startMainActivity(componentName, Process.myUserHandle(), null, null)
            viewModel.onAppLaunched(app)
        } catch (_: Throwable) {
            try {
                packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    viewModel.onAppLaunched(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível abrir o app ${app.packageName}", e)
                showToast("Não foi possível abrir ${app.label}.")
            }
        }
    }

    /** Abre a tela de informações do app (permite forçar parada) — alternativa à Recentes nativa. */
    private fun openAppInfoScreen(app: AppInfo) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir informações de ${app.packageName}", e)
            showToast("Não foi possível abrir as informações de ${app.label}.")
        }
    }

    // ---------- Notificações ----------

    private fun isNotificationAccessGranted(): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    } catch (e: Exception) {
        false
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir configurações de notificação", e)
            showToast("Não foi possível abrir as configurações de notificação.")
        }
    }

    private fun onNotificationSummaryClick(info: com.aviatelite.launcher.notification.LastNotificationInfo) {
        // Abre o app de origem e dispensa a notificação (na Home e no sistema).
        try {
            packageManager.getLaunchIntentForPackage(info.packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir ${info.packageName} a partir da notificação", e)
        }
        viewModel.dismissLastNotification(info.key)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
