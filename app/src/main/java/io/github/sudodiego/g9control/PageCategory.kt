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
    Section.HOME -> listOf(
        Entry({ tr("Resumen", "Overview") }, Page.TABLET_HOME),
        Entry({ tr("Apps abiertas", "Open apps") }, Page.TABLET_HOME) { setOf(tr("Apps abiertas", "Open apps")) },
    )
    Section.GAMES -> listOf(
        Entry({ tr("Biblioteca", "Library") }, Page.GAMES),
        Entry({ tr("Hz en juegos", "Refresh rate in games") }, Page.GAMEPANEL) { setOf(tr("Hz en juegos", "Refresh rate in games")) },
        Entry({ "Game Assistant" }, Page.GAMEPANEL) { setOf("Game Assistant") },
        Entry({ tr("Logros sin internet", "Offline achievements") }, Page.GAMEPANEL) { setOf(tr("Logros sin internet", "Offline achievements")) },
    )
    Section.PANEL -> {
        val preview = { tr("Vista previa", "Preview") }
        listOf(
            Entry({ tr("Panel de juego", "Game panel") }, Page.PANEL) { setOf(preview(), tr("Panel de juego", "Game panel")) },
            Entry({ tr("Diseño", "Layout") }, Page.PANEL) { setOf(preview(), tr("Diseño", "Layout")) },
            Entry({ tr("Fondo", "Background") }, Page.PANEL) { setOf(preview(), tr("Fondo", "Background")) },
            Entry({ tr("Colores", "Colors") }, Page.PANEL) { setOf(preview(), tr("Colores", "Colors")) },
            Entry({ tr("Capturas y grabación", "Screenshots and recording") }, Page.CAPTURES) { setOf(tr("Capturas y grabación", "Screenshots and recording")) },
            Entry({ tr("Monitor de rendimiento", "Performance monitor") }, Page.CAPTURES) { setOf(tr("Monitor de rendimiento", "Performance monitor")) },
        )
    }
    Section.CONTROLS -> listOf(
        Entry({ tr("Botón guía", "Guide button") }, Page.CONTROLS) { setOf(tr("Botón guía", "Guide button")) },
        Entry({ tr("Sin botón guía", "Without a guide button") }, Page.CONTROLS) { setOf(tr("Sin botón guía", "Without a guide button")) },
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
        Entry({ tr("Consumo por app", "Draw per app") }, Page.BATTERY) { setOf(tr("Consumo por app", "Draw per app")) },
        Entry({ tr("Sesiones de juego", "Game sessions") }, Page.BATTERY) { setOf(tr("Sesiones de juego", "Game sessions")) },
        Entry({ tr("Pantalla", "Screen") }, Page.SCREEN) { setOf(tr("Pantalla", "Screen")) },
        Entry({ tr("Monitor", "Monitor") }, Page.SCREEN) { setOf(tr("Monitor", "Monitor")) },
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
fun CategoryScreen(s: AppState) {
    val sec = s.section
    val all = Section.entries
    val idx = all.indexOf(sec)
    fun go(d: Int) { all.getOrNull(idx + d)?.let { s.goTo(it) } }
    val wide = LocalWide.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SideArrow(left = true, enabled = idx > 0) { go(-1) }
            GlassSurface(RoundedCornerShape(40.dp), Modifier.weight(1f).fillMaxHeight()) {
                val content: @Composable () -> Unit = { EntryList(s, sec) { d -> go(d) } }
                if (wide) Row(Modifier.fillMaxSize().padding(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryTile(sec, Modifier.fillMaxHeight(0.82f).aspectRatio(1f))
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.weight(1f)) { content() }
                } else Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CategoryTile(sec, Modifier.size(200.dp))
                    Spacer(Modifier.height(24.dp))
                    content()
                }
            }
            SideArrow(left = false, enabled = idx < all.lastIndex) { go(1) }
        }
        Dock(s)
    }
}

/** El mosaico blanco de la categoría: su ícono grande, de color, y el nombre abajo. */
@Composable
private fun CategoryTile(sec: Section, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(36.dp), color = Color.White, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(sectionIcon(sec), null, Modifier.fillMaxWidth(0.5f).aspectRatio(1f), tint = sectionColor(sec))
            Spacer(Modifier.height(16.dp))
            Text(sectionName(sec), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2A2F3A), maxLines = 1)
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
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Section.entries.forEach { sec ->
            val cur = sec == s.section
            val lift by animateDpAsState(if (cur) (-10).dp else 0.dp, label = "lift")
            val scale by animateFloatAsState(if (cur) 1.25f else 1f, label = "scale")
            Box(Modifier.padding(horizontal = 14.dp).offset(y = lift).scale(scale).focusProperties { canFocus = false }
                .clip(RoundedCornerShape(14.dp)).background(if (cur) Color.White else Color.Transparent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.goTo(sec) }
                .padding(8.dp)) {
                Icon(sectionIcon(sec), sectionName(sec), Modifier.size(36.dp), tint = sectionColor(sec))
            }
        }
    }
}

/** Arriba a la derecha, chico (como la versión en la Wii U): la batería y la hora. */
@Composable
fun TopStatus() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); tick++ } }
    val level = remember(tick) {
        ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?.let { it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
    }
    val time = remember(tick) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 28.dp, top = 14.dp), horizontalArrangement = Arrangement.End) {
        Text(listOfNotNull(level?.let { "$it %" }, time).joinToString("   "), fontSize = 22.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}