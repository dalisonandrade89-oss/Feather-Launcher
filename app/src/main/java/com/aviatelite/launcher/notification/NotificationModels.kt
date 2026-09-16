package com.aviatelite.launcher.notification

import androidx.compose.ui.graphics.ImageBitmap

/** Resumo mínimo de uma notificação, só o necessário para exibir na Home. */
data class LastNotificationInfo(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val appIcon: ImageBitmap?
)
