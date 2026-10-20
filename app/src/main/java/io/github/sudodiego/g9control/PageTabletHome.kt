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
            ShortcutsHelp(s, a)
        },
        second = {
            MemoryPanel(s, a)
        },
        sheets = false
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
    var tick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(AutoRules.perfHud(a)) }
    var wide by remember { mutableStateOf(Aspect.is169(a)) }
    // Cada pocos segundos, como en Batería: el bypass automático lo cambia solo (al salir de un juego quedaba "Encendido").
    LaunchedEffect(ready) { while (ready) { ZuiBattery.readBypass()?.let { bypass = it }; tick++; kotlinx.coroutines.delay(3000) } }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Sin Shizuku el bypass no se puede cambiar: no sale (el aviso de arriba lo dice). Igual que en el panel: con algo
        // automático (bypass automático o límite propio) son tres estados, Automático → Encendido → Apagado → Automático.
        if (ready) {
            val hasAuto = AutoRules.smartBypass(a) || ZuiBattery.ownLimit(a) != null
            val isAuto = remember(tick, bypass) { hasAuto && !ZuiBattery.manualOverride }
            val state = when {
                isAuto -> if (bypass) ZuiBattery.autoWhy?.let { tr("Auto: ", "Auto: ") + it.first } ?: tr("Auto: activo", "Auto: on") else tr("Auto: en espera", "Auto: waiting")
                bypass -> tr("Encendido", "On")
                else -> tr("Apagado", "Off")
            }
            QuickTile(Icons.Outlined.Power, "Bypass", state, bypass, Modifier.weight(1f).fillMaxHeight()) {
                if (!isAuto && !bypass && hasAuto) {
                    AutoModeService.active?.bypassToAuto() ?: ZuiBattery.returnToAuto()
                    tick++
                } else scope.launch {
                    // Desde el automático, el primer toque siempre lo enciende (pasa a ser manual).
                    val want = if (isAuto) true else !bypass
                    if (ZuiBattery.setBypass(want)) bypass = want
                    tick++
                }
            }
        }
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

/**
 * Mosaico que se enciende y apaga, como los del panel de juego: el ícono en una insignia que toma el color de acento al
 * encenderse y el mosaico neutro (antes se llenaba entero de color). La insignia arriba: en tres por fila, al lado no cabía.
 */
@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, state: String, on: Boolean, modifier: Modifier,
    enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.alpha(if (enabled) 1f else 0.5f), shape = MaterialTheme.shapes.large,
        color = c.surfaceContainer.copy(alpha = if (ThemeState.glass) 0.58f else 1f),
        border = if (ThemeState.glass) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)) else null
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(androidx.compose.animation.animateColorAsState(if (on) c.primary else c.onSurface.copy(alpha = 0.08f), label = "badge").value,
                RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (on) c.onPrimary else c.onSurface)
            }
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(state, fontSize = 13.sp, color = if (on) c.primary else c.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
/**
 * Controles conectados ahora, de cualquier marca. El G9 con su modo (y el acceso a su sección); los demás solo el
 * nombre: el panel de juego, las capturas con L3 + R3 y el monitor sirven con cualquiera.
 */
@Composable
private fun ConnectedPads(s: AppState) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val pads = remember(tick) {
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isExternal && (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD }
            .map { it.name }.distinct()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    // Solo los atajos principales que están encendidos, con los botones dibujados (como en Controles → Atajos) y lo que
    // hacen al lado. Antes eran párrafos con todos juntos y no se entendía.
    val g = tr("Guía", "Guide")
    val rows = listOfNotNull(
        if (on(AutoRules.Guide.HOLD_PANEL)) Triple(listOf(g), tr("Mantener", "Hold"), tr("Panel de juego", "Game panel")) else null,
        if (on(AutoRules.Guide.RB_SHOT)) Triple(listOf(g, "RB"), null, tr("Captura", "Screenshot")) else null,
        if (on(AutoRules.Guide.TAP_BACK)) Triple(listOf(g), tr("Tocar", "Tap"), tr("Atrás", "Back")) else null,
        if (chord) Triple(listOf("L3", "R3"), if (AutoRules.chordOnlyStreaming(a)) tr("Transmitiendo", "While streaming") else null, tr("Panel de juego", "Game panel")) else null,
    )
    // Una tarjeta: qué control está conectado y, debajo, sus atajos (separadas, la de controles quedaba casi vacía).
    Panel(tr("Controles", "Controllers"), trailing = { TextButton(onClick = { s.open(Page.CONTROLS) }) { Text(tr("Cambiar atajos", "Change shortcuts")) } }) {
        ConnectedPads(s)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        if (!s.autoServiceOn) Text(tr("Apagados: falta el servicio de accesibilidad.", "Off: the accessibility service is missing."),
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (rows.isEmpty()) Text(tr("Ninguno activado.", "None turned on."), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else rows.forEach { (keys, how, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCombo(keys, Modifier.widthIn(min = 120.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(action, fontSize = 15.sp)
                    how?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
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