package io.github.sudodiego.g9control

import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Página "Tablet": lo que es de la tablet y no del control. A la izquierda la batería (bypass manual y automático) y
 * la pantalla y los botones; a la derecha Game Assistant (rendimiento, ocultarlo, Shizuku).
 */
/** Batería: bypass, automático y límite de carga; a la derecha cómo está ahora. */
@Composable
fun BatteryPage(s: AppState, a: MainActivity) {
    TwoPane(
        first = {
            BatteryPanel(s, a)
            Gate(s.autoServiceOn) { LowBatteryPanel(a) }
            ShizukuHint()
        },
        // Consumo por app y sesiones: en Sistema → Uso, junto a las apps abiertas.
        second = { BatteryNowPanel(a) }
    )
}

/**
 * Sesiones de juego: por modo de rendimiento (W, CPU y temperatura) y los últimos tramos. Para comparar: el mismo
 * juego un rato en cada modo, desenchufada.
 */
@Composable
internal fun SessionsPanel(a: MainActivity) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); tick++ } }
    val modes = remember(tick) { Sessions.byMode(a) }
    val list = remember(tick) { Sessions.list(a).take(8) }
    val c = MaterialTheme.colorScheme
    fun modeLabel(m: String?) = m?.let { runCatching { ZuiGame.PerfMode.valueOf(it).label }.getOrNull() } ?: tr("Modo desconocido", "Unknown mode")
    Panel(tr("Sesiones de juego", "Game sessions"), trailing = {
        if (list.isNotEmpty()) ConfirmButton(tr("Reiniciar", "Reset")) { Sessions.reset(a); tick++ }
    }) {
        if (list.isEmpty()) Hint(tr("Se anotan solas al jugar.", "Logged by themselves while playing."))
        if (modes.isNotEmpty()) {
            Text(tr("Por modo", "By mode"), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            modes.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(modeLabel(m.mode), Modifier.weight(1f), fontSize = 14.sp)
                    Text(listOfNotNull(m.avgWatts?.let { "%.1f W".format(it) }, m.avgCpuMhz?.let { Stats.cpuText(it) },
                        m.avgCpuMax?.let { "$it °C" }, ZuiBattery.formatTime(m.seconds * 1000L)).joinToString(" · "),
                        fontSize = 13.sp, color = c.onSurfaceVariant)
                }
            }
        }
        if (list.isNotEmpty()) {
            Text(tr("Últimas", "Latest"), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            // Las de juegos desinstalados no salen (mostraban el nombre del paquete).
            list.mapNotNull { s -> appLabel(a, s.pkg)?.let { s to it } }.forEach { (s, name) ->
                Column {
                    Text(name + " · " + modeLabel(s.mode) + (s.hz?.let { " · $it Hz" } ?: ""), fontSize = 14.sp, maxLines = 1)
                    Text(listOfNotNull(ZuiBattery.formatTime(s.seconds * 1000L), s.avgWatts?.let { "%.1f W".format(it) },
                        s.avgCpuMhz?.let { "CPU " + Stats.cpuText(it) }, s.cpuMax?.let { "CPU $it °C" }, s.gpuMax?.let { "GPU $it °C" },
                        s.batMax?.let { tr("bat. $it °C", "bat. $it °C") }).joinToString(" · "), fontSize = 12.sp, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Consumo por app: lo que gastó en promedio cada una a batería con la pantalla encendida (lo mide el servicio cada
 * 5 s). Sirve para saber más o menos cuánto dura la batería con cada juego o emulador.
 */
@Composable
internal fun PowerByAppPanel(a: MainActivity) {
    var apps by remember { mutableStateOf(PowerLog.apps(a)) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); apps = PowerLog.apps(a) } }
    val c = MaterialTheme.colorScheme
    Panel(tr("Consumo por app", "Draw per app"), trailing = {
        if (apps.isNotEmpty()) ConfirmButton(tr("Reiniciar", "Reset")) { PowerLog.reset(a); apps = emptyList() }
    }) {
        if (apps.isEmpty()) Hint(tr("Se mide sin cargador.", "Measured unplugged."))
        val full = remember { ZuiBattery.fullWh(a) }
        // Solo las instaladas: una desinstalada salía con el nombre del paquete.
        apps.mapNotNull { u -> appLabel(a, u.pkg)?.let { u to it } }.take(10).forEach { (u, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 15.sp, maxLines = 1)
                    Text(tr("medido ", "measured ") + ZuiBattery.formatTime(u.seconds * 1000), fontSize = 12.sp, color = c.onSurfaceVariant)
                }
                Text("%.1f W".format(u.avgWatts), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                // Cuánto duraría la batería llena solo con esa app.
                full?.takeIf { u.avgWatts > 0.3 }?.let { wh ->
                    Text(ZuiBattery.formatTime((wh / u.avgWatts * 3_600_000).toLong()), Modifier.width(110.dp), fontSize = 14.sp,
                        color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

/** Pantalla: 16:9, volumen invertido y el monitor externo. */
@Composable
fun ScreenPage(s: AppState, a: MainActivity) {
    // Una sola hoja centrada (dos columnas dejaban una con un solo interruptor). 16:9 a mano: permiso de pantalla o
    // Shizuku; el monitor lo aplica el servicio de accesibilidad.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Sheet(Modifier.widthIn(max = 900.dp)) {
            Gate(Aspect.available(a)) { ScreenButtonsPanel(s, a) }
            Gate(s.autoServiceOn) { MonitorPanel(s, a) }
            ShizukuHint()
        }
    }
}

/** Sin Shizuku lo que lo necesita se ve atenuado; su estado y cómo iniciarlo están en Ajustes → Permisos. */
@Composable
private fun ShizukuHint() {
    if (Shell.state != Shell.State.READY) Hint(tr("Lo atenuado necesita Shizuku (Ajustes → Permisos).", "What's dimmed needs Shizuku (Settings → Permissions)."))
}

/**
 * La batería ahora: nivel, estado, temperatura, potencia y tiempo, y ciclos. Se actualiza cada pocos segundos (sin
 * permisos: `BatteryManager`).
 */
@Composable
fun BatteryNowPanel(a: MainActivity) {
    var now by remember { mutableStateOf(batteryNow(a)) }
    var watts by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = batteryNow(a)
            watts = ZuiBattery.watts(a)
            delay(3000)
        }
    }
    val b = now ?: return
    Panel(tr("Ahora", "Now")) {
        Text("${b.level} %", fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        val state = when {
            b.plugged && !b.charging -> if (ZuiBattery.bypassNow == true && Shell.state == Shell.State.READY) "Bypass" else tr("Enchufada, sin cargar", "Plugged in, not charging") + " · " + ZuiBattery.heldLabel(a, b.level)
            b.charging -> tr("Cargando", "Charging")
            else -> tr("Con batería", "On battery")
        }
        InfoRow(tr("Estado", "State"), state)
        InfoRow(tr("Temperatura", "Temperature"), "%.1f °C".format(b.tempC))
        if (!(b.plugged && !b.charging)) watts?.let { InfoRow(if (b.charging) tr("Entra", "Charging at") else tr("Consumo", "Draw"), "%.1f W".format(it)) }
        if (!b.plugged) PowerLog.recentAvg?.let { InfoRow(tr("Promedio del último minuto", "Last minute average"), "%.1f W".format(it)) }
        if (b.cycles >= 0) InfoRow(tr("Ciclos de carga", "Charge cycles"), "${b.cycles}")
    }
}

/** Aviso de batería baja como la Switch (lo muestra el servicio): cuándo, con sonido, encima del toast de ZUI. */
@Composable
private fun LowBatteryPanel(a: MainActivity) {
    Panel(tr("Avisos de batería", "Battery notices")) {
        var charge by remember { mutableStateOf(AutoRules.chargeNotice(a)) }
        SwitchRow(tr("Al enchufar", "When plugged in"), charge) {
            charge = it; AutoRules.setChargeNotice(a, it)
        }
        var chargeSound by remember { mutableStateOf(AutoRules.chargeSound(a)) }
        SwitchRow(tr("Sonido al enchufar", "Sound when plugged in"), chargeSound) { chargeSound = it; AutoRules.setChargeSound(a, it) }
        var low by remember { mutableStateOf(AutoRules.lowBattery(a)) }
        SwitchRow(tr("Batería baja", "Low battery"), low, tr("A 15, 10 y 5 %", "At 15, 10 and 5 %")) {
            low = it; AutoRules.setLowBattery(a, it)
        }
        var lowSound by remember { mutableStateOf(AutoRules.lowBatterySound(a)) }
        SwitchRow(tr("Con sonido", "With sound"), lowSound) { lowSound = it; AutoRules.setLowBatterySound(a, it) }
        var overToast by remember { mutableStateOf(AutoRules.lowBatteryOverToast(a)) }
        SwitchRow(tr("Tapar el aviso de Android", "Cover Android's notice"), overToast) {
            overToast = it; AutoRules.setLowBatteryOverToast(a, it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { AutoRules.preview(a, "charge") }) { Text(tr("Ver al enchufar", "Show plugged in")) }
            TextButton(onClick = { AutoRules.preview(a, "battery") }) { Text(tr("Ver batería baja", "Show low battery")) }
            // El aviso del sistema (notificación de SystemUI, canal BAT): una app no lo puede apagar, pero sí abrir su ajuste.
            TextButton(onClick = {
                runCatching {
                    a.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, "com.android.systemui")
                        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "BAT"))
                }
            }) { Text(tr("Quitar el aviso del sistema", "Turn off the system notice")) }
        }
    }
}

@Composable
internal fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontSize = 14.sp)
    }
}

