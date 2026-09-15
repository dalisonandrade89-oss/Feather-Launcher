package com.umilauncher.kiss.data

import android.content.ComponentName

/**
 * Modelo leve: guardamos só o essencial (String + ComponentName).
 * Nenhum Drawable/Bitmap é mantido aqui - isso vive só no IconCache,
 * que é o único ponto onde controlamos o uso de memória de imagens.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val userHandleHash: Int = 0
) {
    val key: String get() = "$packageName/$activityName"

    fun componentName() = ComponentName(packageName, activityName)
}
