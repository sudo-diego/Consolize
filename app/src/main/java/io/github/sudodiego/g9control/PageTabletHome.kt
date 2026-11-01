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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SpaceDashboard
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
    // Dos líneas en la tarjeta de Inicio: Resumen (estado y accesos) y Apps abiertas. Los controles y sus atajos ya
    // están en Controles.
    Centered {
        if (LocalOnlyPanels.current?.contains(tr("Apps abiertas", "Open apps")) == true) MemoryPanel(s, a)
        else {
            ServiceWarning(s, a)
            ShizukuWarning(a)
            TabletNowTiles(a)
            QuickTiles(s, a)
        }
    }
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
    // Siempre los mismos cuatro círculos (antes Bypass y 16:9 desaparecían sin Shizuku y quedaba solo Monitor):
    // lo que no se puede usar ahora sale translúcido y dice qué le falta.
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        // Igual que en el panel: con algo automático (bypass automático o límite propio) son tres estados,
        // Automático → Encendido → Apagado → Automático.
        if (ready) {
            val hasAuto = AutoRules.smartBypass(a) || ZuiBattery.ownLimit(a) != null
            val isAuto = remember(tick, bypass) { hasAuto && !ZuiBattery.manualOverride }
            val state = when {
                isAuto -> if (bypass) ZuiBattery.autoWhy?.let { tr("Auto: ", "Auto: ") + it.first } ?: tr("Auto: activo", "Auto: on") else tr("Auto: en espera", "Auto: waiting")
                bypass -> tr("Encendido", "On")
                else -> tr("Apagado", "Off")
            }
            QuickTile(Icons.Outlined.Power, "Bypass", state, bypass, Modifier) {
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
        } else QuickTile(Icons.Outlined.Power, "Bypass", tr("Necesita Shizuku", "Needs Shizuku"), false, Modifier, enabled = false) {}
        // El monitor lo dibuja el servicio de accesibilidad: sin él no se puede encender.
        QuickTile(Icons.Outlined.QueryStats, tr("Monitor", "Monitor"), if (!service) tr("Necesita accesibilidad", "Needs accessibility") else { tick; AutoRules.hudLabel(a) },
            hud && service, Modifier, enabled = service) { AutoRules.cycleHud(a); hud = AutoRules.perfHud(a); tick++ }
        val aspectOk = Aspect.available(a)
        QuickTile(Icons.Outlined.AspectRatio, "16:9", if (!aspectOk) tr("Necesita Shizuku", "Needs Shizuku") else if (wide) tr("Encendido", "On") else tr("Apagado", "Off"),
            wide && aspectOk, Modifier, enabled = aspectOk) {
            scope.launch {
                Aspect.toggle(a)
                delay(500)
                wide = Aspect.is169(a)
            }
        }
        // El panel de juego, sin el control (lo abre el servicio).
        QuickTile(Icons.Outlined.SpaceDashboard, tr("Panel", "Panel"), if (!service) tr("Necesita accesibilidad", "Needs accessibility") else tr("Abrir", "Open"),
            false, Modifier, enabled = service) { AutoModeService.active?.openPanel() }
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
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(Modifier.width(112.dp).alpha(if (enabled) 1f else 0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, enabled = enabled, interactionSource = src, shape = androidx.compose.foundation.shape.CircleShape,
            color = androidx.compose.animation.animateColorAsState(if (on) c.primary else cardColor(), label = "q").value,
            contentColor = if (on) c.onPrimary else cardOn(),
            modifier = Modifier.size(76.dp).focusLift(focused, src, 1.08f).bump(androidx.compose.foundation.shape.CircleShape).focusHalo(focused, androidx.compose.foundation.shape.CircleShape)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = backdropOn(), maxLines = 1)
        Text(state, fontSize = 12.sp, color = if (on) c.primary else backdropMuted(), maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    // Solo los atajos principales que están encendidos, con los botones dibujados (como en Controles) y lo que
    // hacen al lado. Antes eran párrafos con todos juntos y no se entendía.
    val g = tr("Guía", "Guide")
    fun action(p: AutoRules.GuidePress) = AutoRules.guideAction(a, p).takeIf { guide && it != AutoRules.GuideAction.NONE }
    val combos = AutoRules.Chord.entries.filter { AutoRules.chordOn(a, it) }.map { c ->
        val (k1, k2) = AutoRules.chordKeys(a, c)
        val where = when (AutoRules.chordWhere(a, c)) {
            AutoRules.Where.ALL -> null
            AutoRules.Where.ONLY -> tr("En algunas apps", "In some apps")
            AutoRules.Where.EXCEPT -> tr("Menos en algunas apps", "Except some apps")
        }
        Triple(listOf(k1.label, k2.label), where, c.label)
    }
    val rows = listOfNotNull(
        action(AutoRules.GuidePress.HOLD)?.let { Triple(listOf(g), tr("Mantener", "Hold"), it.label) },
        if (on(AutoRules.Guide.RB_SHOT)) Triple(listOf(g, "RB"), null, tr("Captura", "Screenshot")) else null,
        action(AutoRules.GuidePress.TAP)?.let { Triple(listOf(g), tr("Tocar", "Tap"), it.label) },
        action(AutoRules.GuidePress.DOUBLE)?.let { Triple(listOf(g), tr("Tocar dos veces", "Double tap"), it.label) },
    ) + combos
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
internal fun MemoryPanel(s: AppState, a: MainActivity) {
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

/**
 * Inicio: la tablet de un vistazo (la batería grande y los datos del mini monitor del panel), lo que se cambia seguido
 * en mosaicos y tus Accesos (lo fijado desde Más con Y). Sin saludo ni explicaciones: solo valores.
 */
@Composable
fun HomeSummary(s: AppState, a: MainActivity) {
    // Todo centrado y en filas, como el menú de una consola: arriba la tablet de un vistazo (una barra), luego volver a
    // lo último, los interruptores redondos y tus accesos. Antes iba en dos columnas y quedaba un hueco en medio.
    // En pantallas más bajas (la Y700 con escala grande) más juntos, y si igual no cabe se desplaza: antes se cortaban
    // los accesos de abajo.
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val gap = if (maxHeight < 720.dp) 18.dp else if (maxHeight < 860.dp) 26.dp else 36.dp
    Column(Modifier.widthIn(max = 1200.dp).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
        ServiceWarning(s, a)
        ShizukuWarning(a)
        HomeHero(a)
        HomeRecents(a)
        QuickTiles(s, a)
        HomeAccess(s)
    }
    }
}

/** La batería grande ("Cargando · llena en 40 min") y, al lado, RAM, W, CPU, temperatura y Hz. */
@Composable
private fun HomeHero(a: MainActivity) {
    val c = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val b = remember(tick) { batteryNow(a) } ?: return
    val bypass = b.plugged && !b.charging && ZuiBattery.bypassNow == true && Shell.state == Shell.State.READY
    val held = b.plugged && !b.charging && !bypass
    val left = remember(tick) { if (b.charging) ZuiBattery.chargeTimeLeft(a) else null }
    val est = remember(tick) { ZuiBattery.estimatedLeft(a) }
    val sub = when {
        bypass -> "Bypass"
        held -> ZuiBattery.heldLabel(a, b.level) + tr(" · Sin cargar", " · Not charging")
        b.charging -> tr("Cargando", "Charging") + (left?.let { tr(" · llena en ", " · full in ") + ZuiBattery.formatTime(it) } ?: "")
        else -> est?.let { tr("Quedan ", "") + ZuiBattery.formatTime(it) + tr("", " left") } ?: tr("Con batería", "On battery")
    }
    val ram = remember(tick) { Memory.info(a) }
    var ramUsed by remember { mutableStateOf(Prefs.homeRamUsed(a)) }
    val w = remember(tick) { ZuiBattery.watts(a) }
    val cpu = remember(tick) { Stats.cpuMhz() }
    val hz = remember(tick) { RefreshRate.current(a) }
    // Una barra de vidrio arriba (como la de estado de una consola): la batería y los datos del mini monitor en fila.
    val shape = RoundedCornerShape(28.dp)
    Row(Modifier.bump(shape).clip(shape).background(cardColor()).padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 30.dp, height = 22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }) { BatteryGlyph(b.level, b.charging && !bypass, bypass, held, ink = cardOn(), accent = c.primary) }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${b.level} %", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = cardOn())
                Text(sub, fontSize = 11.sp, color = cardMuted(), maxLines = 1)
            }
        }
        // RAM: la usada de toda (o la libre); se cambia tocándola.
        HomeChip(Icons.Outlined.Memory,
            if (ramUsed) "%.1f / %.0f GB".format((ram.totalBytes - ram.freeBytes) / 1_073_741_824.0, ram.totalBytes / 1_073_741_824.0) else Memory.gb(ram.freeBytes),
            if (ramUsed) tr("RAM en uso", "RAM in use") else tr("RAM libre", "Free RAM"), Modifier) {
            ramUsed = !ramUsed; Prefs.setHomeRamUsed(a, ramUsed)
        }
        HomeChip(Icons.Outlined.Bolt, w?.let { (if (b.plugged) "+" else "") + "%.1f W".format(it) } ?: "—", null, Modifier, accent = b.plugged)
        HomeChip(Icons.Outlined.Speed, cpu?.let { if (it >= 1000) "%.1f GHz".format(it / 1000f) else "$it MHz" } ?: "—", "CPU", Modifier)
        HomeChip(Icons.Outlined.Thermostat, "%.0f °C".format(b.tempC), tr("Batería", "Battery"), Modifier)
        HomeChip(Icons.Outlined.DisplaySettings, hz?.let { "$it Hz" } ?: "—", tr("Pantalla", "Screen"), Modifier)
    }
}

