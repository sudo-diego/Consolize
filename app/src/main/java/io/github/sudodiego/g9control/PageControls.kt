package io.github.sudodiego.g9control

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Controles: los atajos de la app con cualquier control, cada uno con su interruptor. El botón guía (Legion en el
 * G9, Xbox, PS, Home) con lo que hace tocarlo, tocarlo dos veces, mantenerlo y combinarlo; combinaciones de dos
 * botones (panel, captura, Atrás) para controles sin él; los botones de la tablet.
 */
@Composable
fun ShortcutsPage(s: AppState, a: MainActivity) {
    // Todos los atajos los hace el servicio de accesibilidad: sin él, oscurecidos.
    Gate(s.autoServiceOn) { ShortcutsContent(s, a) }
}

@Composable
private fun ShortcutsContent(s: AppState, a: MainActivity) {
    // Cada parte se abre sola desde la tarjeta de Controles (ver CATEGORY_ENTRIES).
    TwoPane(first = { GuidePanel(a) }, second = { OtherShortcutsPanel(a); TabletButtonsPanel(a) })
}

/** Íconos de lo que puede hacer la guía y sus combinaciones. */
private val AutoRules.GuideAction.icon: ImageVector get() = when (this) {
    AutoRules.GuideAction.NONE -> Icons.Outlined.Block; AutoRules.GuideAction.BACK -> Icons.AutoMirrored.Outlined.ArrowBack
    AutoRules.GuideAction.HOME -> Icons.Outlined.Home; AutoRules.GuideAction.RECENTS -> Icons.Outlined.Layers
    AutoRules.GuideAction.PANEL -> Icons.Outlined.Dashboard; AutoRules.GuideAction.SHOT -> Icons.Outlined.Screenshot
    AutoRules.GuideAction.NOTIFICATIONS -> Icons.Outlined.Notifications; AutoRules.GuideAction.QUICK_SETTINGS -> Icons.Outlined.ToggleOn
}
private val AutoRules.ComboAction.icon: ImageVector get() = when (this) {
    AutoRules.ComboAction.SHOT -> Icons.Outlined.Screenshot; AutoRules.ComboAction.BACK_HOLD -> Icons.AutoMirrored.Outlined.ArrowBack
    AutoRules.ComboAction.RECORD -> Icons.Outlined.Videocam; AutoRules.ComboAction.SHADOW -> Icons.Outlined.History
    AutoRules.ComboAction.PANEL -> Icons.Outlined.Dashboard; AutoRules.ComboAction.HOME -> Icons.Outlined.Home
    AutoRules.ComboAction.RECENTS -> Icons.Outlined.Layers
}

/**
 * Botón guía, visual: el control dibujado; tocar (•), tocar dos veces (••) y mantener (▬) como tres tarjetas con el
 * ícono de lo que hacen (A en una despliega las acciones); y las combinaciones como botones dibujados + un ícono:
 * tocar una la enciende o apaga (apagada, tenue) y mantenerla (o Y) despliega qué hace. Casi sin texto.
 */
