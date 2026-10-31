package io.github.sudodiego.g9control

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Una línea de la tarjeta de una categoría: abre [page] entera o solo sus partes [parts] (los títulos de sus
 * secciones; la primera da el nombre si no hay [label]).
 */
class Entry(val label: () -> String, val page: Page, val parts: (() -> Set<String>)? = null)

/**
 * Lo que tiene cada categoría, en orden. Cada línea abre una sola cosa (antes, páginas con todo junto). Lo del G9, solo
 * si la app vio uno.
 */
fun entriesFor(s: AppState, sec: Section): List<Entry> = when (sec) {
    // Inicio no tiene líneas: su tarjeta muestra el resumen directo.
    Section.HOME -> emptyList()
    Section.GAMES -> listOf(
        Entry({ tr("Biblioteca", "Library") }, Page.GAMES),
        Entry({ tr("Hz en juegos", "Refresh rate in games") }, Page.GAMEPANEL) { setOf(tr("Hz en juegos", "Refresh rate in games")) },
        Entry({ "Game Assistant" }, Page.GAMEPANEL) { setOf("Game Assistant") },
        Entry({ tr("Logros sin internet", "Offline achievements") }, Page.GAMEPANEL) { setOf(tr("Logros sin internet", "Offline achievements")) },
    )
    // El panel en una sola línea: la vista previa con todas sus opciones al lado (dos líneas llevaban a la misma vista
    // previa). Capturas y el monitor de rendimiento, aparte.
    Section.PANEL -> listOf(
        Entry({ tr("Panel de juego", "Game panel") }, Page.PANEL),
        Entry({ tr("Capturas y monitor", "Screenshots and monitor") }, Page.CAPTURES),
    )
    Section.CONTROLS -> listOf(
        Entry({ tr("Botón guía", "Guide button") }, Page.CONTROLS) { setOf(tr("Botón guía", "Guide button")) },
        Entry({ tr("Combinaciones", "Button combos") }, Page.CONTROLS) { setOf(tr("Combinaciones", "Button combos")) },
        Entry({ tr("Botones de volumen", "Volume buttons") }, Page.CONTROLS) { setOf(tr("Botones de volumen", "Volume buttons")) },
        Entry({ tr("Probar control", "Test controller") }, Page.TEST),
    ) + if (!s.g9Known) emptyList() else listOf(
        Entry({ "Legion G9" }, Page.HOME),
        Entry({ tr("Cambio de modo", "Mode switching") }, Page.AUTO),
        Entry({ tr("Luces", "Lights") }, Page.LIGHTS),
        Entry({ tr("Sticks", "Sticks") }, Page.STICKS),
        Entry({ tr("Vibración", "Vibration") }, Page.VIBRATION),
        Entry({ tr("Traseros", "Paddles") }, Page.BACK),
        Entry({ tr("Preferencias del G9", "G9 preferences") }, Page.G9_PREFS),
    )
    Section.SYSTEM -> listOf(
        Entry({ tr("Batería", "Battery") }, Page.BATTERY) { setOf(tr("Batería", "Battery"), tr("Ahora", "Now")) },
        Entry({ tr("Avisos de batería", "Battery notices") }, Page.BATTERY) { setOf(tr("Avisos de batería", "Battery notices")) },
        Entry({ tr("Uso", "Usage") }, Page.USAGE),
        // Una sola: 16:9 y la pantalla externa juntas (sola, 16:9 era una línea con un interruptor).
        Entry({ tr("Pantalla", "Screen") }, Page.SCREEN),
    )
    Section.SETTINGS -> listOf(
        Entry({ tr("Apariencia", "Appearance") }, Page.DIAGNOSTICS) { setOf(tr("Apariencia", "Appearance")) },
        Entry({ tr("General", "General") }, Page.DIAGNOSTICS) { setOf(tr("General", "General")) },
        Entry({ tr("Permisos", "Permissions") }, Page.DIAGNOSTICS) { setOf(tr("Permisos", "Permissions")) },
    )
}

/** El color de cada categoría (su ícono en la tarjeta y en la fila de abajo), como los de la Wii U. */
fun sectionColor(sec: Section) = when (sec) {
    Section.HOME -> Color(0xFF2F80ED)
    Section.GAMES -> Color(0xFFE5484D)
    Section.PANEL -> Color(0xFFF2994A)
    Section.CONTROLS -> Color(0xFF27AE60)
    Section.SYSTEM -> Color(0xFF9B51E0)
    Section.SETTINGS -> Color(0xFF6B7280)
}

/**
 * La tarjeta de una categoría, inspirada en los ajustes de la Wii U: a la izquierda un mosaico blanco con su ícono de
 * color y su nombre; a la derecha lo que tiene, una línea por cosa (▸). Izquierda/derecha o LB/RB cambian de
 * categoría (también las flechas de los lados); abajo, todas las categorías. A abre una línea; B (en lo abierto)
 * vuelve aquí.
 */
@Composable
fun CategoryFrame(s: AppState, content: @Composable () -> Unit) {
    val all = Section.entries
    val idx = all.indexOf(s.section)
    fun go(d: Int) { all.getOrNull(idx + d)?.let { s.goTo(it) } }
    // El marco no cambia al entrar a una línea: lo de adentro se abre en el mismo espacio que la tarjeta (antes la
    // tarjeta se achicaba durante la animación y la página usaba otro ancho).
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SideArrow(left = true, enabled = s.inHub && idx > 0) { go(-1) }
            GlassSurface(RoundedCornerShape(40.dp), Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalInSheet provides true) { content() }
            }
            SideArrow(left = false, enabled = s.inHub && idx < all.lastIndex) { go(1) }
        }
        Dock(s)
    }
}

