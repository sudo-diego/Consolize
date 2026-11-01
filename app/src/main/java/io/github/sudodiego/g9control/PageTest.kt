package io.github.sudodiego.g9control

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Botones que llegan a Android. El M no: el control manda encendido + volumen abajo por su
 * dispositivo "Consumer Control" (captura de pantalla), y Android lo procesa antes que las apps.
 */
val TESTABLE = PadKey.entries - PadKey.M

/** Tecla de Android → botón del G9 (en X-Input y DS5 Android usa los mismos códigos). */
fun padKeyOf(keyCode: Int): PadKey? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> PadKey.A
    KeyEvent.KEYCODE_BUTTON_B -> PadKey.B
    KeyEvent.KEYCODE_BUTTON_X -> PadKey.X
    KeyEvent.KEYCODE_BUTTON_Y -> PadKey.Y
    KeyEvent.KEYCODE_BUTTON_L1 -> PadKey.LB
    KeyEvent.KEYCODE_BUTTON_R1 -> PadKey.RB
    KeyEvent.KEYCODE_BUTTON_L2 -> PadKey.LT
    KeyEvent.KEYCODE_BUTTON_R2 -> PadKey.RT
    KeyEvent.KEYCODE_BUTTON_THUMBL -> PadKey.L3
    KeyEvent.KEYCODE_BUTTON_THUMBR -> PadKey.R3
    KeyEvent.KEYCODE_BUTTON_SELECT -> PadKey.VIEW
    KeyEvent.KEYCODE_BUTTON_START -> PadKey.MENU
    KeyEvent.KEYCODE_BUTTON_MODE -> PadKey.HOME
    KeyEvent.KEYCODE_DPAD_UP -> PadKey.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> PadKey.DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> PadKey.LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> PadKey.RIGHT
    else -> null
}

/** Lo que está apretado ahora: teclas, cruceta como eje (HAT), gatillos y sticks. */
fun pressedNow(s: AppState): Set<PadKey> = buildSet {
    s.pressedKeys.keys.forEach { k -> padKeyOf(k)?.let(::add) }
    if (s.hatY < -0.5f) add(PadKey.UP)
    if (s.hatY > 0.5f) add(PadKey.DOWN)
    if (s.hatX < -0.5f) add(PadKey.LEFT)
    if (s.hatX > 0.5f) add(PadKey.RIGHT)
    if (s.lt > 0.5f) add(PadKey.LT)
    if (s.rt > 0.5f) add(PadKey.RT)
    if (hypot(s.lx, s.ly) > 0.5f) add(PadKey.STICK_L)
    if (hypot(s.rx, s.ry) > 0.5f) add(PadKey.STICK_R)
}

/**
 * Probar control (sección Tablet): botones, gatillos y sticks de cualquier control, no solo el G9 (lo que llega a
 * Android). A la derecha los controles conectados y los sticks en números: sirve para ver si un stick tiene deriva
 * (no vuelve a 0) o si un gatillo no llega al fondo.
 */
@Composable
fun ControlsPage(s: AppState, a: MainActivity) {
    val right: @Composable ColumnScope.() -> Unit = {
        PadsPanel(a)
        SticksReadout(s)
        if (s.otherKeys.isNotEmpty()) Panel(tr("Otros botones", "Other buttons")) { Text(s.otherKeys.joinToString("\n"), fontSize = 13.sp) }
    }
    DrawingPane(drawing = { TestPanel(s) }, side = right)
}

/** Los controles conectados (los que Android ve como gamepad), con su nombre y sus ids. */
@Composable
private fun PadsPanel(a: MainActivity) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }
    val pads = remember(tick) {
        android.view.InputDevice.getDeviceIds().toList().mapNotNull { android.view.InputDevice.getDevice(it) }
            .filter { d -> !d.isVirtual && (d.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD }
            .distinctBy { it.descriptor }
    }
    val c = MaterialTheme.colorScheme
    Panel(tr("Controles conectados", "Connected controllers")) {
        if (pads.isEmpty()) Hint(tr("Ninguno: conecta un control por USB o Bluetooth.", "None: connect a controller over USB or Bluetooth."))
        pads.forEach { d ->
            Column {
                Text(d.name, fontSize = 15.sp, maxLines = 1)
                Text("%04x:%04x".format(d.vendorId, d.productId) + (if (d.vibratorManager.vibratorIds.isNotEmpty()) tr(" · vibra", " · rumble") else ""),
                    fontSize = 12.sp, color = c.onSurfaceVariant)
            }
        }
    }
}