@Composable
private fun GuidePanel(a: MainActivity) {
    var on by remember { mutableStateOf(AutoRules.legionKeys(a)) }
    val states = remember { AutoRules.Guide.entries.associateWith { mutableStateOf(AutoRules.guide(a, it)) } }
    val actions = remember { AutoRules.GuidePress.entries.associateWith { mutableStateOf(AutoRules.guideAction(a, it)) } }
    val combos = remember { AutoRules.GuideCombo.entries.associateWith { mutableStateOf(AutoRules.comboAction(a, it)) } }
    var burst by remember { mutableStateOf(AutoRules.burstShots(a)) }
    var streamTip by remember { mutableStateOf(AutoRules.streamTip(a)) }
    // Lo desplegado: una forma de apretar (tocar, dos veces, mantener) o una combinación.
    var openPress by remember { mutableStateOf<AutoRules.GuidePress?>(null) }
    var openCombo by remember { mutableStateOf<AutoRules.GuideCombo?>(null) }
    val s = LocalAppState.current
    DisposableEffect(openPress, openCombo) {
        val open = openPress != null || openCombo != null
        if (open) s?.backOverride = { openPress = null; openCombo = null; true }
        onDispose { if (open) s?.backOverride = null }
    }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Botón guía", "Guide button"), null,
            trailing = { Switch(on, { on = it; AutoRules.setLegionKeys(a, it) }, enabled = LocalGateOk.current) }) {
            val editable = on && LocalGateOk.current
            Row(Modifier.alpha(if (editable) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                PadLineArt(Modifier.width(240.dp))
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AutoRules.GuidePress.entries.forEach { p ->
                            val act = actions.getValue(p).value
                            PressCard(p, act, openPress == p, editable, Modifier.weight(1f)) { openCombo = null; openPress = if (openPress == p) null else p }
                        }
                    }
                    openPress?.let { p ->
                        OptionStrip(AutoRules.GuideAction.entries.map { Triple(it, it.icon, it.label) }, actions.getValue(p).value) {
                            actions.getValue(p).value = it; AutoRules.setGuideAction(a, p, it); openPress = null
                        }
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AutoRules.GuideCombo.entries.forEach { c ->
                            val st = states.getValue(c.guide)
                            val act = combos.getValue(c).value
                            ComboChip(c.key, act.icon, if (act == AutoRules.ComboAction.BACK_HOLD) "0,5 s" else null, st.value, openCombo == c, editable,
                                onToggle = { st.value = !st.value; AutoRules.setGuide(a, c.guide, st.value) },
                                onMenu = { openPress = null; openCombo = if (openCombo == c) null else c })
                        }
                        // Mantener la guía 5 s: el control se apaga y la tablet se bloquea.
                        val lock = states.getValue(AutoRules.Guide.HOLD_LOCK)
                        ComboChip("5 s", Icons.Outlined.Bedtime, null, lock.value, false, editable,
                            onToggle = { lock.value = !lock.value; AutoRules.setGuide(a, AutoRules.Guide.HOLD_LOCK, lock.value) }, onMenu = {})
                    }
                    openCombo?.let { c ->
                        OptionStrip(AutoRules.ComboAction.entries.map { Triple(it, it.icon, it.label) }, combos.getValue(c).value) {
                            combos.getValue(c).value = it; AutoRules.setComboAction(a, c, it)
                            if (it != AutoRules.ComboAction.SHOT) openCombo = null
                        }
                        // Con la captura: seguir manteniendo el botón saca una por segundo.
                        if (combos.getValue(c).value == AutoRules.ComboAction.SHOT)
                            SwitchRow(tr("Ráfaga al mantener", "Burst while held"), burst) { burst = it; AutoRules.setBurstShots(a, it) }
                    }
                }
            }
            SwitchTile(Icons.Outlined.Cast, tr("Aviso al transmitir", "Streaming notice"), streamTip) { streamTip = it; AutoRules.setStreamTip(a, it) }
        }
    }
}

/** Una forma de apretar la guía: los puntos (•, ••, ▬) con su nombre chico y, grande, el ícono y lo que hace. */
@Composable
private fun PressCard(p: AutoRules.GuidePress, act: AutoRules.GuideAction, open: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Surface(onClick = onClick, enabled = enabled, interactionSource = src, shape = shape, color = cardColor(), contentColor = cardOn(),
        modifier = modifier.focusLift(focused, src, 1.04f).bump(shape).focusHalo(focused || open, shape)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PressDots(p)
                Spacer(Modifier.width(8.dp))
                Text(p.label, fontSize = 12.sp, color = cardMuted())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (act == AutoRules.GuideAction.NONE) cardOn().copy(alpha = 0.12f) else c.primary), contentAlignment = Alignment.Center) {
                    Icon(act.icon, null, Modifier.size(24.dp), tint = if (act == AutoRules.GuideAction.NONE) cardOn() else c.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Text(act.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
    }
}

/** Cómo se aprieta: un punto (tocar), dos (dos veces) o una raya (mantener). */
@Composable
private fun PressDots(p: AutoRules.GuidePress) {
    val col = cardMuted()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (p) {
            AutoRules.GuidePress.TAP -> Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(col))
            AutoRules.GuidePress.DOUBLE -> repeat(2) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(col)) }
            AutoRules.GuidePress.HOLD -> Box(Modifier.size(width = 30.dp, height = 9.dp).clip(RoundedCornerShape(5.dp)).background(col))
        }
    }
}

