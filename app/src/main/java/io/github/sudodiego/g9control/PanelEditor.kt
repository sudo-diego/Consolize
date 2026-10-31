package io.github.sudodiego.g9control

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusRequester

/** El ícono de cada mosaico del panel (los mismos que usa el panel). */
internal val PanelTile.icon: ImageVector get() = when (this) {
    PanelTile.BRIGHTNESS -> Icons.Outlined.BrightnessMedium; PanelTile.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
    PanelTile.RECENTS -> Icons.Outlined.Layers; PanelTile.SHOT -> Icons.Outlined.Screenshot; PanelTile.RECORD -> Icons.Outlined.Videocam
    PanelTile.SHADOW -> Icons.Outlined.History; PanelTile.ACHIEVEMENTS -> Icons.Outlined.Star; PanelTile.GAME_ASSISTANT -> Icons.Outlined.Tune
    PanelTile.PERFORMANCE -> Icons.Outlined.Speed; PanelTile.BYPASS -> Icons.Outlined.Power; PanelTile.MONITOR -> Icons.Outlined.QueryStats
    PanelTile.REFRESH -> Icons.Outlined.DisplaySettings; PanelTile.LOCK_GESTURES -> Icons.Outlined.Lock; PanelTile.WIDE -> Icons.Outlined.AspectRatio
    PanelTile.ASPECT -> Icons.Outlined.Crop; PanelTile.DARK_SCREEN -> Icons.Outlined.TabletAndroid; PanelTile.WIFI -> Icons.Outlined.Wifi
    PanelTile.BLUETOOTH -> Icons.Outlined.Bluetooth; PanelTile.AIRPLANE -> Icons.Outlined.AirplanemodeActive; PanelTile.NIGHT_LIGHT -> Icons.Outlined.NightsStay
    PanelTile.READING -> Icons.AutoMirrored.Outlined.MenuBook; PanelTile.MOUSE -> Icons.Outlined.Mouse; PanelTile.BACK -> Icons.AutoMirrored.Outlined.ArrowBack
    PanelTile.HOME -> Icons.Outlined.Home; PanelTile.RECENTS_KEY -> Icons.Outlined.Layers; PanelTile.QUICK_SETTINGS -> Icons.Outlined.ToggleOn
    PanelTile.SLEEP -> Icons.Outlined.Bedtime; PanelTile.SETTINGS -> Icons.Outlined.Settings; PanelTile.G9_MODE -> Icons.Outlined.VideogameAsset
    PanelTile.APP -> Icons.AutoMirrored.Outlined.OpenInNew
}

/**
 * Editar el panel viéndolo, como los íconos de Android o iOS: las secciones y sus mosaicos tal como salen.
 * - Con el dedo: mantener un mosaico y arrastrarlo lo cambia de lugar; tocar uno lo toma y tocar otro lo pone ahí; el ojo
 *   lo oculta o lo muestra. Tocar el nombre de una sección la toma y tocar otra la pone en su lugar.
 * - Con el control: A toma el mosaico (o la sección), las flechas lo mueven y A lo suelta; Y lo oculta o lo muestra.
 * B suelta lo tomado o sale del editor.
 */
