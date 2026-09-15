package com.umilauncher.kiss.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fonte única de verdade para a lista de apps.
 *
 * Diferença chave em relação a um launcher "ingênuo": não usamos
 * PackageManager.getInstalledApplications (que traz TODOS os pacotes,
 * incluindo os sem launcher/serviços) e não guardamos ApplicationInfo/
 * ResolveInfo em memória além do necessário para montar a lista - cada
 * ResolveInfo carrega referências pesadas (Drawable, metadata) que não
 * usamos e que o GC demoraria a coletar se ficassem retidos em uma lista
 * de estado do Compose.
 */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

        resolveInfos
            .asSequence()
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.componentName()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            appContext.startActivity(intent)
        } catch (e: PackageManager.NameNotFoundException) {
            // App desinstalado entre a listagem e o toque; ignoramos
            // silenciosamente e deixamos a próxima varredura corrigir a lista.
        } catch (e: SecurityException) {
            // Alguns apps de sistema restringem o lançamento externo.
        }
    }
}