/**
 * Una combinación: [guía] + [botón] y el ícono de lo que hace (con [note] chico debajo, como "0,5 s" en mantener Atrás).
 * Tocarla la enciende o apaga (apagada, tenue); mantenerla con el dedo, o Y con el control, despliega qué hace.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ComboChip(key: String, icon: ImageVector, note: String?, on: Boolean, open: Boolean, enabled: Boolean, onToggle: () -> Unit, onMenu: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.focusLift(focused, src, 1.05f).bump(shape).focusHalo(focused || open, shape).clip(shape).background(cardColor())
        .combinedClickable(interactionSource = src, indication = null, enabled = enabled, onClick = onToggle, onLongClick = onMenu)
        .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyUp && e.key == Key.ButtonY) { onMenu(); true } else false }
        .alpha(if (on) 1f else 0.4f)
        .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        // La guía: un círculo del color de acento con la casita.
        Box(Modifier.size(28.dp).border(2.dp, c.primary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Home, null, Modifier.size(15.dp), tint = c.primary)
        }
        Text("  +  ", fontSize = 13.sp, color = cardMuted(), fontWeight = FontWeight.Bold)
        Box(Modifier.height(28.dp).widthIn(min = 30.dp).border(2.dp, cardOn(), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
            Text(key, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cardOn())
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.primary)
            if (note != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(10.dp), tint = cardMuted())
                Text(note, fontSize = 9.sp, color = cardMuted())
            }
        }
    }
}

/** Las opciones desplegadas: cada una con su ícono y su nombre; la elegida, del color de acento. A elige; B cierra. */
@Composable
private fun <T> OptionStrip(options: List<Triple<T, ImageVector, String>>, selected: T, onPick: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.clip(RoundedCornerShape(18.dp)).background(cardOn().copy(alpha = 0.05f)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, icon, label) ->
            val sel = v == selected
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val shape = RoundedCornerShape(14.dp)
            Row(Modifier.then(if (sel) Modifier.focusRequester(first) else Modifier)
                .focusLift(focused, src, 1.05f).focusHalo(focused, shape).clip(shape).background(if (sel) c.primary else cardColor())
                .clickable(interactionSource = src, indication = null) { onPick(v) }
                .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (sel) c.onPrimary else cardOn())
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 14.sp, color = if (sel) c.onPrimary else cardOn())
            }
        }
    }
}

/**
 * Combinaciones de dos botones (para controles sin guía o con una guía que no llega): panel, captura y Atrás. Cada una
 * con sus botones y en qué apps funciona; las apps se quitan aquí y se agregan desde la ficha de cada una en la
 * biblioteca (Juegos → Biblioteca, con "Todas las apps" salen todas).
 */
@Composable
private fun OtherShortcutsPanel(a: MainActivity) {
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Combinaciones", "Button combos")) {
            AutoRules.Chord.entries.forEach { c -> ChordEditor(a, c) }
        }
    }
}

