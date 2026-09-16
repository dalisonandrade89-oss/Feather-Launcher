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
import androidx.compose.ui.Modifier
import com.aviatelite.launcher.data.AppInfo
import com.aviatelite.launcher.ui.LauncherApp
import com.aviatelite.launcher.ui.theme.AviateLiteTheme
import com.aviatelite.launcher.viewmodel.LauncherViewModel
import com.aviatelite.launcher.widget.WidgetHostManager

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private lateinit var widgetHostManager: WidgetHostManager

    /**
     * ID do widget em processo de criação (pick -> configure opcional -> add).
     * Guardado como campo (em vez de depender só do Intent de retorno)
     * porque quando o usuário CANCELA o seletor do sistema, o result.data
     * costuma vir nulo — sem esse campo, o appWidgetId alocado "vazaria"
     * (ficaria reservado no AppWidgetHost para sempre).
     */
    private var pendingAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    // Fluxo: usuário escolhe o widget na lista do sistema.
    private val pickWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = extractAppWidgetId(result.data) ?: pendingAppWidgetId

        if (result.resultCode == RESULT_OK && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                val configureIntent = widgetHostManager.createConfigureIntentIfNeeded(appWidgetId)
                if (configureIntent != null) {
                    pendingAppWidgetId = appWidgetId
                    configureWidgetLauncher.launch(configureIntent)
                } else {
                    finalizeWidget(appWidgetId)
                }
            } catch (e: ActivityNotFoundException) {
                // Widget exige configuração, mas a Activity de configuração
                // não existe/foi desinstalada: descarta o widget com segurança.
                Log.w(TAG, "Activity de configuração do widget não encontrada", e)
                cancelPendingWidget(appWidgetId)
                showToast("Não foi possível configurar este widget.")
            } catch (e: Exception) {
                Log.w(TAG, "Erro inesperado ao processar widget escolhido", e)
                cancelPendingWidget(appWidgetId)
                showToast("Não foi possível adicionar este widget.")
            }
        } else {
            // Usuário cancelou o seletor, ou nenhum widget válido foi retornado.
            cancelPendingWidget(appWidgetId)
        }
    }

    // Fluxo opcional: configuração exigida por alguns widgets (ex.: calendário).
    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = pendingAppWidgetId
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

        if (result.resultCode == RESULT_OK && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            finalizeWidget(appWidgetId)
        } else {
            cancelPendingWidget(appWidgetId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetHostManager = WidgetHostManager(applicationContext)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            AviateLiteTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val apps by viewModel.allApps.collectAsState()
                    val filteredApps by viewModel.filteredApps.collectAsState()
                    val searchQuery by viewModel.searchQuery.collectAsState()
                    val spaces by viewModel.spaces.collectAsState()
                    val contextMode by viewModel.contextMode.collectAsState()
                    val recentApps by viewModel.recentApps.collectAsState()
                    val widgetIds by viewModel.widgetIds.collectAsState()

                    LauncherApp(
                        apps = apps,
                        filteredApps = filteredApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        spaces = spaces,
                        contextMode = contextMode,
                        onContextModeChange = viewModel::onContextModeChange,
                        themeMode = themeMode,
                        onThemeModeChange = viewModel::onThemeModeChange,
                        recentApps = recentApps,
                        onAppClick = ::launchApp,
                        onAppLongClick = viewModel::onToggleAppPinned,
                        onRecentAppLongClick = ::openAppInfoScreen,
                        widgetIds = widgetIds,
                        createWidgetHostView = widgetHostManager::createHostView,
                        onAddWidgetClick = ::startAddWidgetFlow,
                        onRemoveWidget = ::removeWidget
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // AppWidgetHost só deve "escutar" atualizações enquanto a UI
        // está visível — evita desenho/trabalho de widgets em segundo plano.
        widgetHostManager.startListening()
    }

    override fun onStop() {
        widgetHostManager.stopListening()
        super.onStop()
    }

    /**
     * FIX (crash ao adicionar widget): todo o fluxo de escolha do widget é
     * protegido por try-catch. Em alguns dispositivos/ROMs (comum em
     * customizações MediaTek) a Activity do seletor de widgets do sistema
     * (ACTION_APPWIDGET_PICK) pode não existir, o que lançava
     * ActivityNotFoundException direto na UI thread e derrubava o app.
     */
    private fun startAddWidgetFlow() {
        val appWidgetId = widgetHostManager.allocateAppWidgetId()
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showToast("Não foi possível iniciar a criação do widget.")
            return
        }

        pendingAppWidgetId = appWidgetId

        try {
            pickWidgetLauncher.launch(widgetHostManager.createPickIntent(appWidgetId))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Seletor de widgets do sistema não encontrado", e)
            cancelPendingWidget(appWidgetId)
            showToast("Nenhum seletor de widgets disponível neste aparelho.")
        } catch (e: Exception) {
            Log.w(TAG, "Erro inesperado ao abrir o seletor de widgets", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível abrir o seletor de widgets.")
        }
    }

    /** Confirma o widget no ViewModel (persistência), protegido contra falhas. */
    private fun finalizeWidget(appWidgetId: Int) {
        try {
            viewModel.onWidgetAdded(appWidgetId)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao salvar o widget appWidgetId=$appWidgetId", e)
            cancelPendingWidget(appWidgetId)
            showToast("Não foi possível salvar o widget.")
        }
    }

    /** Libera um appWidgetId alocado que não deve mais ser usado (cancelado/erro). */
    private fun cancelPendingWidget(appWidgetId: Int) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetHostManager.deleteAppWidgetId(appWidgetId)
        }
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun extractAppWidgetId(data: Intent?): Int? {
        val id = data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return if (id != AppWidgetManager.INVALID_APPWIDGET_ID) id else null
    }

    // FIX #3: remoção de widget continua disponível (toque longo na
    // alça ou botão de lixeira, ambos implementados em WidgetsPanelScreen)
    // e libera o appWidgetId tanto no AppWidgetHost quanto no WidgetPrefs.
    private fun removeWidget(appWidgetId: Int) {
        widgetHostManager.deleteAppWidgetId(appWidgetId)
        viewModel.onWidgetRemoved(appWidgetId)
    }

    private fun launchApp(app: AppInfo) {
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            val componentName = ComponentName(app.packageName, app.activityName)
            launcherApps?.startMainActivity(componentName, Process.myUserHandle(), null, null)
            viewModel.onAppLaunched(app)
        } catch (_: Throwable) {
            // Fallback simples caso LauncherApps falhe (ex.: ROMs customizadas).
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

    /**
     * FIX #7: um launcher de terceiros não tem permissão de sistema para
     * desenhar a tela nativa de "Apps Recentes" (exclusiva do SystemUI).
     * Como alternativa funcional e oficialmente suportada, abrimos a tela
     * de informações do app (Settings), de onde o usuário pode forçar a
     * parada — a mesma ação que o painel de recentes do sistema oferece.
     */
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

    companion object {
        private const val TAG = "MainActivity"
    }
}