@Composable
fun PanelEditor(onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s = LocalAppState.current
    val cols = COLUMNS
    var picked by remember { mutableStateOf<PanelTile?>(null) }
    var pickedSec by remember { mutableStateOf<PanelRow?>(null) }
    // Arrastrando con el dedo: el mosaico, dónde está el dedo (en la pantalla) y dónde se lo tomó.
    var dragging by remember { mutableStateOf<PanelTile?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<PanelTile, Rect>() }
    val focus = remember { PanelTile.entries.associateWith { androidx.compose.ui.focus.FocusRequester() } }

    fun save(order: List<PanelTile>) { ThemeState.panelTileOrder = order; Prefs.setPanelTileOrder(ctx, order) }
    /** Lleva [t] a la posición [to] de su sección (los de las otras secciones no se mueven). */
    fun moveTo(t: PanelTile, to: Int) {
        val order = ThemeState.panelTileOrder
        val same = order.filter { it.section == t.section }.toMutableList()
        val from = same.indexOf(t); val dest = to.coerceIn(0, same.size - 1)
        if (from < 0 || from == dest) return
        same.add(dest, same.removeAt(from))
        val it = same.iterator()
        save(order.map { o -> if (o.section == t.section) it.next() else o })
    }
    fun indexOf(t: PanelTile) = ThemeState.panelTileOrder.filter { it.section == t.section }.indexOf(t)
    fun toggleHidden(t: PanelTile) {
        ThemeState.panelHidden = if (t in ThemeState.panelHidden) ThemeState.panelHidden - t else ThemeState.panelHidden + t
        Prefs.setPanelHidden(ctx, ThemeState.panelHidden)
    }
    fun moveSection(sec: PanelRow, d: Int) {
        val list = ThemeState.panelSections.toMutableList()
        val i = list.indexOf(sec); val j = (i + d).coerceIn(0, list.size - 1)
        if (i == j) return
        list.add(j, list.removeAt(i)); ThemeState.panelSections = list; Prefs.setPanelSections(ctx, list)
    }
    // B: suelta lo tomado o sale.
    DisposableEffect(Unit) {
        s?.backOverride = { if (picked != null || pickedSec != null) { picked = null; pickedSec = null } else onDone(); true }
        onDispose { s?.backOverride = null }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemeState.panelSections.forEach { sec -> key(sec) {
            val tiles = ThemeState.panelTileOrder.filter { it.section == sec }
            val secSrc = remember { MutableInteractionSource() }
            val secFocused by secSrc.collectIsFocusedAsState()
            val secPicked = pickedSec == sec
            // Nombre de la sección: tomarla y moverla (A o tocar; ↑ ↓ con el control; tocar otra la pone ahí).
            Row(Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (secPicked) c.primary.copy(alpha = 0.22f) else Color.Transparent)
                .then(if (secFocused) Modifier.border(2.dp, c.onSurface, RoundedCornerShape(10.dp)) else Modifier)
                .clickable(interactionSource = secSrc, indication = null) {
                    val p = pickedSec
                    pickedSec = when {
                        p == null -> sec
                        p == sec -> null
                        else -> { moveSection(p, ThemeState.panelSections.indexOf(sec) - ThemeState.panelSections.indexOf(p)); null }
                    }
                }
                .onPreviewKeyEvent { e ->
                    if (!secPicked || e.type != KeyEventType.KeyDown) false
                    else when (e.key) { Key.DirectionUp -> { moveSection(sec, -1); true }; Key.DirectionDown -> { moveSection(sec, 1); true }; else -> false }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (secPicked) { Icon(Icons.Outlined.OpenWith, null, Modifier.size(16.dp), tint = c.primary); Spacer(Modifier.width(6.dp)) }
                Text(sec.label.uppercase(), fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                    color = if (secPicked) c.primary else c.onSurfaceVariant)
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(maxItemsInEachRow = if (sec == PanelRow.SLIDER) 2 else cols,
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.forEach { t -> key(t) {
                        val hidden = t in ThemeState.panelHidden
                        val src = remember { MutableInteractionSource() }
                        val focused by src.collectIsFocusedAsState()
                        val isPicked = picked == t
                        val isDragged = dragging == t
                        val lift by animateFloatAsState(if (isPicked || isDragged) 1.08f else if (focused) 1.04f else 1f,
                            spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
                        val r = bounds[t]
                        Box(Modifier.weight(1f).height(60.dp)
                            .zIndex(if (isDragged) 10f else if (isPicked) 5f else 0f)
                            .onGloballyPositioned { bounds[t] = it.boundsInRoot() }
                            .graphicsLayer {
                                scaleX = lift; scaleY = lift
                                if (isDragged && r != null) { translationX = finger.x - grab.x - r.left; translationY = finger.y - grab.y - r.top }
                                shadowElevation = if (isPicked || isDragged) 18f else 0f
                                shape = RoundedCornerShape(16.dp); clip = false
                            }
                            .focusRequester(focus.getValue(t))
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isPicked || isDragged) c.primary.copy(alpha = 0.28f) else cardColor())
                            .then(if (focused || isPicked) Modifier.border(2.dp, if (isPicked) c.primary else cardOn(), RoundedCornerShape(16.dp)) else Modifier)
                            .pointerInput(t) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { p -> val b = bounds[t] ?: return@detectDragGesturesAfterLongPress
                                        dragging = t; picked = null; grab = p; finger = Offset(b.left + p.x, b.top + p.y) },
                                    onDragEnd = { dragging = null }, onDragCancel = { dragging = null }
                                ) { ch, d ->
                                    ch.consume(); finger += d
                                    // Sobre otro mosaico de su sección: se pone en su lugar (los demás se corren).
                                    val over = bounds.entries.firstOrNull { (o, b) -> o != t && o.section == t.section && b.contains(finger) }?.key
                                    if (over != null) moveTo(t, indexOf(over))
                                }
                            }
                            .clickable(interactionSource = src, indication = null) {
                                val p = picked
                                picked = when {
                                    p == null -> t
                                    p == t -> null
                                    p.section == t.section -> { moveTo(p, indexOf(t)); null }
                                    else -> t
                                }
                            }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                if (e.key == Key.ButtonY) { toggleHidden(t); return@onPreviewKeyEvent true }
                                if (!isPicked) return@onPreviewKeyEvent false
                                val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1
                                    Key.DirectionUp -> -(if (t.section == PanelRow.SLIDER) 2 else cols); Key.DirectionDown -> if (t.section == PanelRow.SLIDER) 2 else cols; else -> 0 }
                                if (d == 0) false else { moveTo(t, indexOf(t) + d); runCatching { focus.getValue(t).requestFocus() }; true }
                            }
                            .graphicsLayer { alpha = if (hidden && !isPicked) 0.4f else 1f },
                            contentAlignment = Alignment.CenterStart) {
                            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(cardOn().copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Icon(t.icon, null, Modifier.size(19.dp), tint = cardOn())
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(t.label, Modifier.weight(1f), fontSize = 13.sp, color = cardOn(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                // Ojo: mostrar u ocultar (con el dedo; con el control, Y).
                                Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null,
                                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { toggleHidden(t) }.padding(4.dp),
                                    tint = if (hidden) cardOn().copy(alpha = 0.6f) else cardOn().copy(alpha = 0.35f))
                            }
                        }
                    } }
            }
        } }
        // Lo que hacen los botones, chico y abajo.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            PadBadge("A", round = true); Spacer(Modifier.width(6.dp))
            Text(if (picked != null || pickedSec != null) tr("Soltar", "Drop") else tr("Mover", "Move"), fontSize = 13.sp, color = c.onSurfaceVariant)
            Spacer(Modifier.width(16.dp)); PadBadge("Y", round = true); Spacer(Modifier.width(6.dp))
            Text(tr("Ocultar", "Hide"), fontSize = 13.sp, color = c.onSurfaceVariant)
            Spacer(Modifier.width(16.dp)); PadBadge("B", round = true); Spacer(Modifier.width(6.dp))
            Text(tr("Listo", "Done"), fontSize = 13.sp, color = c.onSurfaceVariant)
        }
    }
}