/** Ajuste de Game Assistant: 1 = apaga el brillo automático en los juegos (y lo devuelve al salir). */
private const val GA_NO_AUTO_BRIGHTNESS = "key_game_assistant_disable_auto_brightness"

/** Estado de la batería ahora (de ACTION_BATTERY_CHANGED, sin permisos). */
data class BatteryNow(val level: Int, val tempC: Float, val plugged: Boolean, val charging: Boolean, val cycles: Int)

fun batteryNow(a: MainActivity): BatteryNow? {
    val i = a.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
    return BatteryNow(
        level, i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f, i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        if (android.os.Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) else -1
    )
}

/**
 * Batería: estado, bypass manual (el cargador alimenta la tablet y la batería no carga) y bypass automático con los
 * rangos que se elijan. El bypass va con Shizuku (el servicio de batería de ZUI solo acepta apps del sistema).
 */
@Composable
private fun BatteryPanel(s: AppState, a: MainActivity) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(batteryNow(a)) }
    var bypass by remember { mutableStateOf(ZuiBattery.bypassNow) }
    var smart by remember { mutableStateOf(AutoRules.smartBypass(a)) }
    var level by remember { mutableIntStateOf(AutoRules.smartBypassLevel(a)) }
    var temp by remember { mutableIntStateOf(AutoRules.smartBypassTemp(a)) }
    var games by remember { mutableStateOf(AutoRules.smartBypassGames(a)) }
    var any by remember { mutableStateOf(AutoRules.smartBypassAny(a)) }
    var limit by remember { mutableStateOf<Int?>(null) }
    var own by remember { mutableIntStateOf(ZuiBattery.ownLimit(a) ?: 0) }
    val ready = Shell.state == Shell.State.READY
    // Se refresca cada pocos segundos mientras la página está abierta.
    LaunchedEffect(ready) {
        while (true) {
            now = batteryNow(a)
            if (ready) {
                ZuiBattery.readBypass()?.let { bypass = it }
                limit = ZuiBattery.readChargeLimit()
            }
            delay(3000)
        }
    }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Batería", "Battery"), trailing = {
            now?.let { b ->
                // Enchufada y sin cargar: el bypass, el límite de carga de ZUI o algo más (la batería decide).
                val state = when {
                    bypass == true && b.plugged -> "Bypass"
                    b.charging -> tr("Cargando", "Charging")
                    b.plugged && limit != null -> tr("Límite $limit %", "Limit $limit %")
                    b.plugged -> tr("No carga", "Not charging")
                    else -> tr("Sin cargador", "Unplugged")
                }
                Text("${b.level} % · ${"%.0f".format(b.tempC)} °C · $state", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }) {
            // Lo que no se puede usar se ve oscurecido: el bypass a mano necesita Shizuku; el límite y el automático,
            // además, el servicio de accesibilidad (que los vigila).
            Gate(ready && s.autoServiceOn) {
                // Límite propio: al llegar, bypass (la batería deja de cargar). El de ZUI corta siempre en 80 %.
                Text(tr("Límite de carga", "Charge limit"), fontSize = 14.sp)
                ChoiceChips(listOf(0 to tr("Apagado", "Off")) + ZuiBattery.LIMITS.map { it to "$it %" }, own) { lv ->
                    own = lv; ZuiBattery.setOwnLimit(a, lv.takeIf { it > 0 })
                }
                // Sin explicar el de ZUI: las opciones dicen solas dónde corta. Solo el aviso si el del sistema no deja llegar.
                if (own > 80 && limit != null) Hint(tr("El límite del sistema corta antes ($limit %).", "The system limit stops earlier ($limit %)."))
            }
            Gate(ready) {
                SwitchRow(
                    tr("Bypass", "Bypass"), bypass == true, null
                ) { want ->
                    if (ready) scope.launch { if (ZuiBattery.setBypass(want)) bypass = want }
                }
            }
            Gate(ready && s.autoServiceOn) {
                // Automático: un resumen de cuándo se activa, que cambia con lo que se elija.
                val conds = tr("desde $level %", "from $level %") +
                    if (temp > 0) (if (any) tr(" o ", " or ") else tr(" y ", " and ")) + tr("desde $temp °C", "from $temp °C") else ""
                val summary = listOfNotNull(
                    tr("Enchufada", "Plugged in"),
                    if (games) tr("en un juego", "in a game") else null,
                    conds
                ).joinToString(" · ")
                SwitchRow(tr("Bypass automático", "Automatic bypass"), smart, if (smart) summary else null) {
                    smart = it; AutoRules.setSmartBypass(a, it)
                    // Que no peleen: el inteligente de Game Assistant queda en Manual (se puede con WRITE_SECURE_SETTINGS).
                    if (it && Aspect.canWrite(a)) runCatching { Settings.Global.putInt(a.contentResolver, "intelligent_bypass_charging_value", 0) }
                }
                if (smart) {
                    Text(tr("Cuándo", "When"), fontSize = 14.sp)
                    ChoiceChips(listOf(true to tr("En juegos", "In games"), false to tr("Siempre", "Always")), games) { games = it; AutoRules.setSmartBypassGames(a, it) }
                    // Libre, pero se pega en 20, 40, 60 y 80 (los de antes). Por qué esos: ver el texto de abajo.
                    SliderRow(tr("Batería desde", "Battery from"), level, 10..90, "$level %", snaps = listOf(20, 40, 60, 80)) {
                        level = it; AutoRules.setSmartBypassLevel(a, it)
                    }
                    // 30 en la barra = "Cualquiera" (se guarda 0). Se pega en 35, 38 y 41 °C.
                    SliderRow(tr("Temperatura de la batería desde", "Battery temperature from"), if (temp == 0) 30 else temp, 30..45,
                        if (temp == 0) tr("Cualquiera", "Any") else "$temp °C", snaps = listOf(35, 38, 41), snapPull = 1) {
                        temp = if (it <= 30) 0 else it; AutoRules.setSmartBypassTemp(a, temp)
                    }
                    if (temp > 0) {
                        Text(tr("Se activa con", "Turns on with"), fontSize = 14.sp)
                        ChoiceChips(listOf(false to tr("Las dos", "Both"), true to tr("Cualquiera", "Either")), any) { any = it; AutoRules.setSmartBypassAny(a, it) }
                    }
                }
            }
            OutlinedButton(onClick = { runCatching { a.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } }) {
                Text(tr("Optimización de carga (Ajustes)", "Charge optimization (Settings)"))
            }
        }
    }
}

