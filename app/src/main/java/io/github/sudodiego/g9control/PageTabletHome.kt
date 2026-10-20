package io.github.sudodiego.g9control

import android.view.InputDevice
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Inicio de la sección Tablet: para cualquiera con la Y700, tenga o no el G9. Arriba los avisos y cómo está la tablet
 * en mosaicos grandes; abajo lo que más se cambia, también en mosaicos (como el panel de juego). A la derecha los
 * controles conectados y los atajos.
 */
@Composable
fun TabletHomePage(s: AppState, a: MainActivity) {
    TwoPane(
        // Izquierda: cómo está el equipo y lo que más se cambia; derecha: memoria, controles y atajos (antes la izquierda
        // tenía cuatro mosaicos sueltos y mucho espacio vacío debajo).
        first = {
            ServiceWarning(s, a)
            ShizukuWarning(a)
            TabletNowTiles(a)
            QuickTiles(s, a)
            ControllersPanel(s)
            ShortcutsHelp(s, a)
        },
        second = {
            MemoryPanel(s, a)
        }
    )
}

/** La tablet ahora, en mosaicos: batería (con el tiempo de carga), temperatura y Hz. Se actualiza cada pocos segundos. */
@Composable
private fun TabletNowTiles(a: MainActivity) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val b = remember(tick) { batteryNow(a) }
    val hz = remember(tick) { RefreshRate.current(a) }
    val monitor = remember(tick) { Monitor.current(a) }
    val left = remember(tick) { if (b?.charging == true) ZuiBattery.chargeTimeLeft(a) else null }
    // Una tarjeta: la batería grande arriba y temperatura, Hz y CPU en una fila debajo.
    val cpu = remember(tick) { cpuMhz() }
    Panel {
            b?.let {
                // Las mismas palabras e íconos que el panel de juego (StatChip del mini monitor): bypass y "sin cargar" con
                // el enchufe, cargando con el rayo.
                val bypass = it.plugged && !it.charging && ZuiBattery.bypassNow == true && Shell.state == Shell.State.READY
                val held = it.plugged && !it.charging && !bypass
                val state = when {
                    bypass -> tr("Bypass · Activo", "Bypass · On")
                    held -> ZuiBattery.heldLabel(a, it.level) + tr(" · Sin cargar", " · Not charging")
                    it.charging -> tr("Cargando", "Charging")
                    else -> tr("Con batería", "On battery")
                }
                val stateIcon = if (bypass || held) Icons.Outlined.Power else if (it.charging) Icons.Outlined.Bolt else null
                // Debajo: lo que consume y cuánto dura, o cargando cuánto falta (hasta el límite, si hay) y lo que entra.
                val w = remember(tick) { ZuiBattery.watts(a) }
                val extra = when {
                    it.plugged && !it.charging -> null
                    it.charging -> listOfNotNull(left?.let { ms -> ZuiBattery.formatTime(ms) + ZuiBattery.chargeTarget(a).let { t -> if (t < 100) tr(" a $t %", " to $t %") else "" } },
                        w?.let { x -> "+%.1f W".format(x) }).joinToString(" · ")
                    // A batería: lo de ahora, el promedio del último minuto (si el servicio mide) y cuánto dura a ese ritmo.
                    else -> listOfNotNull(w?.let { x -> "%.1f W".format(x) }, PowerLog.recentAvg?.let { x -> tr("prom. %.1f W", "avg %.1f W").format(x) },
                        remember(tick) { ZuiBattery.estimatedLeft(a) }?.let { ms -> ZuiBattery.formatTime(ms) }).joinToString(" · ")
                }?.takeIf { s -> s.isNotEmpty() }
                // Qué es el número: "Batería" con el mismo ícono de batería que el encabezado del panel.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryGlyph(it.level, it.charging, bypass, held)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Batería", "Battery"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${it.level}", fontSize = 56.sp, fontWeight = FontWeight.SemiBold, lineHeight = 56.sp)
                    Text(" %", fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            stateIcon?.let { ic -> Icon(ic, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                            Text(state, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        extra?.let { x -> Text(x, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("${"%.0f".format(it.tempC)} °C", tr("Temperatura", "Temperature"), Modifier.weight(1f))
                    Metric(hz?.let { h -> "$h Hz" } ?: "—", monitor?.name ?: tr("Pantalla", "Screen"), Modifier.weight(1f))
                    Metric(cpu?.let { c -> if (c >= 1000) "%.1f GHz".format(c / 1000f) else "$c MHz" } ?: "—", "CPU", Modifier.weight(1f))
                }
            }
    }
}

/** Un dato chico de la tarjeta de Inicio: el valor y su etiqueta debajo. */
@Composable
private fun Metric(value: String, label: String, modifier: Modifier) = Column(modifier) {
    Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}
/** La frecuencia más alta de la CPU ahora (MHz), de los núcleos que se pueden leer; null si ninguno. */
private fun cpuMhz(): Int? = Stats.cpuMhz()

/** Un dato grande con su etiqueta debajo, en una tarjeta de vidrio. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, big: Boolean = false, extra: String? = null) {
    Panel(modifier = modifier) {
        // Una sola línea, sin cortar: el valor (y su unidad) no se parten aunque el mosaico sea angosto.
        Text(value, fontSize = if (big) 38.sp else 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        extra?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1) }
    }
}

/** Lo que más se cambia, a un toque y en mosaicos: bypass, monitor de rendimiento y pantalla 16:9. */
@Composable
private fun QuickTiles(s: AppState, a: MainActivity) {
    val service = s.autoServiceOn
    val scope = rememberCoroutineScope()
    val ready = Shell.state == Shell.State.READY
    var bypass by remember { mutableStateOf(ZuiBattery.bypassNow == true) }
    var hud by remember { mutableStateOf(AutoRules.perfHud(a)) }
    var wide by remember { mutableStateOf(Aspect.is169(a)) }
    // Cada pocos segundos, como en Batería: el bypass automático lo cambia solo (al salir de un juego quedaba "Encendido").
    LaunchedEffect(ready) { while (ready) { ZuiBattery.readBypass()?.let { bypass = it }; kotlinx.coroutines.delay(3000) } }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Sin Shizuku el bypass no se puede cambiar: no sale (el aviso de arriba lo dice).
        if (ready) QuickTile(Icons.Outlined.Power, "Bypass", if (bypass) tr("Encendido", "On") else tr("Apagado", "Off"), bypass,
            Modifier.weight(1f).fillMaxHeight()) { scope.launch { if (ZuiBattery.setBypass(!bypass)) bypass = !bypass } }
        // El monitor lo dibuja el servicio de accesibilidad: sin él no se puede encender.
        QuickTile(Icons.Outlined.QueryStats, tr("Monitor", "Monitor"), if (!service) tr("Necesita accesibilidad", "Needs accessibility") else if (hud) tr("Encendido", "On") else tr("Apagado", "Off"),
            hud && service, Modifier.weight(1f).fillMaxHeight(), enabled = service) { hud = !hud; AutoRules.setPerfHud(a, hud) }
        if (Aspect.available(a)) QuickTile(Icons.Outlined.AspectRatio, "16:9", if (wide) tr("Encendido", "On") else tr("Apagado", "Off"), wide,
            Modifier.weight(1f).fillMaxHeight()) {
            scope.launch {
                Aspect.toggle(a)
                delay(500)
                wide = Aspect.is169(a)
            }
        }
    }
}

/** Mosaico que se enciende y apaga, como los del panel de juego: encendido en celeste. */
@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, state: String, on: Boolean, modifier: Modifier,
    enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.alpha(if (enabled) 1f else 0.5f), shape = MaterialTheme.shapes.large,
        // Encendido con el color de acento (como lo elegido en toda la app); sin interruptor adentro: el mosaico entero se
        // toca y su color dice cómo está.
        color = androidx.compose.animation.animateColorAsState(if (on) c.primary else c.surfaceContainer.copy(alpha = if (ThemeState.glass) 0.58f else 1f), label = "tile").value,
        border = if (ThemeState.glass && !on) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)) else null
    ) {
        // Ícono arriba y el texto debajo: al lado, en tres mosaicos por fila, "Encendido" se cortaba.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (on) c.onPrimary else c.onSurface)
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.onPrimary else c.onSurface, maxLines = 1)
                Text(state, fontSize = 13.sp, color = if (on) c.onPrimary.copy(alpha = 0.85f) else c.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * Controles conectados ahora, de cualquier marca. El G9 con su modo (y el acceso a su sección); los demás solo el
 * nombre: el panel de juego, las capturas con L3 + R3 y el monitor sirven con cualquiera.
 */
@Composable
private fun ControllersPanel(s: AppState) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val pads = remember(tick) {
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isExternal && (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD }
            .map { it.name }.distinct()
    }
    Panel(tr("Controles", "Controllers")) {
        if (pads.isEmpty()) Text(tr("No hay controles conectados.", "No controllers connected."), fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        pads.forEach { name ->
            val g9 = name.contains("G9", true) || name.contains("x-box 360", true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (g9) "Legion G9" else name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (g9) Text(s.padMode?.label?.let { tr("Modo $it", "$it mode") } ?: "", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (g9) TextButton(onClick = { s.g9Known = true; s.open(Page.HOME) }) { Text(tr("Configurar", "Set up")) }
            }
        }
    }
}

/** Los atajos que están activados ahora, en corto; todos (y sus interruptores) en Controles. */
@Composable
private fun ShortcutsHelp(s: AppState, a: MainActivity) {
    val guide = AutoRules.legionKeys(a)
    fun on(g: AutoRules.Guide) = guide && AutoRules.guide(a, g)
    val chord = AutoRules.gamePanel(a)
    val panel = listOfNotNull(if (on(AutoRules.Guide.HOLD_PANEL)) tr("Mantener guía", "Hold guide") else null, if (chord) (if (AutoRules.chordOnlyStreaming(a)) tr("L3 + R3 transmitiendo", "L3 + R3 while streaming") else "L3 + R3") else null)
    val shot = listOfNotNull(if (on(AutoRules.Guide.RB_SHOT)) tr("Guía + RB", "Guide + RB") else null, if (chord) tr("mantener L3 + R3", "hold L3 + R3") else null,
        if (AutoRules.touchpadShot(a)) tr("M dos veces (DS5)", "M twice (DS5)") else null)
    val back = listOfNotNull(if (on(AutoRules.Guide.TAP_BACK)) tr("Tocar guía", "Tap guide") else null,
        if (on(AutoRules.Guide.LB_BACK)) tr("Guía + LB (mantenido)", "Guide + LB (held)") else null)
    val lock = on(AutoRules.Guide.HOLD_LOCK)
    val streamMenu = AutoRules.streamBack(a)
    Panel(tr("Atajos", "Shortcuts"), trailing = { TextButton(onClick = { s.open(Page.CONTROLS) }) { Text(tr("Cambiar", "Change")) } }) {
        if (!s.autoServiceOn) Text(tr("Apagados: falta el servicio de accesibilidad.", "Off: the accessibility service is missing."),
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (panel.isEmpty() && shot.isEmpty() && back.isEmpty()) Text(tr("Ninguno activado.", "None turned on."),
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            if (panel.isNotEmpty()) HelpRow(tr("Panel de juego", "Game panel"), panel.joinToString(" · "))
            if (shot.isNotEmpty()) HelpRow(tr("Captura", "Screenshot"), shot.joinToString(" · "))
            if (back.isNotEmpty()) HelpRow(tr("Atrás", "Back"), back.joinToString(" · "))
            HelpRow(tr("Grabar", "Record"), tr("Guía + LT: empezar o parar · Guía + RT: guardar los últimos segundos (Shadow Rec)", "Guide + LT: start or stop · Guide + RT: save the last seconds (Shadow Rec)"))
            if (lock) HelpRow(tr("Bloquear la tablet", "Lock the tablet"), tr("Mantener guía 5 s (el control se apaga)", "Hold guide 5 s (the controller turns off)"))
            if (streamMenu) HelpRow(tr("Menú al transmitir", "Menu while streaming"), "Start + Select")
        }
    }
}

@Composable
private fun HelpRow(title: String, desc: String) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Apps abiertas, como en una consola: cuánta RAM está en uso, cerrar lo que quedó de fondo antes de jugar y, con
 * Shizuku, cada app con lo que gasta en palabras ("655 MB de RAM", "usando CPU") para cerrarla. Antes era una tabla
 * con "MB" y "%" sin títulos y un historial de "Vuelven solas": no se entendía.
 */
@Composable
private fun MemoryPanel(s: AppState, a: MainActivity) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val info = remember(tick) { Memory.info(a) }
    val ready = Shell.state == Shell.State.READY
    var tasks by remember { mutableStateOf(listOf<Memory.Task>()) }
    LaunchedEffect(ready, tick / 2) { if (ready) tasks = Memory.tasks(a) }
    var busy by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Panel(tr("Apps abiertas", "Open apps")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("RAM en uso", "RAM in use"), fontSize = 13.sp, color = c.onSurfaceVariant)
                Text(tr("${Memory.gb(info.totalBytes - info.freeBytes)} de ${Memory.gb(info.totalBytes)}", "${Memory.gb(info.totalBytes - info.freeBytes)} of ${Memory.gb(info.totalBytes)}"),
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            // Cierra lo que quedó en segundo plano; lo activo (un video sonando, una descarga) sigue.
            Button(onClick = {
                busy = true
                scope.launch {
                    val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Memory.freeAll(a) }
                    busy = false; tick++
                    s.status = tr("Se liberaron ${Memory.gb(freed)}.", "Freed ${Memory.gb(freed)}.")
                }
            }, enabled = !busy) { Text(if (busy) tr("Cerrando…", "Closing…") else tr("Cerrar las de fondo", "Close background apps")) }
        }
        val used = 1f - info.freeBytes.toFloat() / info.totalBytes.coerceAtLeast(1)
        LinearProgressIndicator({ used }, Modifier.fillMaxWidth().height(6.dp), color = c.primary, trackColor = c.onSurface.copy(alpha = 0.08f))
        if (!ready) Hint(tr("Para ver y cerrar cada app hace falta Shizuku.", "Seeing and closing each app needs Shizuku."))
        else if (tasks.isEmpty()) Text(tr("Leyendo…", "Reading…"), fontSize = 14.sp, color = c.onSurfaceVariant)
        else tasks.take(8).forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                task.app.icon?.let { d ->
                    androidx.compose.foundation.Image(remember(task.app.pkg) { androidx.compose.ui.graphics.painter.BitmapPainter(d.toBitmapSafe().asImageBitmap()) },
                        null, Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(task.app.label, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    // Lo que gasta, en palabras. La CPU solo si de verdad está trabajando (casi siempre es 0,0 %).
                    val detail = listOfNotNull(
                        tr("${task.ramMb} MB de RAM", "${task.ramMb} MB of RAM"),
                        if (task.cpu >= 5) tr("usando CPU", "using CPU") else null,
                        if (task.active) tr("en uso", "in use") else null,
                        if (Memory.returnOf(a, task.app.pkg) != null) tr("se abre sola", "reopens by itself") else null
                    ).joinToString(" · ")
                    Text(detail, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    tasks = tasks - task
                    scope.launch { Memory.stop(a, task.app.pkg); tick++ }
                }) { Text(tr("Cerrar", "Close")) }
            }
        }
    }
}
/** Ícono de app como bitmap (los adaptativos no son BitmapDrawable). */
private fun android.graphics.drawable.Drawable.toBitmapSafe(): android.graphics.Bitmap {
    (this as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { return it }
    val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
    setBounds(0, 0, 96, 96); draw(android.graphics.Canvas(b))
    return b
}