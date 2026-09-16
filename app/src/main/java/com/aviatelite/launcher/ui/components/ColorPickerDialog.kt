package com.aviatelite.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Cores prontas para seleção rápida, além do slider de matiz (hue) contínuo. */
private val presetColors = listOf(
    Color(0xFF5AC8FA), Color(0xFFFF6B6B), Color(0xFFFFA94D),
    Color(0xFFFFD43B), Color(0xFF69DB7C), Color(0xFF3BC9DB),
    Color(0xFFB197FC), Color(0xFFF783AC)
)

/**
 * Seletor de cor de destaque simples: uma faixa de matiz (hue) que pode
 * ser arrastada para qualquer ponto do espectro (cor 100% personalizada,
 * não só predefinida), mais uma linha de cores prontas para um toque
 * rápido. Usa apenas Canvas + gestos de arraste/toque, ambos APIs
 * estáveis do Compose — sem bibliotecas externas de color picker.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var hue by remember { mutableFloatStateOf(colorToHue(initialColor)) }
    val currentColor = Color.hsv(hue, 1f, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cor de destaque") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                Text(
                    text = "Arraste para escolher a matiz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )

                Text(
                    text = "Ou escolha uma cor pronta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presetColors.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                    shape = CircleShape
                                )
                                .clickable { hue = colorToHue(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Faixa horizontal com o espectro de matizes (hue 0..360); arraste para escolher. */
@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val hueGradient = remember {
        Brush.horizontalGradient(
            colors = (0..360 step 30).map { degrees -> Color.hsv(degrees.toFloat().coerceAtMost(359f), 1f, 1f) }
        )
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                onHueChange(fraction * 360f)
                change.consume()
            }
        }
    ) {
        drawRoundRect(
            brush = hueGradient,
            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
        )
        val indicatorX = (hue / 360f) * size.width
        drawCircle(
            color = Color.White,
            radius = size.height / 2,
            center = Offset(indicatorX.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2),
            style = Stroke(width = 4f)
        )
    }
}

private fun colorToHue(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toAndroidArgb(), hsv)
    return hsv[0]
}

private fun Color.toAndroidArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt(),
    (red * 255f).toInt(),
    (green * 255f).toInt(),
    (blue * 255f).toInt()
)