/**
 * Monitor externo en modo espejo: 16:9 al conectarlo (para todos, y cada monitor puede tener lo suyo), qué hace la
 * pantalla de la tablet mientras tanto, y la lista de los que se han conectado. Lo aplica el servicio de accesibilidad.
 */
@Composable
private fun MonitorPanel(s: AppState, a: MainActivity) {
    var monitor by remember { mutableStateOf(Monitor.current(a)) }
    var mirror by remember { mutableStateOf(Monitor.mirror(a)) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { monitor = Monitor.current(a); mirror = Monitor.mirror(a); tick++; delay(2000) } }
    var all169 by remember { mutableStateOf(Monitor.all169(a)) }
    var screen by remember { mutableStateOf(Monitor.screen(a)) }
    val known = remember(tick) { Monitor.known(a) }
    val ready = Shell.state == Shell.State.READY
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Pantalla externa", "External display"), trailing = {
            val m = monitor
            Text(
                when {
                    m == null -> tr("Sin conectar", "Not connected")
                    mirror -> "${m.name} · " + tr("espejo", "mirror")
                    else -> "${m.name} · " + tr("extendido", "extended")
                }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }) {
            if (Aspect.available(a)) {
                SwitchRow(
                    tr("16:9 en cualquier monitor", "16:9 on any monitor"), all169, null
                ) { all169 = it; Monitor.setAll169(a, it) }
            }
            Text(tr("Esta pantalla, con el monitor", "This screen, with the monitor"), fontSize = 14.sp)
            ChoiceChips(
                listOf(Monitor.Screen.NORMAL to tr("Normal", "Normal"), Monitor.Screen.DIM to tr("Oscura", "Dim"),
                    Monitor.Screen.NO_LIGHT to tr("Sin luz", "No light"), Monitor.Screen.OFF to tr("Apagada", "Off")), screen
            ) { screen = it; Monitor.setScreen(a, it) }
            // Encendida con el botón de encendido: volver a oscurecerla sin control.
            if (screen != Monitor.Screen.NORMAL && monitor != null && mirror && remember(tick) { Monitor.woken }) {
                OutlinedButton(onClick = { AutoRules.darkenTablet(a); tick++ }) { Text(tr("Oscurecer ahora", "Darken now")) }
            }
            if (screen >= Monitor.Screen.NO_LIGHT && !ready) Hint(tr("Sin Shizuku solo se oscurece.", "Without Shizuku it's only dimmed."))
            if (screen != Monitor.Screen.NORMAL) Hint(tr("Si algo falla: encendido dos veces la enciende.", "If anything fails: press power twice to turn it on."))
            if (known.isNotEmpty() && Aspect.available(a)) {
                Text(tr("Conocidas", "Known"), fontSize = 14.sp)
                known.forEach { k ->
                    var fit by remember(k.key, tick) { mutableStateOf(Monitor.fit(a, k.key)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            k.name + if (k.key == monitor?.key) tr(" (conectado)", " (connected)") else "",
                            Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ConfirmButton(tr("Olvidar", "Forget")) { Monitor.forget(a, k.key); tick++ }
                    }
                    ChoiceChips(
                        listOf(Monitor.Fit.ALL to (if (all169) tr("Como todos (16:9)", "Like all (16:9)") else tr("Como todos (16:10)", "Like all (16:10)")),
                            Monitor.Fit.WIDE to "16:9", Monitor.Fit.NATIVE to "16:10"), fit
                    ) { fit = it; Monitor.setFit(a, k.key, it) }
                }
            }
        }
    }
}

