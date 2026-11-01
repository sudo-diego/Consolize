package io.github.sudodiego.g9control

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.togetherWith
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
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

/** Los íconos de los cuatro temas de Más (en el mismo orden que [moreGroups]). */
val MORE_ICONS = listOf(Icons.Outlined.SportsEsports, Icons.Outlined.VideogameAsset, Icons.Outlined.TabletAndroid, Icons.Outlined.Settings)

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
fun CategoryHub(s: AppState, dest: String = s.dest) {
    val a = LocalContext.current as MainActivity
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        // El destino de ESTA pantalla (no el actual): mientras se va con su animación sigue mostrando lo suyo (antes, al
        // ir de Control a Panel se veía Inicio un instante y de Control a Más, Más dos veces).
        when (dest) {
            Dest.MORE -> MoreHub(s)
            Dest.CONTROL -> ControlHub(s)
            else -> HomeSummary(s, a)
        }
    }
}

/**
 * Control: a la izquierda el dibujo del control conectado (el del G9 con sus luces; para los demás, uno en líneas), su
 * nombre y debajo "conexión · batería" (lo que se sepa: la batería solo si Android la informa; el modo, solo del G9). A
 * la derecha, en dos columnas, lo de los controles (y lo del G9 si la app vio uno).
 */
@Composable
private fun ControlHub(s: AppState) {
    val c = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(3000); tick++ } }
    val pad = remember(tick) {
        android.view.InputDevice.getDeviceIds().toList().mapNotNull { android.view.InputDevice.getDevice(it) }
            .firstOrNull { !it.isVirtual && it.isExternal && (it.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD }
    }
    val g9 = pad?.name?.let { it.contains("G9", true) || it.contains("x-box 360", true) } == true || s.padMode != null
    val battery = remember(tick, pad?.id) {
        if (android.os.Build.VERSION.SDK_INT >= 31) pad?.batteryState?.takeIf { it.isPresent }?.capacity?.takeIf { it in 0f..1f }?.let { (it * 100).toInt() } else null
    }
    val info = listOfNotNull(if (g9) s.padMode?.label else null, battery?.let { "$it %" })
    val entries = entriesFor(s, Section.CONTROLS)
    Row(Modifier.widthIn(max = 1200.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(400.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (g9 && s.g9Known) ControllerPreview(s, Modifier.width(340.dp))
            else PadLineArt(Modifier.width(320.dp).graphicsLayer { alpha = if (pad == null) 0.35f else 1f })
            Spacer(Modifier.height(14.dp))
            Text(when { g9 -> "Legion G9"; pad != null -> pad.name; else -> tr("Sin control", "No controller") },
                fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = backdropOn())
            if (info.isNotEmpty()) Text(info.joinToString("  ·  "), fontSize = 15.sp, color = backdropMuted())
            else if (pad == null) Text(tr("Conecta uno para probarlo", "Connect one to test it"), fontSize = 15.sp, color = backdropMuted())
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { e -> EntryTile(s, e, Modifier.weight(1f)) }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Un control dibujado en líneas finas (para cualquier control que no sea el G9); el botón guía, del color de acento. */
@Composable
fun PadLineArt(modifier: Modifier) {
    val line = backdropOn().copy(alpha = 0.75f)
    val bumper = backdropOn().copy(alpha = 0.45f)
    val accent = MaterialTheme.colorScheme.primary
    val paths = remember {
        fun p(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
        listOf(
            p("M84 46 C120 38 200 38 236 46 C262 52 276 66 286 96 C298 134 302 166 284 176 C268 185 252 176 240 158 C232 146 224 140 210 140 L110 140 C96 140 88 146 80 158 C68 176 52 185 36 176 C18 166 22 134 34 96 C44 66 58 52 84 46Z"),
            p("M128 112v18M119 121h18"), p("M146 84h8M166 84h8"),
        ) to listOf(
            p("M60 51 C68 36 84 29 102 27 C113 26 121 28 127 32"), p("M260 51 C252 36 236 29 218 27 C207 26 199 28 193 32"),
        )
    }
    androidx.compose.foundation.Canvas(modifier.aspectRatio(320f / 190f)) {
        val k = size.width / 320f
        withTransform({ translate(0f, -10f * k); scale(k, k, androidx.compose.ui.geometry.Offset.Zero) }) {
            val thin = androidx.compose.ui.graphics.drawscope.Stroke(2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            paths.first.forEach { drawPath(it, line, style = thin) }
            paths.second.forEach { drawPath(it, bumper, style = androidx.compose.ui.graphics.drawscope.Stroke(5.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
            fun ring(x: Float, y: Float, r: Float, col: androidx.compose.ui.graphics.Color = line) = drawCircle(col, r, androidx.compose.ui.geometry.Offset(x, y), style = thin)
            ring(104f, 90f, 17f); ring(104f, 90f, 7f); ring(196f, 118f, 17f); ring(196f, 118f, 7f)
            ring(230f, 74f, 6f); ring(246f, 90f, 6f); ring(214f, 90f, 6f); ring(230f, 106f, 6f)
            ring(160f, 62f, 8f, accent)
        }
    }
}

/**
 * Más, como la configuración de una consola: los cuatro temas grandes (su nombre y lo que tienen). A abre el tema en un
 * menú encima (con un fundido y un leve crecimiento), con sus opciones como mosaicos; B lo cierra y el foco vuelve al
 * tema. En una opción, Y la fija en Inicio y X en el dock.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MoreHub(s: AppState) {
    val groups = moreGroups(s)
    val catFocus = remember { List(groups.size) { FocusRequester() } }
    val firstOption = remember { FocusRequester() }
    // El tema abierto (null = ninguno). Se recuerda al volver de una opción, así el menú sigue abierto.
    var open by remember { mutableStateOf(s.moreOpen) }
    LaunchedEffect(open) { s.moreOpen = open; if (open != null) { kotlinx.coroutines.delay(60); runCatching { firstOption.requestFocus() } } }
    DisposableEffect(open) {
        s.backOverride = if (open != null) ({ val k = open!!; open = null; s.moreOpen = null; runCatching { catFocus[k].requestFocus() }; true }) else null
        onDispose { s.backOverride = null }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Al llegar a Más, el foco entra directo al último tema elegido (antes pasaba por el primero y se notaba un salto).
        Row(Modifier.widthIn(max = 1200.dp).fillMaxWidth()
            .focusProperties { enter = { catFocus[s.moreCat.coerceIn(0, groups.size - 1)] } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            groups.forEachIndexed { i, (name, items) ->
                val src = remember { MutableInteractionSource() }
                val focused by src.collectIsFocusedAsState()
                LaunchedEffect(focused) { if (focused) s.moreCat = i }
                Surface(
                    onClick = { open = i }, interactionSource = src,
                    shape = RoundedCornerShape(24.dp), color = cardColor(), contentColor = cardOn(),
                    modifier = Modifier.weight(1f).height(260.dp).focusRequester(catFocus[i]).focusProperties { canFocus = open == null }
                        .graphicsLayer { alpha = if (open != null && open != i) 0.3f else 1f }
                        .focusLift(focused, src, 1.04f).bump(RoundedCornerShape(24.dp)).focusHalo(focused, RoundedCornerShape(24.dp))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        // Como un canal de consola: en vez de un ícono suelto, una ventanita con los íconos de lo que
                        // tiene adentro (los mismos de su menú). Con el foco la ventanita toma el acento.
                        val acc = MaterialTheme.colorScheme.primary
                        val win = androidx.compose.animation.animateColorAsState(
                            if (focused) acc else acc.copy(alpha = 0.12f), label = "ventana").value
                        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(win),
                            contentAlignment = Alignment.Center) {
                            val shown = items.take(4)
                            val extra = items.size - shown.size
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                shown.chunked(2).forEachIndexed { r, row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEachIndexed { j, e ->
                                            val last = r == 1 && j == 1 && extra > 0
                                            Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(cardBase()),
                                                contentAlignment = Alignment.Center) {
                                                if (last) Text("+${extra + 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = acc)
                                                else Icon(e.icon, null, Modifier.size(28.dp), tint = acc)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
                    }
                }
            }
        }
        // El menú del tema, encima: un velo sobre lo de atrás y la tarjeta con sus opciones.
        androidx.compose.animation.AnimatedVisibility(open != null, Modifier.matchParentSize(),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))) {
            Box(Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.backOverride?.invoke() },
                contentAlignment = Alignment.Center) {
                val k = open ?: s.moreCat
                val (name, items) = groups[k.coerceIn(0, groups.size - 1)]
                Surface(shape = RoundedCornerShape(28.dp), color = cardBase(), contentColor = cardOn(), shadowElevation = 16.dp,
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(0.8f)
                        .animateEnterExit(enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(200), initialScale = 0.94f),
                            exit = androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(140), targetScale = 0.96f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        items.chunked(2).forEachIndexed { r, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEachIndexed { j, e -> EntryTile(s, e, Modifier.weight(1f).then(if (r == 0 && j == 0) Modifier.focusRequester(firstOption) else Modifier), pins = true) }
                                if (row.size < 2) Spacer(Modifier.weight(1f))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            PadBadge("Y", round = true); Spacer(Modifier.width(6.dp))
                            Text(tr("Fijar en Inicio", "Pin to Home"), fontSize = 13.sp, color = cardMuted())
                            Spacer(Modifier.width(18.dp))
                            PadBadge("X", round = true); Spacer(Modifier.width(6.dp))
                            Text(tr("Fijar en el dock", "Pin to dock"), fontSize = 13.sp, color = cardMuted())
                            Spacer(Modifier.width(18.dp))
                            PadBadge("B", round = true); Spacer(Modifier.width(6.dp))
                            Text(tr("Cerrar", "Close"), fontSize = 13.sp, color = cardMuted())
                        }
                    }
                }
            }
        }
    }
}

/**
 * Texto y dibujos directo sobre el fondo de la app (no sobre una tarjeta): con imagen o fondo de pantalla, claros (se
 * leen sobre cualquier foto); con degradado o liso, los del tema. Se puede fijar en Apariencia.
 */
@Composable
fun backdropOn(): Color = when (ThemeState.backdropText) {
    BackdropText.LIGHT -> Color.White
    BackdropText.DARK -> Color(0xFF14203A)
    BackdropText.AUTO -> when {
        ThemeState.background == AppBackground.IMAGE || ThemeState.background == AppBackground.WALLPAPER -> Color.White
        ThemeState.consoleLook -> if (Color(ThemeState.stripeBase).luminance() > 0.4f) Color(0xFF2B3340) else Color.White
        else -> MaterialTheme.colorScheme.onBackground
    }
}
@Composable
fun backdropMuted(): Color = backdropOn().copy(alpha = 0.72f)

/**
 * Consola: las tarjetas "abultadas", con un borde sólido abajo un poco más oscuro (como un botón). Con otros fondos, nada.
 */
@Composable
fun Modifier.bump(shape: androidx.compose.ui.graphics.Shape): Modifier {
    if (!ThemeState.consoleLook) return this
    val lip = androidx.compose.ui.graphics.lerp(cardBase(), Color.Black, 0.16f)
    return drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        translate(0f, 3.dp.toPx()) { drawOutline(o, lip) }
    }
}

/** El color base de las tarjetas: la superficie del tema, que ya lleva lo elegido en Apariencia → Tarjetas. */
@Composable
fun cardBase(): Color = MaterialTheme.colorScheme.surfaceContainerHigh
/** El color de las tarjetas (mosaicos, temas, datos, dock): un poco translúcido sobre el fondo. */
@Composable
fun cardColor(): Color = cardBase().copy(alpha = if (ThemeState.glass) 0.88f else 1f)
/** Texto e íconos sobre las tarjetas: oscuros si la tarjeta es clara. */
@Composable
fun cardOn(): Color = MaterialTheme.colorScheme.onSurface
@Composable
fun cardMuted(): Color = cardOn().copy(alpha = 0.66f)

/**
 * Crecer con el foco (con un rebote suave, como en RomSync) y hundirse un poco al tocarlo: más vivo que cambiar de golpe.
 */
@Composable
fun Modifier.focusLift(focused: Boolean, src: androidx.compose.foundation.interaction.InteractionSource, grow: Float): Modifier {
    val pressed by src.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else if (focused) grow else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    return graphicsLayer { scaleX = k; scaleY = k }
}

/**
 * El foco de un mosaico: borde blanco (oscuro sobre tarjetas claras) y, si se eligió, un halo del color de acento que
 * sube y baja (Apariencia → Halo del foco).
 */
@Composable
fun Modifier.focusHalo(focused: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    val edge = cardOn()
    val halo = ThemeState.halo
    val pulse = if (focused && halo != Halo.OFF) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "halo")
        t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(if (halo == Halo.FAST) 650 else 1300), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse").value
    } else 0f
    return this
        .then(if (focused && halo != Halo.OFF) Modifier.drawBehind {
            val o = outlineFor(shape)
            for (i in 1..3) drawOutline(o, accent.copy(alpha = 0.16f * pulse / i), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (4 + i * 5).dp.toPx()))
        } else Modifier)
        .then(if (focused) Modifier.border(2.dp, edge, shape) else Modifier)
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.outlineFor(shape: androidx.compose.ui.graphics.Shape) = shape.createOutline(size, layoutDirection, this)

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
        shape = RoundedCornerShape(18.dp), color = cardColor(), contentColor = cardOn(),
        modifier = modifier.height(72.dp).focusRequester(focus)
            .focusLift(focused, src, 1.03f).bump(RoundedCornerShape(18.dp)).focusHalo(focused, RoundedCornerShape(18.dp))
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
            // Los mismos íconos de la ventanita del tema: del acento, sobre un tinte del acento.
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(e.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
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
    Surface(shape = RoundedCornerShape(22.dp), color = cardBase().copy(alpha = if (ThemeState.consoleLook) 1f else 0.92f), contentColor = cardOn(),
        shadowElevation = if (ThemeState.consoleLook) 0.dp else 8.dp, modifier = Modifier.bump(RoundedCornerShape(22.dp))) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            PadBadge("LB"); Spacer(Modifier.width(4.dp))
            dockItems(s).forEach { (id, label, icon) ->
                val cur = s.dest == id
                val bg by androidx.compose.animation.animateColorAsState(if (cur) c.primary else Color.Transparent, label = "dock")
                Row(Modifier.clip(RoundedCornerShape(15.dp)).background(bg).focusProperties { canFocus = false }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.goDest(id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val fg = if (cur) c.onPrimary else cardMuted()
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
        if (showBattery && level != null) { BatteryGlyph(level, charging = charging && !bypass, bypass = bypass, held = plugged && !charging && !bypass,
            ink = backdropOn(), accent = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
        Text(listOfNotNull(level?.takeIf { showBattery }?.let { "$it %" }, time).joinToString("   "), fontSize = 22.sp, fontWeight = FontWeight.Medium,
            color = backdropMuted())
    }
}