/** Un dato de Inicio, como los del mini monitor del panel: ícono de acento, el valor y (si hace falta) qué es. */
@Composable
private fun HomeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String?, modifier: Modifier,
    accent: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Row(modifier.clip(RoundedCornerShape(12.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = c.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = if (accent) c.primary else cardOn())
            if (label != null) Text(label, fontSize = 11.sp, color = cardMuted(), maxLines = 1)
        }
    }
}

/** Accesos: lo fijado desde Más (Y), como íconos grandes con su nombre debajo; "+" lleva a Más para elegir. */
@Composable
private fun HomeAccess(s: AppState) {
    val entries = allEntries(s).associateBy { it.id }
    val pins = s.homePins.mapNotNull { entries[it] }
    var picking by remember { mutableStateOf(false) }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        pins.forEach { e -> AccessIcon(e.icon, e.label()) { s.goDest(e.id) } }
        AccessIcon(Icons.Outlined.Add, tr("Editar", "Edit"), ghost = true) { picking = true }
    }
    if (picking) AccessPicker(s) { picking = false }
}

/**
 * Elegir los accesos ahí mismo (antes "+" llevaba a Más y había que saber que Y fijaba): todo lo que hay, por tema y
 * en íconos; tocar uno lo fija o lo quita (el fijado lleva un visto del acento). B o tocar fuera cierra.
 */
