package com.aviatelite.launcher.notification

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

/**
 * Captura notificações em tempo real para alimentar o resumo exibido
 * abaixo do relógio na Home. Requer que o usuário conceda manualmente o
 * acesso de "Notificações" ao app (Settings > Acesso a notificações) —
 * isso NÃO pode ser concedido silenciosamente, é uma exigência do
 * próprio Android para qualquer app, inclusive launchers.
 */
class AviateNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return // ignora notificações do próprio launcher
            val notification = sbn.notification ?: return
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return

            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                sbn.packageName
            }

            val icon = loadSmallAppIcon(sbn.packageName)

            NotificationRepository.post(
                LastNotificationInfo(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    title = title,
                    text = text,
                    appIcon = icon
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao processar notificação", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationRepository.clearIfMatches(sbn.key)
    }

    private fun loadSmallAppIcon(pkg: String): androidx.compose.ui.graphics.ImageBitmap? {
        return try {
            val drawable = packageManager.getApplicationIcon(pkg)
            val sizePx = (32 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val bitmap: Bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "NotifListener"

        /**
         * Referência estática ao serviço em execução, usada apenas para que a
         * Activity possa pedir a dispensa (cancelNotification) de uma
         * notificação específica quando o usuário toca no resumo da Home.
         * Seguro aqui porque o serviço roda no mesmo processo do app.
         */
        private var instance: AviateNotificationListenerService? = null

        /** Dispensa a notificação do sistema correspondente a [key], se o serviço estiver ativo. */
        fun dismiss(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao dispensar notificação $key", e)
            }
            NotificationRepository.clearIfMatches(key)
        }
    }
}