/** Los dos sticks en números (−1 a 1): en reposo deberían quedar cerca de 0. */
@Composable
private fun SticksReadout(s: AppState) {
    val on = s.testMode
    fun v(x: Float) = if (on) x else 0f
    val c = MaterialTheme.colorScheme
    Panel(tr("Sticks", "Sticks")) {
        // Cada stick dibujado: el punto donde está y la estela de todo lo que recorrió (al girarlo, se ve si llega a todo
        // el borde, como en Sticks del G9). Los números, chicos debajo.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(tr("Izquierdo", "Left") to (v(s.lx) to v(s.ly)), tr("Derecho", "Right") to (v(s.rx) to v(s.ry))).forEach { (name, xy) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StickTrace(xy.first, xy.second, on, Modifier.size(150.dp))
                    Text(name, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("%+.2f  %+.2f".format(xy.first, xy.second), fontSize = 12.sp, color = c.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
                }
            }
        }
        if (!on) Hint(tr("Toca «Probar botones» y gira los sticks.", "Tap “Test buttons” and rotate the sticks."))
    }
}

/**
 * Un stick: el círculo, lo que alcanzó en cada dirección (una figura que se va llenando hacia el borde al girarlo; no se
 * borra mientras dura la prueba) y el punto actual. Si la figura no llega al círculo en algún lado, el stick no alcanza.
 */
