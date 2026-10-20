package io.github.sudodiego.g9control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Nivel 0–4 de la app oficial a partir del valor 0–100 guardado en el control. */
fun gripLevel(v: Int) = ((v + 12) / 25).coerceIn(0, 4)

@Composable
fun VibrationPage(s: AppState, a: MainActivity) {
    val v = s.vibration
    TwoPane(
        first = {
            SidePanel(tr("Lado izquierdo", "Left side"), "LT", gripLevel(v.leftGrip), s.lt, s.triggerPreview,
                onLevel = { s.vibration = s.vibration.copy(leftGrip = it * 25) },
                onTest = { a.testRumble(255, 0) })
        },
        second = {
            SidePanel(tr("Lado derecho", "Right side"), "RT", gripLevel(v.rightGrip), s.rt, s.triggerPreview,
                onLevel = { s.vibration = s.vibration.copy(rightGrip = it * 25) },
                onTest = { a.testRumble(0, 255) })
        }
    )
    Panel {
        SwitchRow(tr("Probar con LT y RT", "Test with LT and RT"), s.triggerPreview) { s.triggerPreview = it }
        Hint(tr("En modo Android los juegos no hacen vibrar el control.", "In Android mode, games don't vibrate the controller."))
    }
}

/** Nombre de cada nivel (el firmware solo guarda 0, 25, 50, 75 y 100). */
private fun levelNames() = listOf(tr("Apagada", "Off"), tr("Suave", "Light"), tr("Media", "Medium"), tr("Fuerte", "Strong"), tr("Máxima", "Max"))

@Composable
private fun SidePanel(
    title: String, trigger: String, level: Int, live: Float, preview: Boolean,
    onLevel: (Int) -> Unit, onTest: () -> Unit
) {
    // Mientras dura la prueba (~1 s) el dibujo vibra al máximo del nivel elegido.
    var testing by remember { mutableStateOf(false) }
    LaunchedEffect(testing) { if (testing) { kotlinx.coroutines.delay(1100); testing = false } }
    val drive = when {
        testing -> 1f
        preview -> live.coerceIn(0f, 1f)
        else -> 0f
    }
    Panel(title, "${levelNames()[level]} · ${level * 25}%", trailing = {
        FilledTonalButton(onClick = { testing = true; onTest() }, enabled = LocalEditable.current && level > 0) {
            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Probar", "Test"))
        }
    }) {
        MotorView(level, drive, Modifier.fillMaxWidth().height(130.dp))
        LevelSelector(level, onLevel)
    }
}

/**
 * Motor de vibración visto de frente: más ondas y más rápidas a mayor nivel. Si [drive] > 0
 * (gatillo apretado o prueba), el motor tiembla con esa fuerza multiplicada por el nivel.
 */
@Composable
private fun MotorView(level: Int, drive: Float, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "motor")
    val period = when (level) { 0 -> 2000; 1 -> 1400; 2 -> 1000; 3 -> 750; else -> 520 }
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "ondas"
    )
    val shake by t.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(45, easing = LinearEasing), RepeatMode.Reverse), label = "temblor"
    )
    Canvas(modifier.clipToBounds()) {
        val cx = size.width / 2; val cy = size.height / 2
        val strength = drive * level / 4f
        val dx = shake * 6.dp.toPx() * strength
        val body = Size(44.dp.toPx(), 70.dp.toPx())
        val color = if (level == 0) c.outline else c.primary
        // Ondas a ambos lados, tantas como el nivel, hasta el borde del dibujo (arco de 80°).
        val r0 = body.width * 0.9f
        val rMax = minOf(size.width / 2 - 8.dp.toPx(), (size.height / 2 - 8.dp.toPx()) / 0.643f)
        if (level > 0) for (side in listOf(-1f, 1f)) for (i in 0 until level) {
            val f = (i + phase) / level
            val r = r0 + f * (rMax - r0)
            drawArc(
                color, if (side < 0) 140f else -40f, 80f, false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                alpha = (1f - f) * (0.4f + 0.6f * level / 4f),
                style = Stroke((3f + level * 1.5f).dp.toPx(), cap = StrokeCap.Round)
            )
        }
        // Motor: cuerpo redondeado, con tres líneas de agarre; se desplaza al temblar.
        val tl = Offset(cx - body.width / 2 + dx, cy - body.height / 2)
        drawRoundRect(color.copy(alpha = 0.16f), tl, body, CornerRadius(14.dp.toPx()))
        drawRoundRect(color, tl, body, CornerRadius(14.dp.toPx()), style = Stroke(3.dp.toPx()))
        for (k in -1..1) {
            val y = cy + k * 12.dp.toPx()
            drawLine(color, Offset(cx - 10.dp.toPx() + dx, y), Offset(cx + 10.dp.toPx() + dx, y), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Cinco niveles con nombre; el elegido se resalta. */
@Composable
private fun LevelSelector(level: Int, onLevel: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..4) {
            val on = i == level
            Surface(
                onClick = { onLevel(i) }, enabled = LocalEditable.current,
                shape = MaterialTheme.shapes.small,
                color = if (on) c.primary else c.surfaceContainerHighest,
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        levelNames()[i], fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) c.onPrimary else c.onSurface, maxLines = 1
                    )
                    Text("${i * 25}%", fontSize = 11.sp, color = if (on) c.onPrimary.copy(alpha = 0.8f) else c.onSurfaceVariant)
                }
            }
        }
    }
}
