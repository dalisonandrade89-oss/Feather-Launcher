package com.feather.launcher.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponte simples, em memória, entre o [FeatherNotificationListenerService]
 * (que roda no mesmo processo do app) e a UI Compose. Como o serviço e a
 * Activity vivem no mesmo processo, um singleton com StateFlow é
 * suficiente — não é necessário IPC/Binder para isso.
 *
 * Mantém duas coisas:
 * - [lastNotification]: a notificação mais recente, para o resumo na Home;
 * - [activeNotificationPackages]: TODOS os pacotes com notificação ativa
 *   no momento (não só o último), usado para o ponto nos ícones dos apps.
 *   Os métodos são `@Synchronized` porque o NotificationListenerService
 *   pode chamar de uma thread de binder do sistema, não necessariamente
 *   a main thread onde a UI Compose lê o StateFlow.
 */
object NotificationRepository {

    private val _lastNotification = MutableStateFlow<LastNotificationInfo?>(null)
    val lastNotification: StateFlow<LastNotificationInfo?> = _lastNotification.asStateFlow()

    // key da notificação -> packageName. Não exposto diretamente; serve
    // só para saber quando um pacote deixou de ter QUALQUER notificação
    // ativa (pode haver mais de uma notificação do mesmo app ao mesmo tempo).
    private val activePackageByKey = mutableMapOf<String, String>()

    private val _activeNotificationPackages = MutableStateFlow<Set<String>>(emptySet())
    val activeNotificationPackages: StateFlow<Set<String>> = _activeNotificationPackages.asStateFlow()

    @Synchronized
    fun post(info: LastNotificationInfo) {
        _lastNotification.value = info
        activePackageByKey[info.key] = info.packageName
        _activeNotificationPackages.value = activePackageByKey.values.toSet()
    }

    /** Chamado quando o sistema remove a notificação (lida/descartada pelo usuário). */
    @Synchronized
    fun clearIfMatches(key: String) {
        if (_lastNotification.value?.key == key) {
            _lastNotification.value = null
        }
        if (activePackageByKey.remove(key) != null) {
            _activeNotificationPackages.value = activePackageByKey.values.toSet()
        }
    }

    @Synchronized
    fun clear() {
        _lastNotification.value = null
        activePackageByKey.clear()
        _activeNotificationPackages.value = emptySet()
    }
}
