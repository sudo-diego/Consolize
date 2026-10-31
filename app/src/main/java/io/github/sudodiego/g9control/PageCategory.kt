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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.togetherWith
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
 * Una cosa de la app que se puede abrir: una página entera o solo sus partes [parts] (los títulos de sus secciones).
 * [id] es fijo (se guarda en lo fijado en Inicio o en el dock); [icon], el de su mosaico.
 */
class Entry(val label: () -> String, val page: Page, val id: String, val icon: ImageVector, val parts: (() -> Set<String>)? = null)

private fun part(es: String, en: String): () -> Set<String> = { setOf(tr(es, en)) }

/**
 * Lo que tiene cada categoría, en orden. Cada línea abre una sola cosa. Lo del G9, solo si la app vio uno.
 */
fun entriesFor(s: AppState, sec: Section): List<Entry> = when (sec) {
    Section.HOME -> emptyList()
    Section.GAMES -> listOf(
        Entry({ tr("Biblioteca", "Library") }, Page.GAMES, "library", Icons.Outlined.SportsEsports),
        Entry({ tr("Hz en juegos", "Refresh rate in games") }, Page.GAMEPANEL, "hz", Icons.Outlined.DisplaySettings, part("Hz en juegos", "Refresh rate in games")),
        Entry({ "Game Assistant" }, Page.GAMEPANEL, "ga", Icons.Outlined.Tune) { setOf("Game Assistant") },
        Entry({ tr("Logros sin internet", "Offline achievements") }, Page.GAMEPANEL, "achievements", Icons.Outlined.Star, part("Logros sin internet", "Offline achievements")),
    )
    Section.PANEL -> listOf(
        Entry({ tr("Panel de juego", "Game panel") }, Page.PANEL, "panel", Icons.Outlined.Dashboard),
        Entry({ tr("Capturas y monitor", "Screenshots and monitor") }, Page.CAPTURES, "captures", Icons.Outlined.Screenshot),
    )
    Section.CONTROLS -> listOf(
        Entry({ tr("Botón guía", "Guide button") }, Page.CONTROLS, "guide", Icons.Outlined.Home, part("Botón guía", "Guide button")),
        Entry({ tr("Combinaciones", "Button combos") }, Page.CONTROLS, "combos", Icons.Outlined.Gamepad, part("Combinaciones", "Button combos")),
        Entry({ tr("Botones de volumen", "Volume buttons") }, Page.CONTROLS, "volume", Icons.AutoMirrored.Outlined.VolumeUp, part("Botones de volumen", "Volume buttons")),
        Entry({ tr("Probar control", "Test controller") }, Page.TEST, "test", Icons.Outlined.TouchApp),
    ) + if (!s.g9Known) emptyList() else listOf(
        Entry({ "Legion G9" }, Page.HOME, "g9", Icons.Outlined.VideogameAsset),
        Entry({ tr("Cambio de modo", "Mode switching") }, Page.AUTO, "g9_auto", Icons.Outlined.SwapHoriz),
        Entry({ tr("Luces", "Lights") }, Page.LIGHTS, "g9_lights", Icons.Outlined.Lightbulb),
        Entry({ tr("Sticks", "Sticks") }, Page.STICKS, "g9_sticks", Icons.Outlined.OpenWith),
        Entry({ tr("Vibración", "Vibration") }, Page.VIBRATION, "g9_vibration", Icons.Outlined.Vibration),
        Entry({ tr("Traseros", "Paddles") }, Page.BACK, "g9_back", Icons.Outlined.FlipToBack),
        Entry({ tr("Preferencias del G9", "G9 preferences") }, Page.G9_PREFS, "g9_prefs", Icons.Outlined.SettingsSuggest),
    )
    Section.SYSTEM -> listOf(
        Entry({ tr("Batería", "Battery") }, Page.BATTERY, "battery", Icons.Outlined.BatteryFull) { setOf(tr("Batería", "Battery"), tr("Ahora", "Now")) },
        Entry({ tr("Avisos de batería", "Battery notices") }, Page.BATTERY, "battery_notices", Icons.Outlined.Notifications, part("Avisos de batería", "Battery notices")),
        Entry({ tr("Uso", "Usage") }, Page.USAGE, "usage", Icons.Outlined.Insights),
        Entry({ tr("Pantalla", "Screen") }, Page.SCREEN, "screen", Icons.Outlined.TabletAndroid),
    )
    Section.SETTINGS -> listOf(
        Entry({ tr("Apariencia", "Appearance") }, Page.DIAGNOSTICS, "appearance", Icons.Outlined.Palette, part("Apariencia", "Appearance")),
        Entry({ tr("General", "General") }, Page.DIAGNOSTICS, "general", Icons.Outlined.Settings, part("General", "General")),
        Entry({ tr("Permisos", "Permissions") }, Page.DIAGNOSTICS, "permissions", Icons.Outlined.VerifiedUser, part("Permisos", "Permissions")),
    )
}

