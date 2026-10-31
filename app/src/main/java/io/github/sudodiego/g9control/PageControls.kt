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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeyCombo(listOf(tr("Guía", "Guide")), Modifier.widthIn(min = 132.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, fontSize = 15.sp)
                            if (p == AutoRules.GuidePress.DOUBLE && st.value != AutoRules.GuideAction.NONE)
                                Text(tr("Tocar una vez espera un momento por si viene el segundo", "A single tap waits a moment for a second one"),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (LocalEditable.current) ChoiceChips(AutoRules.GuideAction.entries.map { it to it.label }, st.value) {
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
    var editKeys by remember { mutableStateOf(false) }
    val how = listOfNotNull(
        if (c == AutoRules.Chord.PANEL) (if (press == AutoRules.Press.TAP) tr("Tocar · mantener: captura", "Tap · hold: screenshot") else tr("Mantener", "Hold")) else null,
        when (where) {
            AutoRules.Where.ALL -> null
            AutoRules.Where.ONLY -> tr("Solo en ${apps.size} apps", "Only in ${apps.size} apps")
            AutoRules.Where.EXCEPT -> tr("Menos en ${apps.size} apps", "Except ${apps.size} apps")
        }
    ).joinToString(" · ").ifEmpty { null }
    ShortcutRow(listOf(keys.first.label, keys.second.label), how, c.label, on) { on = it; AutoRules.setChordOn(a, c, it) }
    if (!on || !LocalEditable.current) return
    // Los mismos dos botones que otro atajo encendido: gana el que va antes (Panel, Captura, Atrás). Se avisa.
    val clash = AutoRules.Chord.entries.firstOrNull { o ->
        o != c && AutoRules.chordOn(a, o) && AutoRules.chordKeys(a, o).let { setOf(it.first, it.second) } == setOf(keys.first, keys.second)
    }
    Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (clash != null) Text(tr("Los mismos botones que ${clash.short}: cambia uno de los dos.", "Same buttons as ${clash.short}: change one of them."),
            fontSize = 13.sp, color = col.error)
        TextButton(onClick = { editKeys = !editKeys }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(if (editKeys) tr("Listo", "Done") else tr("Cambiar botones", "Change buttons"))
        }
        if (editKeys) {
            Text(tr("Primer botón", "First button"), fontSize = 13.sp, color = col.onSurfaceVariant)
            ChoiceChips(AutoRules.PadKey.entries.map { it to it.label }, keys.first) { k ->
                val second = if (k == keys.second) AutoRules.PadKey.entries.first { it != k } else keys.second
                keys = k to second; AutoRules.setChordKeys(a, c, k, second)
            }
            Text(tr("Segundo botón", "Second button"), fontSize = 13.sp, color = col.onSurfaceVariant)
            ChoiceChips(AutoRules.PadKey.entries.filter { it != keys.first }.map { it to it.label }, keys.second) { k ->
                keys = keys.first to k; AutoRules.setChordKeys(a, c, keys.first, k)
            }
            Text(tr("El primero llega al juego; el segundo se lo queda el atajo.", "The first one reaches the game; the shortcut keeps the second."),
                fontSize = 12.sp, color = col.onSurfaceVariant)
        }
        if (c == AutoRules.Chord.PANEL) {
            ChoiceChips(AutoRules.Press.entries.map { it to it.label }, press) { press = it; AutoRules.setChordPress(a, it) }
            if (press == AutoRules.Press.HOLD) Text(
                tr("Los dos botones llegan al juego y el panel se abre si se mantienen un momento: sirve en juegos que usan la misma combinación.",
                    "Both buttons reach the game and the panel opens if they're held for a moment: works in games that use the same combo."),
                fontSize = 12.sp, color = col.onSurfaceVariant)
        }
        ChoiceChips(AutoRules.Where.entries.map { it to it.label }, where) { where = it; AutoRules.setChordWhere(a, c, it) }
        if (where != AutoRules.Where.ALL) {
            val pm = a.packageManager
            val named = apps.mapNotNull { p -> runCatching { p to pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrNull() }
                .sortedBy { it.second.lowercase() }
            if (named.isEmpty()) Text(tr("Ninguna app elegida.", "No apps chosen."), fontSize = 13.sp, color = col.onSurfaceVariant)
            else MultiChips(named, named.map { it.first }.toSet()) { p, keep ->
                apps = if (keep) apps + p else apps - p; AutoRules.setChordApps(a, c, apps)
            }
            Text(tr("Tocar una app la quita. Se agregan en Juegos → Biblioteca, en la ficha de cada una.",
                "Tap an app to remove it. Add them in Games → Library, on each app's page."), fontSize = 12.sp, color = col.onSurfaceVariant)
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
