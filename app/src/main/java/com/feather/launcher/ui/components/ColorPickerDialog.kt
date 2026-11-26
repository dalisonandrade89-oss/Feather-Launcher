package com.feather.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.feather.launcher.data.toHexString

enum class ColorTargetMode(val title: String) {
    ACCENT("Cor de destaque"),
    BACKGROUND("Cor de fundo")
}

private val accentPresets = listOf(
    Color(0xFF5AC8FA), Color(0xFF38BDF8), Color(0xFF69DB7C),
    Color(0xFFFFA94D), Color(0xFFFF6B6B), Color(0xFFB197FC),
    Color(0xFFF783AC), Color(0xFFFFD43B)
)

private val backgroundPresets = listOf(
    Color(0xFF000000), Color(0xFF0E0F12), Color(0xFF141820),
    Color(0xFF18141C), Color(0xFF101C18), Color(0xFF1E1914),
    Color(0xFFF7F8FA), Color(0xFFEAECEF)
)

/**
 * Seletor moderno e leve para cor de destaque ou fundo:
 * 1) Campo 2D de Saturação (X) e Brilho/Valor (Y) em um único Canvas
 * 2) Barra contínua de Matiz (Hue 0..360)
 * 3) Swatches rápidos sem re-alocar bitmaps ou bibliotecas pesadas
 */
@Composable
fun ColorPickerDialog(
    mode: ColorTargetMode,
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor, mode) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toAndroidArgb(), hsv)
        hsv
    }

    var hue by remember(initialColor, mode) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor, mode) {
        mutableFloatStateOf(initialHsv[1].coerceIn(0f, 1f))
    }
    var value by remember(initialColor, mode) {
        mutableFloatStateOf(initialHsv[2].coerceIn(0.04f, 1f))
    }

    val selectedColor = Color.hsv(hue, saturation, value)
    val presets = if (mode == ColorTargetMode.ACCENT) accentPresets else backgroundPresets

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = mode.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedColor.toHexString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SaturationValuePad(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSelect = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                HueBar(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.forEach { preset ->
                        val isMatch = preset.toHexString().equals(selectedColor.toHexString(), ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .border(
                                    width = if (isMatch) 2.5.dp else 1.dp,
                                    color = if (isMatch) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(preset.toAndroidArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1].coerceIn(0f, 1f)
                                    value = hsv[2].coerceIn(0.04f, 1f)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun SaturationValuePad(
    hue: Float,
    saturation: Float,
    value: Float,
    onSelect: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pureHueColor = remember(hue) { Color.hsv(hue, 1f, 1f) }
    val horizontalBrush = remember(pureHueColor) {
        Brush.horizontalGradient(listOf(Color.White, pureHueColor))
    }
    val verticalBrush = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
    }

    Canvas(
        modifier = modifier
            .pointerInput(hue) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val v = (1f - (offset.y / size.height.coerceAtLeast(1))).coerceIn(0.04f, 1f)
                    onSelect(s, v)
                }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val v = (1f - (change.position.y / size.height.coerceAtLeast(1))).coerceIn(0.04f, 1f)
                    onSelect(s, v)
                    change.consume()
                }
            }
    ) {
        drawRect(brush = horizontalBrush)
        drawRect(brush = verticalBrush)

        val cx = (saturation * size.width).coerceIn(8f, size.width - 8f)
        val cy = ((1f - value) * size.height).coerceIn(8f, size.height - 8f)
        drawCircle(
            color = Color.White,
            radius = 11f,
            center = Offset(cx, cy),
            style = Stroke(width = 3.5f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = 13f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueGradient = remember {
        Brush.horizontalGradient(
            colors = (0..360 step 30).map { deg ->
                Color.hsv(deg.toFloat().coerceAtMost(359f), 1f, 1f)
            }
        )
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onHueChange(fraction * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onHueChange(fraction * 360f)
                    change.consume()
                }
            }
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            brush = hueGradient,
            cornerRadius = CornerRadius(radius, radius)
        )
        val x = ((hue / 360f) * size.width).coerceIn(radius, size.width - radius)
        drawCircle(
            color = Color.White,
            radius = radius - 2f,
            center = Offset(x, radius),
            style = Stroke(width = 3.5f)
        )
    }
}

private fun Color.toAndroidArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt(),
    (red * 255f).toInt(),
    (green * 255f).toInt(),
    (blue * 255f).toInt()
)
