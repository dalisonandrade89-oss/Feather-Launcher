package com.feather.launcher.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponte simples, em memória, entre o [FeatherNotificationListenerService]
 * (que roda no mesmo processo do app) e a UI Compose. Como o serviço e a
 * Activity vivem no mesmo processo, um singleton com StateFlow é
 * suficiente — não é necessário IPC/Binder para isso.
 */
object NotificationRepository {

    private val _lastNotification = MutableStateFlow<LastNotificationInfo?>(null)
    val lastNotification: StateFlow<LastNotificationInfo?> = _lastNotification.asStateFlow()

    fun post(info: LastNotificationInfo) {
        _lastNotification.value = info
    }

    /** Chamado quando o sistema remove a notificação (lida/descartada pelo usuário). */
    fun clearIfMatches(key: String) {
        if (_lastNotification.value?.key == key) {
            _lastNotification.value = null
        }
    }

    fun clear() {
        _lastNotification.value = null
    }
}
