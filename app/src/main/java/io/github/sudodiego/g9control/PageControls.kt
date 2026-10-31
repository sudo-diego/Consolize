package io.github.sudodiego.g9control

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

@Composable
private fun GuidePanel(a: MainActivity) {
    var on by remember { mutableStateOf(AutoRules.legionKeys(a)) }
    val states = remember { AutoRules.Guide.entries.associateWith { mutableStateOf(AutoRules.guide(a, it)) } }
    val actions = remember { AutoRules.GuidePress.entries.associateWith { mutableStateOf(AutoRules.guideAction(a, it)) } }
    var burst by remember { mutableStateOf(AutoRules.burstShots(a)) }
    var streamTip by remember { mutableStateOf(AutoRules.streamTip(a)) }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Botón guía", "Guide button"), null,
            trailing = { Switch(on, { on = it; AutoRules.setLegionKeys(a, it) }, enabled = LocalGateOk.current) }) {
            CompositionLocalProvider(LocalEditable provides (on && LocalGateOk.current)) {
                fun row(g: AutoRules.Guide) = states.getValue(g)
                // Tocar, tocar dos veces y mantener: cada uno hace lo que se elija.
                AutoRules.GuidePress.entries.forEach { p ->
                    val st = actions.getValue(p)
                    ChoiceRow(p.label, AutoRules.GuideAction.entries.map { it to it.label }, st.value, keys = listOf(tr("Guía", "Guide"))) {
                        st.value = it; AutoRules.setGuideAction(a, p, it)
                    }
                }
                ShortcutRow(listOf(tr("Guía", "Guide")), tr("Mantener 5 s (el control se apaga)", "Hold 5 s (the controller turns off)"), tr("Bloquear la pantalla", "Lock the screen"),
                    row(AutoRules.Guide.HOLD_LOCK).value) {
                    row(AutoRules.Guide.HOLD_LOCK).value = it; AutoRules.setGuide(a, AutoRules.Guide.HOLD_LOCK, it)
                }
                ShortcutRow(listOf(tr("Guía", "Guide"), "RB"), null, tr("Captura", "Screenshot"), row(AutoRules.Guide.RB_SHOT).value) {
                    row(AutoRules.Guide.RB_SHOT).value = it; AutoRules.setGuide(a, AutoRules.Guide.RB_SHOT, it)
                }
                ShortcutRow(emptyList(), tr("Seguir manteniendo RB", "Keep holding RB"), tr("Ráfaga, una por segundo", "Burst, one per second"), burst) {
                    burst = it; AutoRules.setBurstShots(a, it)
                }
                ShortcutRow(listOf(tr("Guía", "Guide"), "LB"), null, tr("Mantener Atrás", "Hold Back"),
                    row(AutoRules.Guide.LB_BACK).value) {
                    row(AutoRules.Guide.LB_BACK).value = it; AutoRules.setGuide(a, AutoRules.Guide.LB_BACK, it)
                }
                ShortcutRow(listOf(tr("Guía", "Guide"), "LT"), tr("En X-Input no", "Not in X-Input"), tr("Grabar o parar", "Record or stop"), row(AutoRules.Guide.LT_REC).value) {
                    row(AutoRules.Guide.LT_REC).value = it; AutoRules.setGuide(a, AutoRules.Guide.LT_REC, it)
                }
                ShortcutRow(listOf(tr("Guía", "Guide"), "RT"), tr("En X-Input no", "Not in X-Input"), tr("Shadow Rec: guardar lo último", "Shadow Rec: save the latest"), row(AutoRules.Guide.RT_SHADOW).value) {
                    row(AutoRules.Guide.RT_SHADOW).value = it; AutoRules.setGuide(a, AutoRules.Guide.RT_SHADOW, it)
                }
            }
            // Sin la nota larga de cómo se hace todo transmitiendo: lo dicen las filas de arriba ("Transmitiendo").
            SwitchRow(tr("Aviso al transmitir", "Notice when streaming"), streamTip, tr("Recuerda estos atajos en Artemis y Moonlight", "Reminds these shortcuts in Artemis and Moonlight")) {
                streamTip = it; AutoRules.setStreamTip(a, it)
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
            SwitchRow(tr("Invertir el volumen", "Swap volume buttons"), swap,
                null) {
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