@Composable
private fun ChordEditor(a: MainActivity, c: AutoRules.Chord) {
    val col = MaterialTheme.colorScheme
    var on by remember { mutableStateOf(AutoRules.chordOn(a, c)) }
    var keys by remember { mutableStateOf(AutoRules.chordKeys(a, c)) }
    var where by remember { mutableStateOf(AutoRules.chordWhere(a, c)) }
    var apps by remember { mutableStateOf(AutoRules.chordApps(a, c)) }
    var press by remember { mutableStateOf(AutoRules.chordPress(a)) }
    ShortcutRow(listOf(keys.first.label, keys.second.label), null, c.label, on) { on = it; AutoRules.setChordOn(a, c, it) }
    if (!on || !LocalEditable.current) return
    // Los mismos dos botones que otro atajo encendido: gana el que va antes (Panel, Captura, Atrás).
    val clash = AutoRules.Chord.entries.firstOrNull { o ->
        o != c && AutoRules.chordOn(a, o) && AutoRules.chordKeys(a, o).let { setOf(it.first, it.second) } == setOf(keys.first, keys.second)
    }
    Column(Modifier.padding(start = 14.dp)) {
        if (clash != null) Text(tr("Mismos botones que ${clash.short}", "Same buttons as ${clash.short}"), fontSize = 13.sp, color = col.error)
        ChoiceRow(tr("Primer botón", "First button"), AutoRules.PadKey.entries.map { it to it.label }, keys.first) { k ->
            val second = if (k == keys.second) AutoRules.PadKey.entries.first { it != k } else keys.second
            keys = k to second; AutoRules.setChordKeys(a, c, k, second)
        }
        ChoiceRow(tr("Segundo botón", "Second button"), AutoRules.PadKey.entries.filter { it != keys.first }.map { it to it.label }, keys.second) { k ->
            keys = keys.first to k; AutoRules.setChordKeys(a, c, keys.first, k)
        }
        if (c == AutoRules.Chord.PANEL) ChoiceRow(tr("Se activa al", "Triggers on"),
            listOf(AutoRules.Press.TAP to tr("Tocar", "Tap"), AutoRules.Press.HOLD to tr("Mantener (llega al juego)", "Hold (reaches the game)")), press) {
            press = it; AutoRules.setChordPress(a, it)
        }
        ChoiceRow(tr("Funciona en", "Works in"), AutoRules.Where.entries.map { it to it.label }, where) { where = it; AutoRules.setChordWhere(a, c, it) }
        if (where != AutoRules.Where.ALL) AppsRow(if (where == AutoRules.Where.ONLY) tr("Apps", "Apps") else tr("Menos en", "Except in"), apps) {
            apps = it; AutoRules.setChordApps(a, c, it)
        }
    }
}

@Composable
private fun TabletButtonsPanel(a: MainActivity) {
    var swap by remember { mutableStateOf(AutoRules.swapVolume(a)) }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Botones de volumen", "Volume buttons")) {
            SwitchTile(Icons.Outlined.SwapVert, tr("Invertir el volumen", "Swap volume"), swap) {
                swap = it; AutoRules.setSwapVolume(a, it)
            }
            if (swap) {
                var rotations by remember { mutableStateOf(AutoRules.swapRotations(a)) }
                Text(tr("Solo en", "Only in"), fontSize = 14.sp)
                MultiChips(listOf(1 to tr("Horizontal", "Landscape"), 3 to tr("Horizontal invertido", "Reverse landscape"),
                    0 to tr("Vertical", "Portrait"), 2 to tr("Vertical invertido", "Reverse portrait")), rotations) { r, on ->
                    rotations = if (on) rotations + r else rotations - r
                    AutoRules.setSwapRotations(a, rotations)
                }
            }
        }
    }
}

/**
 * Un atajo: los botones dibujados (Guía + RB…), cómo se usan (tocar, mantener) y qué hacen, con su interruptor. Sin
 * botones: variante del atajo de arriba (↳).
 */
@Composable
private fun ShortcutRow(keys: List<String>, how: String?, action: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    val editable = LocalEditable.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = editable) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (keys.isEmpty()) Text("↳", Modifier.widthIn(min = 132.dp).padding(start = 14.dp), fontSize = 20.sp, color = c.onSurfaceVariant)
        else KeyCombo(keys, Modifier.widthIn(min = 132.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(action, fontSize = 15.sp, color = if (editable) c.onSurface else c.onSurface.copy(alpha = 0.5f))
            if (how != null) Text(how, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { Switch(checked, onChange, enabled = editable, modifier = Modifier.scale(0.85f)) }
    }
}
