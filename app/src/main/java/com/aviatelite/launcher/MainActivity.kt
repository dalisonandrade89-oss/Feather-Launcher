package com.aviatelite.launcher

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
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

    /** ID temporário do widget em processo de criação (pick -> bind -> configure). */
    private var pendingAppWidgetId: Int = -1

    // Fluxo: usuário escolhe o widget na lista do sistema.
    private val pickWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val appWidgetId = data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (result.resultCode == RESULT_OK && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val configureIntent = widgetHostManager.createConfigureIntentIfNeeded(appWidgetId)
            if (configureIntent != null) {
                pendingAppWidgetId = appWidgetId
                configureWidgetLauncher.launch(configureIntent)
            } else {
                viewModel.onWidgetAdded(appWidgetId)
            }
        } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetHostManager.deleteAppWidgetId(appWidgetId)
        }
    }

    // Fluxo opcional: configuração exigida por alguns widgets (ex.: calendário).
    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = pendingAppWidgetId
        pendingAppWidgetId = -1
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            viewModel.onWidgetAdded(appWidgetId)
        } else if (appWidgetId != -1) {
            widgetHostManager.deleteAppWidgetId(appWidgetId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetHostManager = WidgetHostManager(applicationContext)

        setContent {
            AviateLiteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val apps by viewModel.allApps.collectAsState()
                    val filteredApps by viewModel.filteredApps.collectAsState()
                    val searchQuery by viewModel.searchQuery.collectAsState()
                    val spaces by viewModel.spaces.collectAsState()
                    val contextMode by viewModel.contextMode.collectAsState()
                    val widgetIds by viewModel.widgetIds.collectAsState()

                    LauncherApp(
                        apps = apps,
                        filteredApps = filteredApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        spaces = spaces,
                        contextMode = contextMode,
                        onContextModeChange = viewModel::onContextModeChange,
                        onAppClick = ::launchApp,
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

    private fun startAddWidgetFlow() {
        val appWidgetId = widgetHostManager.allocateAppWidgetId()
        val pickIntent = widgetHostManager.createPickIntent(appWidgetId)
        pickWidgetLauncher.launch(pickIntent)
    }

    private fun removeWidget(appWidgetId: Int) {
        widgetHostManager.deleteAppWidgetId(appWidgetId)
        viewModel.onWidgetRemoved(appWidgetId)
    }

    private fun launchApp(app: AppInfo) {
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            val componentName = android.content.ComponentName(app.packageName, app.activityName)
            launcherApps?.startMainActivity(componentName, Process.myUserHandle(), null, null)
        } catch (_: Throwable) {
            // Fallback simples caso LauncherApps falhe (ex.: ROMs customizadas).
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }
}