/** Todo lo que se puede abrir, para buscar por [Entry.id]. */
fun allEntries(s: AppState): List<Entry> = Section.entries.flatMap { entriesFor(s, it) }

/**
 * Los cuatro temas de Más (como la configuración de una consola): el nombre y lo que tiene. Biblioteca y el Panel de
 * juego están en el dock; lo demás, aquí.
 */
fun moreGroups(s: AppState): List<Pair<String, List<Entry>>> = listOf(
    tr("Juegos", "Games") to (entriesFor(s, Section.GAMES).filter { it.id != "library" } + entriesFor(s, Section.PANEL).filter { it.id == "captures" }),
    tr("Controles", "Controllers") to entriesFor(s, Section.CONTROLS),
    tr("Tablet", "Tablet") to entriesFor(s, Section.SYSTEM),
    tr("Ajustes", "Settings") to entriesFor(s, Section.SETTINGS),
)

/** Los destinos fijos del dock (los demás son entradas fijadas desde Más). */
object Dest {
    const val HOME = "home"; const val LIBRARY = "library"; const val PANEL = "panel"; const val CONTROL = "control"; const val MORE = "more"
}

/** Lo que hay en el dock, en orden: Inicio, lo elegido (hasta 4) y Más. */
fun dockItems(s: AppState): List<Triple<String, String, ImageVector>> {
    val entries = allEntries(s).associateBy { it.id }
    val mid = s.dockMid.mapNotNull { id ->
        when (id) {
            Dest.CONTROL -> Triple(id, tr("Control", "Controller"), Icons.Outlined.VideogameAsset)
            Dest.PANEL -> Triple(id, tr("Panel", "Panel"), Icons.Outlined.Dashboard)
            Dest.LIBRARY -> Triple(id, tr("Biblioteca", "Library"), Icons.Outlined.SportsEsports)
            else -> entries[id]?.let { Triple(id, it.label(), it.icon) }
        }
    }
    return listOf(Triple(Dest.HOME, tr("Inicio", "Home"), Icons.Outlined.Home)) + mid + Triple(Dest.MORE, tr("Más", "More"), Icons.Outlined.MoreHoriz)
}

fun sectionName(sec: Section) = when (sec) {
    Section.HOME -> tr("Inicio", "Home")
    Section.GAMES -> tr("Juegos", "Games")
    Section.PANEL -> tr("Panel", "Panel")
    Section.CONTROLS -> tr("Controles", "Controllers")
    Section.SYSTEM -> tr("Tablet", "Tablet")
    Section.SETTINGS -> tr("Ajustes", "Settings")
}


/**
 * Lo de en medio de la app: sin marco (lo de adentro va directo sobre el fondo, como en una consola) y abajo el dock.
 */
@Composable
fun CategoryFrame(s: AppState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}

/**
 * La raíz de un destino del dock: Inicio (el resumen), Control (lo de los controles) o Más (los cuatro temas). A abre una
 * cosa; B (en lo abierto) vuelve aquí.
 */
@Composable
fun CategoryHub(s: AppState) {
    val a = LocalContext.current as MainActivity
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (s.dest) {
            Dest.MORE -> MoreHub(s)
            Dest.CONTROL -> ControlHub(s)
            else -> HomeSummary(s, a)
        }
    }
}