@Composable
private fun StickTrace(x: Float, y: Float, on: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val n = 64
    val reach = remember { androidx.compose.runtime.mutableStateListOf<Float>().apply { repeat(n) { add(0f) } } }
    LaunchedEffect(on) { if (!on) for (i in 0 until n) reach[i] = 0f }
    LaunchedEffect(x, y) {
        if (!on) return@LaunchedEffect
        val d = kotlin.math.hypot(x, y).coerceAtMost(1f)
        if (d < 0.05f) return@LaunchedEffect
        val ang = (kotlin.math.atan2(y, x) + 2 * Math.PI) % (2 * Math.PI)
        val i = ((ang / (2 * Math.PI)) * n).toInt().coerceIn(0, n - 1)
        if (d > reach[i]) reach[i] = d
    }
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2f - 6.dp.toPx()
        drawCircle(c.onSurface.copy(alpha = 0.06f), r, center)
        drawLine(c.outlineVariant, androidx.compose.ui.geometry.Offset(center.x - r, center.y), androidx.compose.ui.geometry.Offset(center.x + r, center.y), 1.5f)
        drawLine(c.outlineVariant, androidx.compose.ui.geometry.Offset(center.x, center.y - r), androidx.compose.ui.geometry.Offset(center.x, center.y + r), 1.5f)
        if (reach.any { it > 0f }) {
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0 until n) {
                val a = (i + 0.5) / n * 2 * Math.PI
                val pt = androidx.compose.ui.geometry.Offset(center.x + (kotlin.math.cos(a) * reach[i] * r).toFloat(), center.y + (kotlin.math.sin(a) * reach[i] * r).toFloat())
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            drawPath(path, c.primary.copy(alpha = 0.22f))
            drawPath(path, c.primary.copy(alpha = 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        drawCircle(c.outline.copy(alpha = 0.5f), r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        val p = androidx.compose.ui.geometry.Offset(center.x + x.coerceIn(-1f, 1f) * r, center.y + y.coerceIn(-1f, 1f) * r)
        drawCircle(c.primary, 9.dp.toPx(), p)
        drawCircle(c.onPrimary, 9.dp.toPx(), p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
    }
}

/** M dos veces en DS5 (el clic del touchpad del G9): captura. Es del G9: otros controles no lo tienen así. */
@Composable
internal fun TouchpadShotPanel(s: AppState, a: MainActivity) {
    var on by remember { mutableStateOf(AutoRules.touchpadShot(a)) }
    // La captura la saca el servicio de accesibilidad: sin él, oscurecido y con el recordatorio.
    Gate(s.autoServiceOn) {
        CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
            Panel {
                SwitchRow(tr("M dos veces: captura", "M twice: screenshot"), on, tr("En DS5", "In DS5")) { on = it; AutoRules.setTouchpadShot(a, it) }
            }
        }
    }
    if (!s.autoServiceOn) Hint(tr("M dos veces necesita el servicio de accesibilidad (Ajustes → Permisos).", "M twice needs the accessibility service (Settings → Permissions)."))
}

@Composable
private fun TestPanel(s: AppState) {
    // Fuera de la prueba el dibujo queda quieto (el stick izquierdo además mueve el foco como cruceta).
    val on = s.testMode
    val pressed = if (on) pressedNow(s) else emptySet()
    Panel {
        fun v(x: Float) = if (on) x else 0f
        // El dibujo del G9 solo si hay un G9 conectado (en cualquier modo); con otro control, uno genérico.
        val g9 = remember(on, s.connected) {
            android.view.InputDevice.getDeviceIds().toList().mapNotNull { android.view.InputDevice.getDevice(it) }
                .any { !it.isVirtual && (it.name.contains("G9", ignoreCase = true) ||
                    // En X-Input el G9 se presenta como el mando de Xbox 360 (045e:028e).
                    (it.vendorId == 0x045e && it.productId == 0x028e)) }
        }
        if (g9) ControllerTestView(pressed, v(s.lx), v(s.ly), v(s.rx), v(s.ry), v(s.lt), v(s.rt), Modifier.fillMaxWidth(), lights = s)
        else GenericControllerView(pressed, v(s.lx), v(s.ly), v(s.rx), v(s.ry), v(s.lt), v(s.rt), Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Prueba de botones", "Button test"), fontWeight = FontWeight.SemiBold)
                Text(tr("${s.testedKeys.keys.count { it in TESTABLE }} de ${TESTABLE.size} probados", "${s.testedKeys.keys.count { it in TESTABLE }} of ${TESTABLE.size} tested"), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.testMode) {
                TextButton(onClick = { s.testedKeys.clear(); s.otherKeys.clear() }) { Text(tr("Reiniciar", "Reset")) }
                // Junto a Terminar: B con su aro, que se llena mientras se mantiene (1 s sale de la prueba).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HoldBadge(s.bHold)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("1 s", "1 s"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = { s.testMode = false }) { Text(tr("Terminar", "Finish")) }
                }
            } else Button(onClick = { s.testedKeys.clear(); s.otherKeys.clear(); s.testMode = true }) { Text(tr("Probar botones", "Test buttons")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f)) { TriggerRow("LT", v(s.lt), s.testMode) }
            Box(Modifier.weight(1f)) { TriggerRow("RT", v(s.rt), s.testMode) }
        }
        KeyGrid(s, pressed)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyGrid(s: AppState, pressed: Set<PadKey>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TESTABLE.forEach { k ->
            val now = k in pressed
            val done = k in s.testedKeys
            val c = MaterialTheme.colorScheme
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when { now -> c.primary; done -> Color(0xFF3DDC84).copy(alpha = 0.18f); else -> c.surfaceVariant }
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (done && !now) {
                        Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color(0xFF2EB872))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        k.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = if (now) c.onPrimary else c.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerRow(label: String, v: Float, on: Boolean) {
    val c = MaterialTheme.colorScheme
    // Veinte tramos de 5 %: el de ahora lleno del acento y los que ya se alcanzaron, marcados (así se ve si el gatillo
    // llega a todos los valores o salta alguno). Se borra al terminar la prueba.
    val seen = remember { androidx.compose.runtime.mutableStateListOf<Boolean>().apply { repeat(20) { add(false) } } }
    LaunchedEffect(on) { if (!on) for (i in 0 until 20) seen[i] = false }
    LaunchedEffect(v) { if (on && v > 0.01f) seen[(v * 20).toInt().coerceIn(0, 19)] = true }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(40.dp), fontSize = 14.sp)
        Row(Modifier.weight(1f).height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until 20) {
                val now = v * 20 > i
                Box(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(when { now -> c.primary; seen[i] -> c.primary.copy(alpha = 0.3f); else -> c.onSurface.copy(alpha = 0.08f) }))
            }
        }
        Text("%3d%%".format((v * 100).toInt()), Modifier.width(52.dp), fontSize = 13.sp, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