/** Pantalla 16:9 y volumen invertido. */
@Composable
private fun ScreenButtonsPanel(s: AppState, a: MainActivity) {
    // Invertir el volumen pasó a Controles (botones de la tablet).
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Pantalla", "Screen")) {
            ScreenRow(a)
        }
    }
}

/**
 * Game Assistant, el de la ROM de la tablet: si hay un juego ahora (se actualiza solo), ocultarlo y un acceso a sus
 * ajustes. Ocultarlo es quitarle "Mostrar sobre otras apps": con Shizuku G9 Control lo hace solo (y lo muestra un
 * momento para cambiar el rendimiento); sin Shizuku se puede a mano en Ajustes. Solo si la tablet lo tiene.
 */
@Composable
private fun ModeRow(name: String, desc: String) {
    Row {
        Text(name, Modifier.width(150.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GameAssistantSettings(s: AppState, a: MainActivity) {
    val zui = remember { ZuiGame.installed(a) }
    if (!zui) return
    var game by remember { mutableStateOf(ZuiGame.inGame(a)) }
    DisposableEffect(Unit) {
        val o = ZuiGame.observe(a, android.os.Handler(a.mainLooper)) { game = ZuiGame.inGame(a) }
        onDispose { ZuiGame.unobserve(a, o) }
    }
    val ready = Shell.state == Shell.State.READY
    val gaScope = rememberCoroutineScope()
    var gaHidden by remember { mutableStateOf(AutoRules.gaHidden(a)) }
    // Lo que el usuario eligió, no cómo está ahora: puede estar visible un rato porque G9 Control lo está usando
    // (copiar el estado actual a la preferencia hacía que no se volviera a ocultar). Solo la primera vez, sin
    // preferencia guardada, se toma de cómo está.
    LaunchedEffect(ready) { if (ready && gaHidden == null) ZuiGame.hidden()?.let { gaHidden = it; AutoRules.setGaHidden(a, it) } }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel("Game Assistant", trailing = {
            Text(if (game) tr("En un juego", "In a game") else tr("Sin juego", "No game"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }) {
            CompositionLocalProvider(LocalEditable provides (ready && LocalGateOk.current)) {
                SwitchRow(
                    tr("Ocultar Game Assistant", "Hide Game Assistant"), gaHidden == true
                ) { want ->
                    if (ready) gaScope.launch { if (ZuiGame.setHidden(want)) { AutoRules.setGaHidden(a, want); gaHidden = want } }
                }
            }
            if (!ready) OutlinedButton(onClick = { runCatching { a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) } }) {
                Text(tr("Ocultarlo a mano (Mostrar sobre otras apps)", "Hide it manually (Display over other apps)"))
            }
            // Game Assistant apaga el brillo automático al entrar a un juego (y lo devuelve al salir). Se cambia con el
            // permiso de pantalla de adb (es un ajuste global).
            val canWrite = remember { Aspect.canWrite(a) }
            var autoInGames by remember {
                mutableStateOf(runCatching { Settings.Global.getInt(a.contentResolver, GA_NO_AUTO_BRIGHTNESS) == 0 }.getOrDefault(false))
            }
            CompositionLocalProvider(LocalEditable provides (canWrite && LocalGateOk.current)) {
                SwitchRow(
                    tr("Brillo automático en juegos", "Auto brightness in games"), autoInGames, null
                ) { want ->
                    if (runCatching { Settings.Global.putInt(a.contentResolver, GA_NO_AUTO_BRIGHTNESS, if (want) 0 else 1) }.isSuccess) autoInGames = want
                }
            }
            // Otras dos de Game Assistant (Settings.Global key_game_assistant_*), que su app tiene escondidas en sus ajustes.
            CompositionLocalProvider(LocalEditable provides (canWrite && LocalGateOk.current)) {
                listOf(
                    "key_game_assistant_notification_undisturb" to tr("Sin notificaciones en juegos", "No notifications in games"),
                    "key_game_assistant_memory_accelerate" to tr("Cerrar apps de fondo al abrir un juego", "Close background apps when a game opens"),
                ).forEach { (key, label) ->
                    var on by remember { mutableStateOf(runCatching { Settings.Global.getInt(a.contentResolver, key) == 1 }.getOrDefault(false)) }
                    SwitchRow(label, on) { want -> if (runCatching { Settings.Global.putInt(a.contentResolver, key, if (want) 1 else 0) }.isSuccess) on = want }
                }
            }
            if (!canWrite) Hint(tr("Necesita el permiso de pantalla (Ajustes → Permisos).", "Needs the screen permission (Settings → Permissions)."))
            // Sin la tabla de qué hace cada modo de rendimiento: se elige en el panel, que ya los nombra.
            OutlinedButton(onClick = { if (!ZuiGame.openSettings(a)) s.fail(tr("No se pudieron abrir los ajustes de Game Assistant.", "Couldn't open Game Assistant settings.")) }) {
                Text(tr("Ajustes de Game Assistant", "Game Assistant settings"))
            }
        }
    }
}

/**
 * Pantalla 16:9 para un monitor externo. Sin Shizuku si la app tiene WRITE_SECURE_SETTINGS (se da una vez con
 * adb); si no, con Shizuku; si no hay ninguno, el comando para darlo.
 */
@Composable
private fun ScreenRow(a: MainActivity) {
    val scope = rememberCoroutineScope()
    var wide by remember { mutableStateOf(Aspect.is169(a)) }
    if (!Aspect.available(a)) {
        Text(tr("Pantalla 16:9", "16:9 screen"), fontSize = 14.sp)
        Hint(tr("Se activa una vez desde un computador: ${Aspect.GRANT_COMMAND}", "Enable it once from a computer: ${Aspect.GRANT_COMMAND}"))
        return
    }
    SwitchRow(
        tr("Pantalla 16:9", "16:9 screen"), wide
    ) { want ->
        scope.launch {
            if (want != Aspect.is169(a)) Aspect.toggle(a)
            kotlinx.coroutines.delay(500)
            wide = Aspect.is169(a)
        }
    }
}

/**
 * Uso: una sola lista de tus apps con todo lo que se sabe de cada una, para decidir qué conviene. Lo que gasta a batería
 * (W y cuánto duraría la batería llena), si está abierta (RAM, cerrarla) y cómo fue al jugarla (veces, temperatura).
 * Arriba la RAM y cerrar lo de fondo; abajo, la comparación por modo de rendimiento. Antes eran tres listas sueltas.
 */
@Composable
fun UsagePage(s: AppState, a: MainActivity) {
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); tick++ } }
    val info = remember(tick) { Memory.info(a) }
    val ready = Shell.state == Shell.State.READY
    var tasks by remember { mutableStateOf(listOf<Memory.Task>()) }
    LaunchedEffect(ready, tick) { if (ready) tasks = Memory.tasks(a) }
    val power = remember(tick) { PowerLog.apps(a) }
    val sessions = remember(tick) { Sessions.list(a) }
    val modes = remember(tick) { Sessions.byMode(a) }
    val full = remember { ZuiBattery.fullWh(a) }
    var busy by remember { mutableStateOf(false) }

    class Row(val pkg: String, val label: String, val task: Memory.Task?, val watts: Double?, val measured: Long?,
              val plays: Int, val cpuMax: Int?, val lastHz: Int?)
    val rows = remember(tasks, power, sessions) {
        val pkgs = LinkedHashSet<String>().apply { tasks.forEach { add(it.app.pkg) }; power.forEach { add(it.pkg) }; sessions.forEach { add(it.pkg) } }
        pkgs.mapNotNull { pkg ->
            val label = appLabel(a, pkg) ?: return@mapNotNull null   // desinstaladas no salen
            val p = power.firstOrNull { it.pkg == pkg }
            val ss = sessions.filter { it.pkg == pkg }
            Row(pkg, label, tasks.firstOrNull { it.app.pkg == pkg }, p?.avgWatts, p?.seconds,
                ss.size, ss.mapNotNull { it.cpuMax }.maxOrNull(), ss.firstOrNull()?.hz)
        }.sortedWith(compareByDescending<Row> { it.watts ?: -1.0 }.thenByDescending { it.task?.ramMb ?: 0 })
    }

    Centered {
        Sheet {
            Panel(tr("RAM", "RAM")) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("${Memory.gb(info.totalBytes - info.freeBytes)} de ${Memory.gb(info.totalBytes)} en uso",
                        "${Memory.gb(info.totalBytes - info.freeBytes)} of ${Memory.gb(info.totalBytes)} in use"),
                        Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = {
                        busy = true
                        scope.launch {
                            val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Memory.freeAll(a) }
                            busy = false; tick++
                            s.status = tr("Se liberaron ${Memory.gb(freed)}.", "Freed ${Memory.gb(freed)}.")
                        }
                    }, enabled = !busy) { Text(if (busy) tr("Cerrando…", "Closing…") else tr("Cerrar las de fondo", "Close background apps")) }
                }
                LinearProgressIndicator({ 1f - info.freeBytes.toFloat() / info.totalBytes.coerceAtLeast(1) }, Modifier.fillMaxWidth().height(6.dp),
                    color = c.primary, trackColor = c.onSurface.copy(alpha = 0.08f))
                if (!ready) Hint(tr("Para ver y cerrar cada app abierta hace falta Shizuku.", "Seeing and closing each open app needs Shizuku."))
            }

            Panel(tr("Tus apps", "Your apps"), trailing = {
                if (power.isNotEmpty() || sessions.isNotEmpty()) ConfirmButton(tr("Reiniciar", "Reset")) { PowerLog.reset(a); Sessions.reset(a); tick++ }
            }) {
                if (rows.isEmpty()) Hint(tr("El consumo se mide sin cargador; las partidas se anotan solas.", "Draw is measured unplugged; play sessions log themselves."))
                rows.take(14).forEach { r ->
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = remember(r.pkg) { runCatching { a.packageManager.getApplicationIcon(r.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                        icon?.let { androidx.compose.foundation.Image(it, null, Modifier.size(36.dp)); Spacer(Modifier.width(14.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            // En palabras: si está abierta y cuánto ocupa; cuántas veces se jugó, la temperatura máxima y los Hz.
                            val detail = listOfNotNull(
                                r.task?.let { t -> tr("abierta, ${t.ramMb} MB", "open, ${t.ramMb} MB") + (if (t.cpu >= 5) tr(", usando CPU", ", using CPU") else "") },
                                r.plays.takeIf { it > 0 }?.let { tr(if (it == 1) "1 partida" else "$it partidas", if (it == 1) "1 session" else "$it sessions") },
                                r.cpuMax?.let { tr("CPU hasta $it °C", "CPU up to $it °C") },
                                r.lastHz?.let { "$it Hz" },
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        // Lo que gasta y cuánto duraría la batería llena solo con ella.
                        r.watts?.let { w ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text("%.1f W".format(w), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                full?.takeIf { w > 0.3 }?.let { wh -> Text(ZuiBattery.formatTime((wh / w * 3_600_000).toLong()), fontSize = 13.sp, color = c.onSurfaceVariant) }
                            }
                        }
                        if (r.task != null) {
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(onClick = { tasks = tasks - r.task; scope.launch { Memory.stop(a, r.pkg); tick++ } }) { Text(tr("Cerrar", "Close")) }
                        }
                    }
                }
            }

            if (modes.isNotEmpty()) Panel(tr("Por modo de rendimiento", "By performance mode")) {
                modes.forEach { m ->
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.mode?.let { runCatching { ZuiGame.PerfMode.valueOf(it).label }.getOrNull() } ?: tr("Modo desconocido", "Unknown mode"),
                            Modifier.weight(1f), fontSize = 15.sp)
                        Text(listOfNotNull(m.avgWatts?.let { "%.1f W".format(it) }, m.avgCpuMhz?.let { Stats.cpuText(it) },
                            m.avgCpuMax?.let { "$it °C" }, ZuiBattery.formatTime(m.seconds * 1000L)).joinToString(" · "),
                            fontSize = 14.sp, color = c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