@Composable
private fun AccessPicker(s: AppState, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = MaterialTheme.colorScheme
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = cardBase(), contentColor = cardOn(),
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(0.85f).fillMaxHeight(0.85f)) {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                moreGroups(s).forEach { (name, items) ->
                    Text(name.uppercase(), fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items.forEach { e ->
                            val on = e.id in s.homePins
                            Box {
                                AccessIcon(e.icon, e.label(), ghost = !on) { s.toggleHomePin(ctx, e.id) }
                                if (on) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp).size(24.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = c.onPrimary)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    PadBadge("A", round = true); Spacer(Modifier.width(6.dp)); Text(tr("Fijar o quitar", "Pin or unpin"), fontSize = 13.sp, color = cardMuted())
                    Spacer(Modifier.width(16.dp)); PadBadge("B", round = true); Spacer(Modifier.width(6.dp)); Text(tr("Listo", "Done"), fontSize = 13.sp, color = cardMuted())
                }
            }
        }
    }
}

/** Un acceso: el ícono en un cuadrado redondeado grande (crece con el foco) y su nombre debajo. */
@Composable
private fun AccessIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ghost: Boolean = false, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, interactionSource = src, shape = shape,
            color = if (ghost) androidx.compose.ui.graphics.Color.Transparent else cardColor(), contentColor = cardOn(),
            border = if (ghost) androidx.compose.foundation.BorderStroke(2.dp, backdropOn().copy(alpha = 0.3f)) else null,
            modifier = Modifier.size(88.dp).focusLift(focused, src, 1.08f).then(if (ghost) Modifier else Modifier.bump(shape)).focusHalo(focused, shape)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(38.dp), tint = if (ghost) backdropOn() else cardOn()) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = backdropOn(), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** "Editar" de los Accesos: un mosaico más (con el color de las tarjetas y el halo), que lleva a Más. */
