package com.feather.launcher.data

import androidx.compose.ui.graphics.Color

/** Converte para "#RRGGBB" (sem alpha, já que é sempre uma cor opaca de destaque). */
fun Color.toHexString(): String {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

/** Faz o caminho inverso; retorna null se a string não for um hex válido. */
fun String.hexToColorOrNull(): Color? {
    val cleaned = removePrefix("#")
    if (cleaned.length != 6) return null
    return try {
        val value = cleaned.toLong(16)
        val r = ((value shr 16) and 0xFF).toInt()
        val g = ((value shr 8) and 0xFF).toInt()
        val b = (value and 0xFF).toInt()
        Color(red = r / 255f, green = g / 255f, blue = b / 255f)
    } catch (_: NumberFormatException) {
        null
    }
}