/** Control: por ahora la lista de lo de los controles (la página nueva, con el dibujo, viene después). */
@Composable
private fun ControlHub(s: AppState) {
    val entries = entriesFor(s, Section.CONTROLS)
    Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { e -> EntryTile(s, e, Modifier.weight(1f)) }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Más, como la configuración de una consola: arriba los cuatro temas grandes (su nombre y lo que tienen, sin íconos) y
 * abajo solo las opciones del elegido. Moverse entre los temas cambia lo de abajo; A entra a las opciones y B vuelve al
 * tema. En una opción, Y la fija en Inicio y X en el dock.
 */
@Composable
private fun MoreHub(s: AppState) {
    val c = MaterialTheme.colorScheme
    val groups = moreGroups(s)
    var cat by rememberSaveable { mutableIntStateOf(0) }
    val catFocus = remember { List(groups.size) { FocusRequester() } }
    val firstOption = remember { FocusRequester() }
    Column(Modifier.widthIn(max = 1200.dp).fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            groups.forEachIndexed { i, (name, items) ->
                val src = remember { MutableInteractionSource() }
                val focused by src.collectIsFocusedAsState()
                LaunchedEffect(focused) { if (focused) cat = i }
                Surface(
                    onClick = { cat = i; runCatching { firstOption.requestFocus() } }, interactionSource = src,
                    shape = RoundedCornerShape(24.dp), color = cardColor(),
                    border = if (focused) BorderStroke(2.dp, Color.White) else null,
                    modifier = Modifier.weight(1f).height(140.dp).focusRequester(catFocus[i]).graphicsLayer {
                        val k = if (focused) 1.04f else 1f; scaleX = k; scaleY = k
                        alpha = if (i == cat) 1f else 0.75f
                    }
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.Bottom) {
                        if (i == cat) Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
                        Spacer(Modifier.weight(1f))
                        Text(name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(items.joinToString(" · ") { it.label() }, fontSize = 12.sp, lineHeight = 17.sp, color = c.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        // B en una opción vuelve a su tema.
        Box(Modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyUp && (e.key == Key.ButtonB || e.key == Key.Back)) { runCatching { catFocus[cat].requestFocus() }; true } else false
        }) {
            androidx.compose.animation.AnimatedContent(cat, transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(200)) { -it / 12 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
            }, label = "more") { k ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groups[k].second.chunked(4).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEachIndexed { j, e -> EntryTile(s, e, Modifier.weight(1f).then(if (r == 0 && j == 0) Modifier.focusRequester(firstOption) else Modifier), pins = true) }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            PadBadge("Y", round = true); Spacer(Modifier.width(6.dp))
            Text(tr("Fijar en Inicio", "Pin to Home"), fontSize = 13.sp, color = c.onSurfaceVariant)
            Spacer(Modifier.width(18.dp))
            PadBadge("X", round = true); Spacer(Modifier.width(6.dp))
            Text(tr("Fijar en el dock", "Pin to dock"), fontSize = 13.sp, color = c.onSurfaceVariant)
        }
    }
}

/** El color de las tarjetas (mosaicos, temas, dock): el de la superficie del tema, un poco translúcido sobre el fondo. */
@Composable
fun cardColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (ThemeState.glass) 0.85f else 1f)

/**
 * Un mosaico de una cosa de la app (como los del panel): el ícono en su insignia y el nombre. Con [pins], Y lo fija en
 * Inicio y X en el dock (y lo dice con una etiqueta).
 */
@Composable
fun EntryTile(s: AppState, e: Entry, modifier: Modifier, pins: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    val label = e.label()
    // Al volver de lo que se abrió, el foco vuelve a su mosaico.
    LaunchedEffect(Unit) { if (s.lastSub == label) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() }; s.lastSub = null } }
    Surface(
        onClick = { s.enter(e.page, e.parts?.invoke(), label) }, interactionSource = src,
        shape = RoundedCornerShape(18.dp), color = cardColor(),
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
        modifier = modifier.height(72.dp).focusRequester(focus)
            .graphicsLayer { val k = if (focused) 1.03f else 1f; scaleX = k; scaleY = k }
            .then(if (pins) Modifier.onPreviewKeyEvent { k ->
                if (k.type != KeyEventType.KeyUp) return@onPreviewKeyEvent k.key == Key.ButtonY || k.key == Key.ButtonX
                when (k.key) {
                    Key.ButtonY -> { s.toggleHomePin(ctx, e.id); UiSounds.play(ctx, UiSounds.Kind.SELECT); true }
                    Key.ButtonX -> { s.toggleDockPin(ctx, e.id); UiSounds.play(ctx, UiSounds.Kind.SELECT); true }
                    else -> false
                }
            } else Modifier)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.onSurface.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(e.icon, null, Modifier.size(22.dp), tint = c.onSurface.copy(alpha = 0.85f))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (pins) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (e.id in s.homePins) PinTag(tr("Inicio", "Home"))
                if (e.id in s.dockMid) PinTag("Dock")
            }
        }
    }
}

@Composable
private fun PinTag(text: String) {
    val c = MaterialTheme.colorScheme
    Text(text, Modifier.clip(RoundedCornerShape(6.dp)).background(c.primary.copy(alpha = 0.16f)).padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 10.sp, color = c.primary)
}

/**
 * El dock, abajo y flotando: Inicio, lo elegido (hasta 4) y Más, con LB y RB en los extremos (cambian de destino).
 * Se toca con el dedo; con el control se usa LB/RB (no toma el foco).
 */
@Composable
fun DockBar(s: AppState) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = c.surfaceContainerHigh.copy(alpha = 0.9f), shadowElevation = 8.dp) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            PadBadge("LB"); Spacer(Modifier.width(4.dp))
            dockItems(s).forEach { (id, label, icon) ->
                val cur = s.dest == id
                val bg by androidx.compose.animation.animateColorAsState(if (cur) c.primary else Color.Transparent, label = "dock")
                Row(Modifier.clip(RoundedCornerShape(15.dp)).background(bg).focusProperties { canFocus = false }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.goDest(id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val fg = if (cur) c.onPrimary else c.onSurfaceVariant
                    Icon(icon, null, Modifier.size(20.dp), tint = fg)
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
                }
            }
            Spacer(Modifier.width(4.dp)); PadBadge("RB")
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