@Composable
private fun EditTile(modifier: Modifier, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Surface(onClick = onClick, interactionSource = src, shape = RoundedCornerShape(18.dp), color = cardColor(), contentColor = cardOn(),
        modifier = modifier.height(72.dp).focusLift(focused, src, 1.03f).focusHalo(focused, RoundedCornerShape(18.dp))) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cardOn().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(tr("Editar", "Edit"), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Volver a lo último: las apps recientes (un toque y se vuelve a ellas tal como quedaron) y la última captura (abre la
 * galería). Sin permiso de fotos, la captura no sale.
 */
@Composable
private fun HomeRecents(a: MainActivity) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val recents = remember(tick) { RecentApps.list(a).filter { it != a.packageName }.take(4) }
    var shot by remember { mutableStateOf<Pair<android.net.Uri, androidx.compose.ui.graphics.ImageBitmap>?>(null) }
    LaunchedEffect(tick) { shot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { latestShot(a) } }
    if (recents.isEmpty() && shot == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Top) {
        recents.forEach { pkg ->
            val icon = remember(pkg) { RecentApps.icon(a, pkg) }
            AppIconTile(icon, RecentApps.label(a, pkg)) { RecentApps.open(a, pkg) }
        }
        shot?.let { (uri, bmp) ->
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val shape = RoundedCornerShape(20.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(onClick = {
                    runCatching { a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                }, interactionSource = src, shape = shape, color = cardColor(),
                    modifier = Modifier.size(width = 156.dp, height = 88.dp).focusLift(focused, src, 1.06f).bump(shape).focusHalo(focused, shape)) {
                    androidx.compose.foundation.Image(bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("Última captura", "Last screenshot"), fontSize = 13.sp, color = backdropOn())
            }
        }
    }
}

/** El ícono de una app en un cuadrado redondeado (como los accesos) y su nombre debajo. */
@Composable
private fun AppIconTile(icon: androidx.compose.ui.graphics.ImageBitmap?, label: String, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, interactionSource = src, shape = shape, color = cardColor(),
            modifier = Modifier.size(88.dp).focusLift(focused, src, 1.08f).bump(shape).focusHalo(focused, shape)) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = backdropOn(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** La última captura de pantalla (su dirección y una miniatura), si hay permiso de fotos. */
private fun latestShot(ctx: android.content.Context): Pair<android.net.Uri, androidx.compose.ui.graphics.ImageBitmap>? = runCatching {
    if (ctx.checkSelfPermission(AutoRules.MEDIA_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
    val col = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(android.provider.MediaStore.Images.Media._ID)
    val sel = "${android.provider.MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    ctx.contentResolver.query(col, proj, sel, arrayOf("%Screenshots%"), "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
        if (!c.moveToFirst()) return null
        val uri = android.content.ContentUris.withAppendedId(col, c.getLong(0))
        val bmp = ctx.contentResolver.loadThumbnail(uri, android.util.Size(320, 180), null)
        uri to bmp.asImageBitmap()
    }
}.getOrNull()
