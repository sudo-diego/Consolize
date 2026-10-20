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
 * G9, Xbox, PS, Home) con lo que hace tocarlo, mantenerlo y combinarlo; L3 + R3 para controles sin él; lo propio del
 * G9 y los botones de la tablet.
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
    var burst by remember { mutableStateOf(AutoRules.burstShots(a)) }
    var streamTip by remember { mutableStateOf(AutoRules.streamTip(a)) }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Botón guía", "Guide button"), null,
            trailing = { Switch(on, { on = it; AutoRules.setLegionKeys(a, it) }, enabled = LocalGateOk.current) }) {
            CompositionLocalProvider(LocalEditable provides (on && LocalGateOk.current)) {
                fun row(g: AutoRules.Guide) = states.getValue(g)
                ShortcutRow(listOf(tr("Guía", "Guide")), tr("Tocar", "Tap"), tr("Atrás", "Back"), row(AutoRules.Guide.TAP_BACK).value) {
                    row(AutoRules.Guide.TAP_BACK).value = it; AutoRules.setGuide(a, AutoRules.Guide.TAP_BACK, it)
                }
                ShortcutRow(listOf(tr("Guía", "Guide")), tr("Mantener", "Hold"), tr("Panel de juego", "Game panel"), row(AutoRules.Guide.HOLD_PANEL).value) {
                    row(AutoRules.Guide.HOLD_PANEL).value = it; AutoRules.setGuide(a, AutoRules.Guide.HOLD_PANEL, it)
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


@Composable
private fun OtherShortcutsPanel(a: MainActivity) {
    var chord by remember { mutableStateOf(AutoRules.gamePanel(a)) }
    var onlyStream by remember { mutableStateOf(AutoRules.chordOnlyStreaming(a)) }
    var streamBack by remember { mutableStateOf(AutoRules.streamBack(a)) }
    CompositionLocalProvider(LocalEditable provides LocalGateOk.current) {
        Panel(tr("Sin botón guía", "Without a guide button")) {
            ShortcutRow(listOf("L3", "R3"), tr("Tocar · mantener: captura", "Tap · hold: screenshot"), tr("Panel de juego", "Game panel"), chord) {
                chord = it; AutoRules.setGamePanel(a, it)
            }
            // Con la guía, L3 + R3 sobra (y el primer stick llega al juego): se puede dejar solo para transmitir.
            if (chord) ChoiceChips(listOf(false to tr("En todas partes", "Everywhere"), true to tr("Solo transmitiendo", "Only when streaming")), onlyStream) {
                onlyStream = it; AutoRules.setChordOnlyStreaming(a, it)
            }
            ShortcutRow(listOf("Start", "Select"), tr("Transmitiendo (Artemis, Moonlight)", "While streaming (Artemis, Moonlight)"), tr("Menú de la app (Atrás)", "App menu (Back)"), streamBack) {
                streamBack = it; AutoRules.setStreamBack(a, it)
            }
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
