package com.feather.launcher

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feather.launcher.data.AppInfo
import com.feather.launcher.pwa.PwaBrowser
import com.feather.launcher.pwa.PwaShortcut
import com.feather.launcher.ui.LauncherApp
import com.feather.launcher.update.AppUpdateDialog
import com.feather.launcher.update.AppUpdateManager
import com.feather.launcher.update.UpdateUiState
import com.feather.launcher.ui.theme.FeatherTheme
import com.feather.launcher.viewmodel.LauncherViewModel
import com.feather.launcher.widget.WidgetHostManager
import com.feather.launcher.widget.WidgetPrefs
import com.feather.launcher.widget.WidgetProviderOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var appUpdateManager: AppUpdateManager
    private var canInstallPackages by mutableStateOf(false)

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
        appUpdateManager = AppUpdateManager(applicationContext)
        notificationAccessGranted = isNotificationAccessGranted()
        canInstallPackages = appUpdateManager.canRequestPackageInstalls()
        viewModel.checkForUpdates()
        canInstallPackages = appUpdateManager.canRequestPackageInstalls()
        viewModel.checkForUpdates()

        setContent {
            // FIX #25 (P2 da auditoria): collectAsStateWithLifecycle em vez
            // de collectAsState — a coleta (e a recomposição que ela pode
            // disparar) pausa sozinha quando a Activity não está em
            // STARTED (ex.: launcher em segundo plano, outro app na
            // frente), em vez de continuar reagindo a cada emissão dos
            // StateFlow mesmo sem nada visível na tela. A dependência
            // (androidx.lifecycle:lifecycle-runtime-compose) já estava no
            // projeto, só não era usada em lugar nenhum.
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
            val backgroundColor by viewModel.backgroundColor.collectAsStateWithLifecycle()
            val notificationScale by viewModel.notificationScale.collectAsStateWithLifecycle()

            FeatherTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                backgroundColor = backgroundColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
                    val pwaShortcuts by viewModel.pwaShortcuts.collectAsStateWithLifecycle()
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    val drawerViewMode by viewModel.drawerViewMode.collectAsStateWithLifecycle()
                    val spaces by viewModel.spaces.collectAsStateWithLifecycle()
                    val currentSpaceId by viewModel.currentSpaceId.collectAsStateWithLifecycle()
                    val currentSpaceApps by viewModel.currentSpaceApps.collectAsStateWithLifecycle()
                    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
                    val lastNotification by viewModel.lastNotification.collectAsStateWithLifecycle()
                    val appsWithNotifications by viewModel.appsWithNotifications.collectAsStateWithLifecycle()
                    val widgetPlacements by viewModel.widgetPlacements.collectAsStateWithLifecycle()
                    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

                    LaunchedEffect(updateState, canInstallPackages) {
                        val current = updateState
                        if (current is UpdateUiState.ReadyToInstall && canInstallPackages) {
                            installApkFile(current.apkFile)
                        }
                    }

                    LauncherApp(
                        filteredApps = filteredApps,
                        pwaShortcuts = pwaShortcuts,
                        onAddPwaShortcut = viewModel::addPwaShortcut,
                        onOpenBrowserToInstallPwa = { url, browser -> openUrlInBrowser(url, browser, showInstallGuide = true) },
                        onRemovePwaShortcut = viewModel::removePwaShortcut,
                        onLaunchPwaShortcut = ::launchPwaShortcut,
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        drawerViewMode = drawerViewMode,
                        onToggleDrawerViewMode = viewModel::toggleDrawerViewMode,
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
                        backgroundColor = backgroundColor,
                        onBackgroundColorChange = viewModel::onBackgroundColorChange,
                        notificationScale = notificationScale,
                        onNotificationScaleChange = viewModel::onNotificationScaleChange,
                        appsWithNotifications = appsWithNotifications,
                        lastNotification = lastNotification,
                        onNotificationClick = ::onNotificationSummaryClick,
                        notificationAccessGranted = notificationAccessGranted,
                        onRequestNotificationAccess = ::openNotificationAccessSettings,
                        onAppClick = ::launchApp,
                        onOpenAppInfo = ::openAppInfoScreen,
                        onRemoveAppFromCurrentSpace = viewModel::removeAppFromCurrentSpace,
                        widgetPlacements = widgetPlacements,
                        createWidgetHostView = widgetHostManager::createHostView,
                        onRemoveWidget = ::removeWidget,
                        onResizeWidget = { id, spanX, spanY -> viewModel.onWidgetResized(id, spanX, spanY) },
                        loadAvailableWidgets = ::loadAvailableWidgets,
                        onWidgetProviderSelected = ::onWidgetProviderSelected
                    )

                    AppUpdateDialog(
                        state = updateState,
                        needsInstallPermission = !canInstallPackages,
                        onConfirmDownload = viewModel::startUpdateDownload,
                        onInstallNow = {
                            val current = updateState
                            if (current is UpdateUiState.ReadyToInstall) {
                                installApkFile(current.apkFile)
                            }
                        },
                        onOpenInstallPermission = ::openUnknownSourcesSettings,
                        onDismiss = viewModel::dismissUpdateDialog
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
        canInstallPackages = appUpdateManager.canRequestPackageInstalls()
        viewModel.checkForUpdates()
    }

    override fun onStop() {
        widgetHostManager.stopListening()
        super.onStop()
    }

    // ---------- Auto-Update: instalação do APK ----------

    private fun openUnknownSourcesSettings() {
        try {
            startActivity(appUpdateManager.buildUnknownSourcesSettingsIntent())
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir configuração de fontes desconhecidas", e)
            showToast("Abra Configurações e permita instalar apps desconhecidos.")
        }
    }

    private fun installApkFile(apkFile: java.io.File) {
        if (!appUpdateManager.canRequestPackageInstalls()) {
            canInstallPackages = false
            openUnknownSourcesSettings()
            return
        }
        try {
            startActivity(appUpdateManager.buildInstallIntent(apkFile))
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível iniciar o instalador de pacotes", e)
            showToast("Não foi possível abrir o instalador do Android.")
        }
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
        } catch (_: Throwable) {
            try {
                packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível abrir o app ${app.packageName}", e)
                showToast("Não foi possível abrir ${app.label}.")
            }
        }
    }

    /** Abre a tela de informações do app (permite forçar parada). */
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

    // ---------- PWAs (instalação via menu do navegador + abertura sem barra) ----------

    private fun openUrlInBrowser(url: String, preferredBrowser: PwaBrowser, showInstallGuide: Boolean = false) {
        val uri = Uri.parse(url)
        val targets = buildList {
            add(preferredBrowser)
            for (b in PwaBrowser.preferredBrowsers) {
                if (b !in this) add(b)
            }
        }

        for (browser in targets) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(browser.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    if (showInstallGuide) {
                        showToast(browser.installHint)
                    }
                    return
                }
            } catch (_: Exception) {
            }
        }

        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fallbackIntent)
            if (showInstallGuide) {
                showToast("Use o menu do navegador em "Instalar página como app".")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir $url", e)
            showToast("Não foi possível abrir o navegador.")
        }
    }

    private fun launchPwaShortcut(shortcut: PwaShortcut) {
        openUrlInBrowser(
            url = shortcut.url,
            preferredBrowser = shortcut.browser,
            showInstallGuide = false
        )
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

    private fun onNotificationSummaryClick(info: com.feather.launcher.notification.LastNotificationInfo) {
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