/**
 * Lo de adentro de la tarjeta de una categoría, inspirada en los ajustes de la Wii U: a la izquierda un mosaico blanco
 * con su ícono de color y su nombre; a la derecha lo que tiene, una línea por cosa (▸). Izquierda/derecha cambian de
 * categoría. A abre una línea; B (en lo abierto) vuelve aquí.
 */
@Composable
fun CategoryHub(s: AppState) {
    val sec = s.section
    val all = Section.entries
    val idx = all.indexOf(sec)
    fun go(d: Int) { all.getOrNull(idx + d)?.let { s.goTo(it) } }
    val content: @Composable () -> Unit = {
        if (sec == Section.HOME) HomeSummary(s, LocalContext.current as MainActivity) else EntryList(s, sec) { d -> go(d) }
    }
    if (LocalWide.current) Row(Modifier.fillMaxSize().padding(36.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryHeader(s, sec, Modifier.weight(0.8f).padding(start = 24.dp))
        Spacer(Modifier.width(32.dp))
        Box(Modifier.weight(1f)) { content() }
    } else Column(Modifier.fillMaxSize().padding(28.dp)) {
        CategoryHeader(s, sec, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        content()
    }
}
/**
 * A la izquierda de la tarjeta: el nombre de la categoría en grande con una raya de su color y, debajo, algo vivo de
 * ella (batería, cuántos juegos, el control conectado). Sin ícono grande: se veía como clip-art.
 */
@Composable
private fun CategoryHeader(s: AppState, sec: Section, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val live = when (sec) {
        // Inicio: la batería ya está en su resumen.
        Section.GAMES -> GamesCache.apps?.count { it.game }?.let { tr("$it juegos", "$it games") }
        Section.CONTROLS -> android.view.InputDevice.getDeviceIds().toList().mapNotNull { android.view.InputDevice.getDevice(it) }
            .firstOrNull { !it.isVirtual && (it.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD }?.name
            ?: tr("Sin control", "No controller")
        Section.SETTINGS -> runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()?.let { tr("Versión $it", "Version $it") }
        else -> null
    }
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Box(Modifier.width(56.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(sectionColor(sec)))
        Spacer(Modifier.height(20.dp))
        Text(sectionName(sec), fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp, maxLines = 1)
        if (live != null) {
            Spacer(Modifier.height(10.dp))
            Text(live, fontSize = 20.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
/** Las líneas de la tarjeta. Izquierda y derecha cambian de categoría (con el foco en una línea). */
@Composable
private fun EntryList(s: AppState, sec: Section, go: (Int) -> Unit) {
    val entries = entriesFor(s, sec)
    Column(
        Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) false
            else when (e.key) { Key.DirectionLeft -> { go(-1); true }; Key.DirectionRight -> { go(1); true }; else -> false }
        },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        entries.forEach { e -> EntryRow(s, e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(s: AppState, e: Entry) {
    val c = MaterialTheme.colorScheme
    val label = e.label()
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    // Al volver de lo que se abrió, el foco vuelve a su línea.
    LaunchedEffect(Unit) { if (s.lastSub == label) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() }; s.lastSub = null } }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(
            onClick = { s.enter(e.page, e.parts?.invoke(), label) }, shape = RoundedCornerShape(16.dp),
            color = if (focused) c.primary.copy(alpha = 0.14f) else Color.Transparent,
            border = if (focused) BorderStroke(2.dp, c.primary) else null,
            interactionSource = source, modifier = Modifier.fillMaxWidth().focusRequester(focus)
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("▸", fontSize = 18.sp, color = if (focused) c.primary else c.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.width(16.dp))
                Text(label, fontSize = 24.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Flecha grande a un lado de la tarjeta (con el dedo; con el control se usan izquierda/derecha o LB/RB). */
@Composable
private fun SideArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(64.dp).fillMaxHeight().focusProperties { canFocus = false }
        .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (enabled) Icon(if (left) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight, null, Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

/** Abajo: todas las categorías con su ícono de color; la actual, más grande y levantada. Se tocan para ir. */
@Composable
private fun Dock(s: AppState) {
    // Los nombres, no íconos (se veían como clip-art): la actual en negrita con una raya de su color debajo.
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center) {
        Section.entries.forEach { sec ->
            val cur = sec == s.section
            val c = MaterialTheme.colorScheme
            Column(Modifier.padding(horizontal = 18.dp).focusProperties { canFocus = false }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.goTo(sec) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sectionName(sec), fontSize = 18.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    color = if (cur) c.onSurface else c.onSurface.copy(alpha = 0.55f))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (cur) sectionColor(sec) else Color.Transparent))
            }
        }
    }
}
/** Arriba a la derecha, chico (como la versión en la Wii U): la batería y la hora. */
@Composable
fun TopStatus(showBattery: Boolean = true) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5_000); tick++ } }
    // Cada 5 s: nivel, si carga y si está enchufada sin cargar (límite); el bypass, el que lee el servicio.
    val bat = remember(tick) { ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) }
    val level = bat?.let { it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
    val charging = bat?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING
    val plugged = (bat?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val bypass = ZuiBattery.bypassNow == true && plugged
    val time = remember(tick) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 28.dp, top = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        // Con el mismo ícono de batería que el panel y el inicio.
        if (showBattery && level != null) { BatteryGlyph(level, charging = charging && !bypass, bypass = bypass, held = plugged && !charging && !bypass); Spacer(Modifier.width(8.dp)) }
        Text(listOfNotNull(level?.takeIf { showBattery }?.let { "$it %" }, time).joinToString("   "), fontSize = 22.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}