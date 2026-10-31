package io.github.sudodiego.g9control

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
// Con alias: este archivo tiene su propio Offset.scale (privado) que tapaba al de Compose.
import androidx.compose.ui.draw.scale as drawScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** true en pantallas anchas (tablet en horizontal): la app usa riel lateral y dos columnas. */
val LocalWide = compositionLocalOf { false }

/**
 * false cuando la app no puede escribir en el control (no está conectado en modo Android): los
 * controles de las páginas de ajustes se ven atenuados y no responden.
 */
val LocalEditable = compositionLocalOf { true }

/** Avisa cuando el dedo está sobre un slider: mientras tanto no se guarda ni corre la cuenta atrás. */
val LocalSliderHeld = compositionLocalOf<(Boolean) -> Unit> { {} }

/** Páginas cuyos controles escriben en el control. */
private val EDIT_PAGES = setOf(Page.LIGHTS, Page.STICKS, Page.VIBRATION)

/** Páginas que dependen del servicio de accesibilidad (toda o una parte). */
private val SERVICE_PAGES = setOf(Page.CONTROLS, Page.GAMEPANEL, Page.PANEL, Page.CAPTURES, Page.SCREEN, Page.AUTO, Page.BATTERY)

/** false dentro de un [Gate] cerrado: lo de adentro no se puede usar (ni aunque un panel diga editable). */
val LocalGateOk = compositionLocalOf { true }

/**
 * Lo que depende de un permiso (el servicio de accesibilidad, Shizuku): sin él se ve oscurecido y no responde. Así se
 * nota qué no se puede usar ahora; la franja de arriba de la página dice qué falta.
 */
@Composable
fun Gate(ok: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val open = ok && LocalGateOk.current
    Column(Modifier.alpha(if (open) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(if (LocalInSheet.current) 30.dp else 16.dp)) {
        CompositionLocalProvider(LocalEditable provides (open && LocalEditable.current), LocalGateOk provides open) { content() }
    }
}

/** Franja de lo que falta (un permiso, Shizuku): una línea con el ícono en color y el botón para arreglarlo. */
@Composable
fun NeedsBanner(text: String, action: String, onAction: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.tertiaryContainer.copy(alpha = 0.85f), shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Accessibility, null, Modifier.size(20.dp), tint = c.onTertiaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), fontSize = 14.sp, color = c.onTertiaryContainer)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private data class NavItem(val page: Page, val es: String, val en: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    val label get() = tr(es, en)
}

private val NAV = listOf(
    NavItem(Page.TABLET_HOME, "Inicio", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    NavItem(Page.BATTERY, "Batería", "Battery", Icons.Outlined.BatteryChargingFull, Icons.Filled.BatteryChargingFull),
    NavItem(Page.SCREEN, "Pantalla", "Screen", Icons.Outlined.Monitor, Icons.Filled.Monitor),
    NavItem(Page.GAMES, "Biblioteca", "Library", Icons.Outlined.GridView, Icons.Filled.GridView),
    NavItem(Page.GAMEPANEL, "General", "General", Icons.Outlined.Tune, Icons.Filled.Tune),
    NavItem(Page.PANEL, "Aspecto", "Look", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    NavItem(Page.CAPTURES, "Capturas", "Screenshots", Icons.Outlined.Screenshot, Icons.Filled.Screenshot),
    NavItem(Page.CONTROLS, "Atajos", "Shortcuts", Icons.Outlined.Gesture, Icons.Filled.Gesture),
    // G9 (en Controles, bajo "Legion G9"; Por app en Juegos).
    NavItem(Page.HOME, "Control", "Controller", Icons.Outlined.Home, Icons.Filled.Home),
    NavItem(Page.AUTO, "Cambio de modo", "Mode switching", Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz),
    NavItem(Page.LIGHTS, "Luces", "Lights", Icons.Outlined.Lightbulb, Icons.Filled.Lightbulb),
    NavItem(Page.STICKS, "Sticks", "Sticks", Icons.Outlined.Gamepad, Icons.Filled.Gamepad),
    NavItem(Page.VIBRATION, "Vibración", "Vibration", Icons.Outlined.Vibration, Icons.Filled.Vibration),
    NavItem(Page.TEST, "Probar control", "Test controller", Icons.Outlined.TouchApp, Icons.Filled.TouchApp),
    NavItem(Page.BACK, "Traseros", "Paddles", Icons.Outlined.FlipToBack, Icons.Filled.FlipToBack),
    NavItem(Page.PROFILES, "Perfiles", "Profiles", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    NavItem(Page.G9_PREFS, "Preferencias", "Preferences", Icons.Outlined.SettingsSuggest, Icons.Filled.SettingsSuggest),
    // Las dos.
    NavItem(Page.DIAGNOSTICS, "Ajustes", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(s: AppState, a: MainActivity) {
    val backdropLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    CapturedBackdrop(backdropLayer)
    CompositionLocalProvider(LocalBackdropLayer provides backdropLayer, LocalAppState provides s) {
    BoxWithConstraints(
        Modifier.fillMaxSize().safeDrawingPadding()
    ) {
        val wide = maxWidth >= 720.dp
        // Foco bien visible para navegar con el control (por defecto Material lo marca muy suave).
        val focusRipple = RippleConfiguration(
            color = MaterialTheme.colorScheme.primary,
            rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.30f, hoveredAlpha = 0.08f, pressedAlpha = 0.14f)
        )
        CompositionLocalProvider(
            // Texto fuera de los paneles con el color del tema (si no, queda negro en modo oscuro).
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalWide provides wide, LocalSliderHeld provides { s.sliderHeld = it }, LocalCentered provides s.centered,
            LocalRippleConfiguration provides focusRipple
        ) {
            // Como el inicio de una consola: arriba, dos barras de vidrio (secciones con LB/RB a la izquierda; batería y hora a
            // la derecha); al medio, los mosaicos de la sección o la página; abajo, la ayuda. Igual en horizontal y vertical.
            Column(Modifier.fillMaxSize()) {
                // En la tarjeta de Inicio la batería ya está en el resumen: arriba solo la hora.
                TopStatus()
                // La franja de Vista + Menú, solo en el inicio del G9 (en las demás páginas basta el aviso de cada una).
                if (s.page == Page.HOME && !s.inHub) ModeStrip(s)
                PageHost(s, a, Modifier.weight(1f))
                HintBar(s)
            }
            if (s.showSetup) SetupDialog(s, a) { s.showSetup = false; Prefs.setSetupDone(a) }
        }
    }
    }
}

/**
 * Barra lateral (pantalla ancha): las secciones una debajo de otra; la actual con sus pestañas debajo, con sangría
 * (las del G9 bajo "Legion G9"). Arriba y abajo, el botón que sube o baja por la lista (LT/RT; LB/RB donde LT/RT prueban
 * los gatillos). Se pliega a solo íconos mientras el foco está en la página (ver App).
 */
@Composable
private fun SideBar(s: AppState, a: MainActivity) {
    val c = MaterialTheme.colorScheme
    val compact = s.navCompact
    val focus = remember { Page.entries.associateWith { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    // Con el foco en la barra, LT/RT lo llevan a la pestaña nueva (si no, quedaba en la anterior).
    // Al cambiar de pestaña con un control, el foco va a la pestaña nueva (si no, Android lo dejaba en la primera).
    LaunchedEffect(s.page) { s.navCompact = false; kotlinx.coroutines.delay(50); if (hasFocus || s.anyPad) runCatching { focus.getValue(s.page).requestFocus() } }
    val width by androidx.compose.animation.core.animateDpAsState(if (compact) 88.dp else 220.dp, tween(220), label = "nav")
    Column(
        Modifier.width(width).fillMaxHeight().padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
            .onFocusChanged { hasFocus = it.hasFocus; if (it.hasFocus) s.navCompact = false }
    ) { GlassSurface(RoundedCornerShape(24.dp), Modifier.fillMaxSize(), tint = c.surfaceContainerHigh) { Column(Modifier.fillMaxSize()) {
        // LT/RT recorren la lista (LB/RB también); en Vibración y Sticks LT/RT prueban los gatillos, así que ahí LB/RB.
        val keys = if (s.page in TRIGGER_PAGES) "LB" to "RB" else "LT" to "RT"
        NavKey(keys.first, up = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Section.entries.forEach { sec ->
                val pages = NAV.filter { it.page in s.pages(sec) }
                val current = s.section == sec
                // Una sección de una sola pestaña se elige como pestaña; las demás llevan a su primera y se abren.
                SectionRow(sec, selected = current && pages.size == 1, current = current, compact = compact,
                    focus = if (pages.size == 1) focus.getValue(pages[0].page) else null) { s.goTo(sec) }
                // Las pestañas se despliegan al entrar y se pliegan al salir (aparecer de golpe se sentía brusco).
                androidx.compose.animation.AnimatedVisibility(
                    visible = current && pages.size > 1,
                    enter = androidx.compose.animation.expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = androidx.compose.animation.shrinkVertically(tween(180)) + fadeOut(tween(120))
                ) {
                    // Compacta: las pestañas en un grupo con fondo suave, para que se vea que son de esa sección.
                    Column(
                        if (compact) Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(c.onSurface.copy(alpha = 0.05f)).padding(vertical = 4.dp)
                        else Modifier.padding(start = 14.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        pages.forEach { item ->
                            if (item.page == Page.HOME && !compact) Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("LEGION G9", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = c.onSurfaceVariant)
                                if (s.padMode != null) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF34C759))) }
                            }
                            if (item.page == Page.HOME && compact) HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = c.outlineVariant)
                            SideItem(item, s.page == item.page, compact, focus.getValue(item.page)) { s.page = item.page }
                        }
                    }
                }
            }
        }
        // Conexión del G9: en sus páginas (tocarla vuelve a leer el control).
        if (s.page in G9_PAGES && !compact) Box(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp)) { ConnectionPill(s) { if (!s.busy) a.readAll() } }
        NavKey(keys.second, up = false)
    } } }
}

/** Arriba y abajo de la barra lateral: con qué botón se sube o se baja por la lista. */
@Composable
private fun NavKey(key: String, up: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PadBadge(key)
        Spacer(Modifier.width(4.dp))
        Icon(if (up) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


fun sectionIcon(sec: Section) = when (sec) {
    Section.HOME -> Icons.Outlined.Home
    Section.GAMES -> Icons.Outlined.SportsEsports
    Section.PANEL -> Icons.Outlined.Dashboard
    Section.CONTROLS -> Icons.Outlined.VideogameAsset
    Section.SYSTEM -> Icons.Outlined.TabletAndroid
    Section.SETTINGS -> Icons.Outlined.Settings
}

/** Fila de una sección: la actual con su nombre en el color de acento; [selected] (sin pestañas) con fondo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionRow(sec: Section, selected: Boolean, current: Boolean, compact: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(
            onClick = onClick, shape = RoundedCornerShape(14.dp),
            color = androidx.compose.animation.animateColorAsState(if (selected) c.primaryContainer else Color.Transparent, tween(200), label = "sec").value,
            border = if (focused) BorderStroke(2.dp, c.primary) else null,
            interactionSource = source, modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
        ) {
            val tint = when { selected -> c.onPrimaryContainer; current -> c.primary; else -> c.onSurfaceVariant }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start) {
                Icon(sectionIcon(sec), sectionName(sec), Modifier.size(22.dp), tint = tint)
                if (!compact) {
                    Spacer(Modifier.width(12.dp))
                    Text(sectionName(sec), fontSize = 16.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                        color = when { selected -> c.onPrimaryContainer; current -> c.primary; else -> c.onSurface }, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Pestaña de la barra lateral: ícono y nombre (compacta, solo el ícono); la elegida con fondo, el foco con contorno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideItem(item: NavItem, sel: Boolean, compact: Boolean, focus: FocusRequester, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(
            onClick = onClick, shape = RoundedCornerShape(14.dp),
            color = androidx.compose.animation.animateColorAsState(if (sel) c.primaryContainer else Color.Transparent, tween(200), label = "tab").value,
            border = if (focused) BorderStroke(2.dp, c.primary) else null,
            interactionSource = source, modifier = Modifier.fillMaxWidth().focusRequester(focus)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start) {
                Icon(if (sel) item.selectedIcon else item.icon, item.label, Modifier.size(20.dp),
                    tint = if (sel) c.onPrimaryContainer else c.onSurfaceVariant)
                if (!compact) {
                    Spacer(Modifier.width(10.dp))
                    Text(item.label, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (sel) c.onPrimaryContainer else c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Con la barra compacta no se ven los nombres: el de la página va arriba del contenido. */
@Composable
private fun PageTitle(s: AppState, section: Section, sub: String?) {
    // Abierto desde el dock (Biblioteca, Panel, algo fijado): su nombre del dock; desde una lista: el tema y lo abierto.
    val head = if (s.rootPage) dockItems(s).firstOrNull { it.first == s.dest }?.second ?: sub else sectionName(section)
    val title = listOfNotNull(head, sub.takeIf { it != head }, s.detail.takeIf { !s.inHub }).joinToString(" · ")
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
}
/** Pestañas horizontales, como en una consola: LB/RB a los lados para cambiar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabBar(s: AppState, a: MainActivity) {
    val c = MaterialTheme.colorScheme
    // Arriba: a la izquierda Tablet/G9 (LT RT), al centro las pestañas en una píldora (LB RB a sus lados) y a la
    // derecha el estado del G9.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Angosta (tablet en vertical): las secciones no elegidas solo con su ícono, como las pestañas; si no, no cabía.
        val compact = maxWidth < 900.dp
        // Alto fijo: en Ajustes (sin píldora de pestañas) la barra no sube y no salta al cambiar de sección.
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            PadBadge("LT")
            Spacer(Modifier.width(6.dp))
            SectionSwitch(s, compact)
            Spacer(Modifier.width(6.dp))
            PadBadge("RT")
            Spacer(Modifier.width(12.dp))
            // Con una sola página (Ajustes) no hay pestañas ni LB/RB. La píldora ocupa todo el ancho que queda y las
            // pestañas se reparten parejo (antes iba centrada y sobraba espacio a los lados).
            if (s.pages(s.section).size > 1) Row(
                Modifier.weight(1f).clip(CircleShape).background(c.surfaceContainerHigh.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            PadBadge("LB")
            // Si las pestañas no caben, la elegida se desplaza a la vista.
            val tabScroll = rememberScrollState()
            val tabX = remember { mutableStateMapOf<Page, Pair<Int, Int>>() }
            LaunchedEffect(s.page, tabX[s.page]) {
                val (x, w) = tabX[s.page] ?: return@LaunchedEffect
                val view = tabScroll.viewportSize
                if (x < tabScroll.value || x + w > tabScroll.value + view)
                    tabScroll.animateScrollTo((x + w / 2 - view / 2).coerceAtLeast(0))
            }
            val tabFocus = remember { Page.entries.associateWith { FocusRequester() } }
            var tabFocused by remember { mutableStateOf(false) }
            LaunchedEffect(s.page) { if (tabFocused) runCatching { tabFocus.getValue(s.page).requestFocus() } }
            // Angosta: desplazable (si no caben); ancha: repartidas en todo el ancho.
            Row(
                Modifier.weight(1f).then(if (compact) Modifier.horizontalScroll(tabScroll) else Modifier).padding(horizontal = 6.dp)
                    .onFocusChanged { tabFocused = it.hasFocus },
                horizontalArrangement = if (compact) Arrangement.spacedBy(2.dp) else Arrangement.SpaceEvenly
            ) {
                NAV.filter { it.page in s.pages(s.section) }.forEach { item ->
                    val sel = s.page == item.page
                    // El foco del control se marca con un contorno, no con relleno: con relleno parecía otra pestaña
                    // elegida (se veían dos resaltadas a la vez).
                    val source = remember { MutableInteractionSource() }
                    val focused by source.collectIsFocusedAsState()
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Surface(
                        onClick = { s.page = item.page },
                        shape = CircleShape,
                        color = if (sel) c.primaryContainer else Color.Transparent,
                        border = if (focused) BorderStroke(2.dp, c.primary) else null,
                        interactionSource = source,
                        modifier = Modifier.focusRequester(tabFocus.getValue(item.page))
                            .onGloballyPositioned { tabX[item.page] = it.positionInParent().x.toInt() to it.size.width }
                    ) {
                        // Como un carrusel: la elegida con su nombre, las demás solo el ícono (así caben todas).
                        Row(Modifier.animateContentSize().padding(horizontal = if (sel) 12.dp else if (compact) 6.dp else 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (sel) item.selectedIcon else item.icon, item.label, Modifier.size(22.dp),
                                tint = if (sel) c.onPrimaryContainer else c.onSurfaceVariant
                            )
                            if (sel) {
                                Spacer(Modifier.width(8.dp))
                                Text(item.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    }
                }
            }
            PadBadge("RB")
            }
            if (s.pages(s.section).size <= 1) Spacer(Modifier.weight(1f))
            // Conexión del G9: en sus páginas (tocarla vuelve a leer el control).
            if (s.page in G9_PAGES) { Spacer(Modifier.width(12.dp)); ConnectionPill(s) { if (!s.busy) a.readAll() } }
        }
    }
}

/** Las secciones en una píldora (pantalla angosta): la elegida con su nombre, las demás solo con su ícono. */
@Composable
private fun SectionSwitch(s: AppState, compact: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.clip(CircleShape).background(c.surfaceContainerHighest).padding(3.dp)) {
        Section.entries.forEach { sec ->
            val sel = s.section == sec
            Row(
                Modifier.clip(CircleShape).background(if (sel) c.primary else Color.Transparent)
                    .clickable { s.goTo(sec) }.padding(horizontal = if (sel) 14.dp else 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(sectionIcon(sec), sectionName(sec), Modifier.size(18.dp), tint = if (sel) c.onPrimary else c.onSurfaceVariant)
                if (sel) {
                    Spacer(Modifier.width(6.dp))
                    Text(sectionName(sec), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
                }
            }
        }
    }
}

/** Los botones de un atajo como chips ("Guía" + "RB"): el mismo dibujo en Controles → Atajos y en Inicio. */
@Composable
fun KeyCombo(keys: List<String>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        keys.forEachIndexed { i, k ->
            if (i > 0) Text(" + ", fontSize = 13.sp, color = c.onSurfaceVariant)
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.secondaryContainer).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text(k, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSecondaryContainer, maxLines = 1)
            }
        }
    }
}

/** Botón del control dibujado como etiqueta (LB, A, B…). */
@Composable
fun PadBadge(label: String, round: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(c.onSurface.copy(alpha = 0.85f))
            .then(if (round) Modifier.size(22.dp) else Modifier.padding(horizontal = 7.dp, vertical = 2.dp)),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.surface) }
}

/** Botón B con un aro que se llena mientras se mantiene (1 s o 3 s); al soltar vuelve a cero. */
@Composable
private fun HoldBadge(hold: Pair<Long, Long>?, ready: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(hold, ready) {
        // Cumplido (falta soltar): el aro queda lleno.
        if (ready) progress.snapTo(1f)
        else if (hold == null) progress.animateTo(0f, tween(200))
        else {
            val (since, ms) = hold
            val start = ((android.os.SystemClock.uptimeMillis() - since).toFloat() / ms).coerceIn(0f, 1f)
            progress.snapTo(start)
            progress.animateTo(1f, tween(((1f - start) * ms).toInt(), easing = LinearEasing))
        }
    }
    val ring = c.primary
    Box(
        Modifier.size(30.dp).drawBehind {
            val w = 3.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(ring.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            if (progress.value > 0f) drawArc(
                ring, -90f, 360f * progress.value, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round)
            )
        },
        contentAlignment = Alignment.Center
    ) { PadBadge("B", round = true) }
}

/**
 * Barra inferior: a la izquierda los avisos (se borran solos salvo los problemas) y a la derecha cómo
 * se navega con el control, solo si hay un control conectado.
 */
@Composable
private fun HintBar(s: AppState) {
    val c = MaterialTheme.colorScheme
    val problem = s.statusError
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(s.status) {
        shown = true
        if (!problem) { kotlinx.coroutines.delay(4000); shown = false }
    }
    // Vidrio como la barra lateral: sin fondo, sobre una imagen o el fondo de pantalla no se leía.
    val glass = Modifier.clip(CircleShape).background(c.surfaceContainerHigh.copy(alpha = 0.75f))
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Estado de guardado de la página (siempre en el mismo lugar).
        val save: (@Composable () -> Unit)? = when (s.page) {
            Page.LIGHTS -> { { SaveStatus(s.lightsDirty, s.appliedLeft != null && s.connected, s.lightsChangedAt,
                MainActivity.LIGHTS_SAVE_MS, held = s.sliderHeld, quietMs = MainActivity.LIGHTS_QUIET_MS, modifier = Modifier) } }
            Page.STICKS -> { { SaveStatus(s.sticksDirty, s.appliedDeadZones != null && s.connected, modifier = Modifier) } }
            Page.VIBRATION -> { { SaveStatus(s.vibrationDirty, s.appliedVibration != null && s.connected, modifier = Modifier) } }
            else -> null
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        if (save != null) { Box(glass.padding(horizontal = 14.dp, vertical = 6.dp)) { save() }; Spacer(Modifier.width(12.dp)) }
        Box(Modifier.weight(1f)) {
            if (shown && s.status.isNotEmpty()) Text(
                s.status, glass.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (problem) c.error else c.onSurface
            )
        }
        }
        Spacer(Modifier.width(12.dp))
        DockBar(s)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (s.anyPad || s.padMode != null || s.connected) {
            val wide = LocalWide.current
            val hints = if (s.capturing) listOf("B" to tr("Mantén 1 s para salir", "Hold 1 s to exit"))
            else if (s.editingMacro != null) listOf("A" to tr("Elegir", "Select"), "B" to tr("Cerrar macro", "Close macro"))
            else listOfNotNull("A" to tr("Elegir", "Select"),
                "B" to if (s.bReady) tr("Suelta para salir", "Release to exit") else if (!s.atTop) tr("Volver", "Back") else tr("Mantén 2 s para salir", "Hold 2 s to exit"))
            Row(glass.padding(start = 6.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                hints.forEachIndexed { i, (k, v) ->
                    if (i > 0) Spacer(Modifier.width(18.dp))
                    // El aro de B es la cuenta atrás de mantener (salir de la app, de la prueba); cerrar la macro es un toque.
                    if (k == "B" && s.editingMacro == null && s.atTop) HoldBadge(s.bHold, s.bReady) else if (k.length == 1) PadBadge(k, round = true) else PadBadge(k)
                    Spacer(Modifier.width(6.dp))
                    Text(v, fontSize = 13.sp, color = c.onSurface, maxLines = 1)
                }
            }
        }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PageHost(s: AppState, a: MainActivity, modifier: Modifier) {
    // Al cambiar de pestaña se termina la prueba de botones.
    LaunchedEffect(s.page) { if (s.page != Page.TEST) s.testMode = false }
    // Atrás de Android (gesto o botón) también cierra el menú abierto.
    androidx.activity.compose.BackHandler(enabled = !s.atTop) { s.back() }
    // Probando botones o grabando una macro, la guía es un botón más (no abre el panel).
    LaunchedEffect(s.capturing) { AppKeys.capturing = s.capturing }
    // El marco (tarjeta, flechas y categorías abajo) queda fijo; solo lo de adentro cambia con un fundido, sin cambiar de
    // tamaño.
    Box(modifier.padding(horizontal = 16.dp)) { CategoryFrame(s) {
    AnimatedContent(
        // Lo que se ve, completo: al volver con B, lo que se va sigue mostrando lo suyo mientras se desvanece (antes
        // leía el estado nuevo y por un instante salía otra parte de la página).
        targetState = Screen(s.dest, s.section, if (s.inHub) null else s.page, s.sub, s.only),
        transitionSpec = {
            val ids = dockItems(s).map { it.first }
            val from = ids.indexOf(initialState.dest); val to = ids.indexOf(targetState.dest)
            // Entre destinos del dock, lo de adentro se desliza hacia el lado al que se va; entrar o volver, fundido.
            val move = if (from == to || from < 0 || to < 0) null else if (to > from) 1 else -1
            val enter = if (move == null) fadeIn(tween(160)) else slideInHorizontally(tween(220)) { it / 6 * move } + fadeIn(tween(220))
            val exit = if (move == null) fadeOut(tween(120)) else slideOutHorizontally(tween(220)) { -it / 6 * move } + fadeOut(tween(160))
            (enter togetherWith exit).using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() })
        },
        label = "page"
    ) { (_, section, pageOrHub, sub, only) ->
        val page = pageOrHub ?: s.page
        val scroll = rememberScrollState()
        // Al abrir un menú, el foco entra a su primera opción (si no, se perdía y saltaba a la barra lateral).
        val enter = remember { FocusRequester() }
        // Al entrar a cualquier pantalla (mosaicos, página, menú) el foco va a lo primero, salvo que una fila lo recupere.
        LaunchedEffect(Unit) { val restoring = s.lastSub != null; kotlinx.coroutines.delay(80); if (!restoring) runCatching { enter.requestFocus() } }
        // Al volver a la app (cerrar el panel de juego, por ejemplo) el foco vuelve a donde estaba (focusRestorer).
        val winFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        var hadWin by remember { mutableStateOf(winFocused) }
        LaunchedEffect(winFocused) { if (winFocused && !hadWin) { kotlinx.coroutines.delay(60); runCatching { enter.requestFocus() } }; hadWin = winFocused }
        if (pageOrHub == null) Box(Modifier.fillMaxSize().focusRequester(enter).focusRestorer().focusGroup()) { CategoryHub(s) }
        else CompositionLocalProvider(LocalOnlyPanels provides only) { Column(
            Modifier.fillMaxSize().focusRequester(enter).focusRestorer().focusGroup().verticalScroll(scroll)
                .padding(horizontal = 36.dp).padding(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PageTitle(s, section, sub)
            // Páginas que no hacen nada sin el servicio de accesibilidad (dibujar sobre los juegos, leer el control en
            // cualquier app, pedir la frecuencia): lo dicen arriba, con el botón para activarlo.
            if (page in SERVICE_PAGES && !s.autoServiceOn) NeedsBanner(
                if (page == Page.BATTERY) tr("Lo automático de esta página necesita el servicio de accesibilidad.", "The automatic features here need the accessibility service.")
                else tr("Esta página necesita el servicio de accesibilidad.", "This page needs the accessibility service."),
                tr("Activar", "Turn on")) { AutoRules.openServiceSettings(a) }
            val editable = page !in EDIT_PAGES || s.connected
            CompositionLocalProvider(LocalEditable provides editable) {
                Column(
                    Modifier.alpha(if (editable) 1f else 0.5f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (page) {
                        Page.HOME -> HomePage(s, a)
                        Page.LIGHTS -> LightsPage(s, a)
                        Page.STICKS -> SticksPage(s, a)
                        Page.VIBRATION -> VibrationPage(s, a)
                        Page.PROFILES -> ProfilesPage(s, a)
                        Page.AUTO -> AutoPage(s, a)
                        Page.TEST -> ControlsPage(s, a)
                        Page.BACK -> BackPage(s, a)
                        Page.TABLET_HOME -> TabletHomePage(s, a)
                        Page.BATTERY -> BatteryPage(s, a)
                        Page.SCREEN -> ScreenPage(s, a)
                        Page.GAMES -> CompositionLocalProvider(LocalPageScroll provides scroll) { GamesPage(s, a) }
                        Page.GAMEPANEL -> GamePanelPage(s, a)
                        Page.CAPTURES -> CapturesPage(s, a)
                        Page.PANEL -> CompositionLocalProvider(LocalPageScroll provides scroll) { PanelPage(s, a) }
                        Page.CONTROLS -> ShortcutsPage(s, a)
                        Page.G9_PREFS -> G9PrefsPage(s, a)
                        Page.DIAGNOSTICS -> DiagnosticsPage(s, a)
                        Page.USAGE -> UsagePage(s, a)
                    }
                }
            }
        } }
    }
    } }
}

@Composable
fun ConnectionPill(s: AppState, onConnect: () -> Unit) {
    val (text, color) = when {
        s.connected -> tr("Conectado", "Connected") to Color(0xFF3DDC84)
        s.padMode == PadMode.XINPUT -> tr("En X-Input", "In X-Input") to MaterialTheme.colorScheme.tertiary
        s.padMode == PadMode.DS5 -> tr("En DS5", "In DS5") to MaterialTheme.colorScheme.tertiary
        else -> tr("Sin conexión", "Not connected") to MaterialTheme.colorScheme.error
    }
    // Se tiene que leer sobre cualquier fondo (imagen o fondo de pantalla): fondo de la barra, borde del color del
    // estado y texto claro (antes, texto de color sobre un tinte de 14 % casi no se veía).
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
        modifier = Modifier.clip(CircleShape).clickable(onClick = onConnect)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Componentes compartidos
// ---------------------------------------------------------------------------------------------

/** Lo abierto en cada acordeón, mientras la app sigue abierta: al volver a la página queda como estaba. */
private val accordionMemory = mutableMapOf<String, String?>()

/** El acordeón de [key] (una página): empieza con [default] abierta o con lo que se dejó la última vez. */
@Composable
fun rememberAccordion(key: String, default: String?): MutableState<String?> {
    val st = remember(key) { mutableStateOf(if (accordionMemory.containsKey(key)) accordionMemory[key] else default) }
    LaunchedEffect(key, st.value) { accordionMemory[key] = st.value }
    return st
}

/** Secciones en acordeón: el título de la abierta (null = ninguna). Solo donde se pide (opciones del panel). */
val LocalAccordion = compositionLocalOf<MutableState<String?>?> { null }

/** Las partes de la página que se ven (abiertas desde la tarjeta de la categoría); null = todas. */
val LocalOnlyPanels = compositionLocalOf<Set<String>?> { null }

/** Dentro de una hoja ([Sheet]): las secciones no llevan tarjeta propia. */
val LocalInSheet = compositionLocalOf { false }

/**
 * Hoja: una superficie por columna de la página, con sus secciones adentro separadas por espacio y un título chico
 * (antes, cada grupo de dos o tres opciones era una tarjeta de vidrio y la pantalla se llenaba de rectángulos).
 */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Ya dentro del marco de la categoría: sin otra superficie encima.
    if (LocalInSheet.current) { Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(30.dp), content = content); return }
    GlassSurface(RoundedCornerShape(28.dp), modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalInSheet provides true) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(30.dp), content = content)
        }
    }
}

/**
 * Un grupo de opciones. En una hoja, una sección: título chico en mayúsculas espaciadas y sus filas. Suelta (Inicio,
 * Biblioteca, la vista previa), una tarjeta: muestra una cosa.
 */
@Composable
fun Panel(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.colorScheme
    // Abierta una sola parte de la página desde la tarjeta de su categoría: las demás no se dibujan.
    LocalOnlyPanels.current?.let { only -> if (title == null || title !in only) return }
    // En acordeón (opciones del panel): una fila con su nombre que se abre con A; abierta, una sola a la vez.
    val acc = LocalAccordion.current
    if (acc != null && title != null) {
        val open = acc.value == title
        val src = remember { MutableInteractionSource() }
        val focused by src.collectIsFocusedAsState()
        // Sin espacio fijo entre la fila y lo de adentro: va dentro de la animación (si no, al terminar de cerrarse el
        // espacio desaparecía de golpe y lo de abajo saltaba).
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (focused) c.primary.copy(alpha = 0.14f) else if (open) c.onSurface.copy(alpha = 0.05f) else Color.Transparent)
                .then(if (focused) Modifier.border(2.dp, c.primary, RoundedCornerShape(16.dp)) else Modifier)
                .clickable(interactionSource = src, indication = null) { acc.value = if (open) null else title }
                .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = if (open || focused) FontWeight.SemiBold else FontWeight.Medium)
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = c.onSurfaceVariant)
            }
            androidx.compose.animation.AnimatedVisibility(open,
                enter = androidx.compose.animation.expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = androidx.compose.animation.shrinkVertically(tween(200)) + fadeOut(tween(140))) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically) { Spacer(Modifier.weight(1f)); trailing() }
                    content()
                }
            }
        }
        return
    }
    if (LocalInSheet.current) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = c.primary)
                    if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            content()
        }
        return
    }
    GlassSurface(MaterialTheme.shapes.large, modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}
/** Diseño elegido en Ajustes: dibujo del control al centro o en columna. */
val LocalCentered = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Página con el dibujo del control: en columnas (dibujo a la izquierda, [side] a la derecha) o, con
 * "dibujo al centro", el dibujo arriba y centrado y [side] debajo en dos columnas.
 */
@Composable
fun DrawingPane(
    drawing: @Composable ColumnScope.() -> Unit, side: @Composable ColumnScope.() -> Unit
) {
    // En horizontal, el dibujo a la izquierda (a tamaño razonable) y lo demás a la derecha: en una columna el mando quedaba
    // gigante y tapaba las opciones.
    if (LocalWide.current) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp), content = drawing)
        Column(Modifier.weight(1f)) { Sheet(content = side) }
    } else Centered {
        drawing()
        Sheet(content = side)
    }
}

/** Dos columnas en pantallas anchas; una debajo de la otra en angostas. */
@Composable
fun TwoPane(first: @Composable ColumnScope.() -> Unit, second: @Composable ColumnScope.() -> Unit, sheets: Boolean = true) {
    // Una sola columna centrada (antes dos, lado a lado: demasiado a la vista). Con [sheets], en una hoja; Inicio no.
    Centered {
        if (sheets) Sheet { first(); second() }
        else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { first(); second() }
    }
}

/** El contenido de la página al centro, con un ancho cómodo de leer (en pantalla ancha no se estira de lado a lado). */
@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/** La app, para lo que la necesita sin recibirla (las filas de menú recuperan el foco al volver). */
val LocalAppState = compositionLocalOf<AppState?> { null }

/**
 * Lista de una página, al estilo de los ajustes de una consola: filas grandes, cada una abre su menú ([MenuRow]).
 * Lo que antes se veía todo junto (muchos interruptores) queda adentro de cada fila.
 */
@Composable
fun MenuList(content: @Composable ColumnScope.() -> Unit) = Centered {
    Sheet { CompositionLocalProvider(LocalInMenu provides true) { Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content) } }
}

private val LocalInMenu = compositionLocalOf { false }

/**
 * Una fila que abre un menú: ícono en una insignia, nombre, su estado a la derecha ("6 activos", "Apagado") y ›.
 * [onClick] null: la fila no abre nada y lleva [trailing] (un interruptor, por ejemplo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuRow(icon: ImageVector, title: String, value: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val s = LocalAppState.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    // Al volver de su menú, el foco vuelve a esta fila.
    LaunchedEffect(Unit) { if (s != null && s.lastSub == title && s.sub == null) { runCatching { focus.requestFocus() }; s.lastSub = null } }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(
            onClick = { onClick?.invoke() }, enabled = LocalEditable.current, shape = RoundedCornerShape(18.dp),
            color = if (focused) c.primary.copy(alpha = 0.12f) else Color.Transparent,
            border = if (focused) BorderStroke(2.dp, c.primary) else null,
            interactionSource = source, modifier = Modifier.fillMaxWidth().focusRequester(focus)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = c.primary)
                }
                Spacer(Modifier.width(16.dp))
                Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (value != null) Text(value, fontSize = 15.sp, color = c.onSurfaceVariant, maxLines = 1)
                if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
                if (onClick != null) { Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.ChevronRight, null, Modifier.size(24.dp), tint = c.onSurfaceVariant) }
            }
        }
    }
}

/**
 * Estado del guardado automático: los cambios se escriben solos en el control poco después
 * de dejar de mover un control de la pantalla. [known] = ya se leyó la configuración del control.
 */
@Composable
fun SaveStatus(
    dirty: Boolean, known: Boolean, changedAt: Long = 0L, waitMs: Long = 0L,
    held: Boolean = false, quietMs: Long = 0L, modifier: Modifier = Modifier.fillMaxWidth()
) {
    val c = MaterialTheme.colorScheme
    // Cuenta atrás hasta el guardado (si la página la usa). No corre con el dedo sobre un slider ni
    // durante [quietMs] después del último cambio (por ejemplo, tocando + varias veces).
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(changedAt, dirty, held) {
        progress.snapTo(0f)
        if (!dirty || waitMs <= 0 || changedAt <= 0) { pending = false; return@LaunchedEffect }
        pending = true
        if (held) return@LaunchedEffect
        val elapsed = android.os.SystemClock.uptimeMillis() - changedAt
        if (elapsed < quietMs) kotlinx.coroutines.delay(quietMs - elapsed)
        pending = false
        val start = ((elapsed - quietMs).toFloat() / waitMs).coerceIn(0f, 1f)
        progress.snapTo(start)
        progress.animateTo(1f, androidx.compose.animation.core.tween(((1f - start) * waitMs).toInt(),
            easing = androidx.compose.animation.core.LinearEasing))
    }
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            !known -> {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(tr("Conecta el control para guardar los cambios.", "Connect the controller to save changes."), fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            dirty && waitMs > 0 && pending -> {
                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(tr("Cambios sin guardar", "Unsaved changes"), fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            dirty && waitMs > 0 && progress.value < 1f -> {
                Text(tr("Guardando en ${waitMs / 1000} s…", "Saving in ${waitMs / 1000} s…"), fontSize = 13.sp, color = c.primary)
                Spacer(Modifier.width(12.dp))
                LinearProgressIndicator(
                    progress = { progress.value }, Modifier.width(140.dp).height(6.dp),
                    trackColor = c.surfaceContainerHighest, drawStopIndicator = {}
                )
            }
            dirty -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(tr("Guardando…", "Saving…"), fontSize = 13.sp, color = c.primary)
            }
            else -> {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Color(0xFF3DDC84))
                Spacer(Modifier.width(8.dp))
                Text(tr("Guardado en el control", "Saved to the controller"), fontSize = 13.sp, color = c.onSurfaceVariant)
            }
        }
    }
}

/**
 * Botón que borra o reinicia algo: el primer toque pregunta ("¿Seguro? Toca otra vez") y solo el segundo lo hace;
 * si no se confirma en 4 s vuelve a su estado. Así un toque sin querer (o A apretado de más) no borra nada.
 */
@Composable
fun ConfirmButton(label: String, enabled: Boolean = true, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(4000); armed = false } }
    TextButton(onClick = { if (armed) { armed = false; onConfirm() } else armed = true }, enabled = enabled) {
        Text(if (armed) tr("¿Seguro? Toca otra vez", "Sure? Tap again") else label,
            color = if (armed) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
    }
}

@Composable
fun Hint(text: String, icon: ImageVector = Icons.Outlined.Info) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Fila con etiqueta a la izquierda y valor a la derecha, sobre un slider. */
@Composable
/**
 * Barra con − y + a los lados (de a 1). [snaps]: valores recomendados donde la barra "se pega" al arrastrar cerca
 * (a ±[snapPull]), para elegir libre pero caer fácil en los sugeridos; se marcan con una rayita bajo la barra.
 */
fun SliderRow(
    label: String, value: Int, range: IntRange, valueText: String = "$value",
    snaps: List<Int> = emptyList(), snapPull: Int = 2,
    onChange: (Int) -> Unit
) {
    // Mismo estilo que los sliders de color, con − y + a los lados para ajustar de a 1.
    val c = MaterialTheme.colorScheme
    val span = (range.last - range.first).coerceAtLeast(1)
    val editable = LocalEditable.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(range.first)) }, enabled = editable && value > range.first) {
                Icon(Icons.Filled.Remove, tr("Bajar 1", "Down 1"))
            }
            Box(Modifier.weight(1f)) {
                GradientTrack(
                    (value - range.first).toFloat() / span,
                    Brush.horizontalGradient(listOf(c.primary.copy(alpha = 0.18f), c.primary)), c.primary,
                    keyStep = 1f / span
                ) { f ->
                    val raw = (range.first + Math.round(f * span)).coerceIn(range.first, range.last)
                    onChange(snaps.firstOrNull { kotlin.math.abs(it - raw) <= snapPull } ?: raw)
                }
                // Las marcas de los recomendados, bajo la barra.
                if (snaps.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                    // Mismo recorrido que la perilla de GradientTrack (16 dp de margen a cada lado), si no quedaban corridas.
                    val r = 16.dp.toPx()
                    snaps.forEach { s ->
                        val x = r + (size.width - 2 * r) * (s - range.first) / span
                        drawLine(c.primary.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(x, center.y + 9.dp.toPx()),
                            androidx.compose.ui.geometry.Offset(x, center.y + 14.dp.toPx()), strokeWidth = 2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
            IconButton(onClick = { onChange((value + 1).coerceAtMost(range.last)) }, enabled = editable && value < range.last) {
                Icon(Icons.Filled.Add, tr("Subir 1", "Up 1"))
            }
        }
    }
}

/** Slider con fondo degradado (para tono, saturación y luminosidad). [value] va de 0 a 1. */
@Composable
fun GradientSlider(
    label: String, valueText: String, value: Float, brush: Brush, thumb: Color,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        GradientTrack(value, brush, thumb, onChange = onChange)
    }
}

/** Barra del slider (sin etiqueta): degradado, perilla redonda, toque o arrastre. [value] va de 0 a 1. */
@Composable
fun GradientTrack(value: Float, brush: Brush, thumb: Color, keyStep: Float = 0.02f, onChange: (Float) -> Unit) {
    val onChangeNow by rememberUpdatedState(onChange)
    val border = MaterialTheme.colorScheme.onSurface
    val editable = LocalEditable.current
    val held by rememberUpdatedState(LocalSliderHeld.current)
    val valueNow by rememberUpdatedState(value)
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    run {
        Canvas(
            Modifier.fillMaxWidth().height(32.dp)
                // Con el control: el slider recibe el foco y se ajusta con izquierda/derecha.
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { ev ->
                    if (!editable || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft -> { onChangeNow((valueNow - keyStep).coerceIn(0f, 1f)); true }
                        Key.DirectionRight -> { onChangeNow((valueNow + keyStep).coerceIn(0f, 1f)); true }
                        else -> false
                    }
                }
                .focusable(editable)
                .pointerInput(editable) {
                    if (!editable) return@pointerInput
                    val r = 16.dp.toPx()
                    detectTapGestures { onChangeNow(((it.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)) }
                }
                .pointerInput(editable) {
                    if (!editable) return@pointerInput
                    val r = 16.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { held(true) }, onDragEnd = { held(false) }, onDragCancel = { held(false) }
                    ) { change, _ ->
                        change.consume()
                        onChangeNow(((change.position.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f))
                    }
                }
        ) {
            val r = 16.dp.toPx()
            val h = 12.dp.toPx()
            drawRoundRect(
                brush, topLeft = Offset(r, center.y - h / 2), size = Size(size.width - 2 * r, h),
                cornerRadius = CornerRadius(h / 2)
            )
            val x = r + value.coerceIn(0f, 1f) * (size.width - 2 * r)
            drawCircle(Color.Black.copy(alpha = 0.25f), r - 1.dp.toPx(), Offset(x, center.y + 1.dp.toPx()))
            if (focused) drawCircle(ring.copy(alpha = 0.35f), r + 4.dp.toPx(), Offset(x, center.y))
            drawCircle(thumb, r - 3.dp.toPx(), Offset(x, center.y))
            drawCircle(border, r - 3.dp.toPx(), Offset(x, center.y), style = Stroke(2.5.dp.toPx()))
        }
    }
}

/**
 * Una opción como pastilla: la elegida llena con el color de acento, las demás con un fondo neutro suave, sin ✓ ni
 * borde (las de Material por defecto ponían morado y ✓ en todo). El foco del control, con contorno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val editable = LocalEditable.current
    Surface(
        onClick = onClick, enabled = editable, shape = CircleShape,
        color = androidx.compose.animation.animateColorAsState(if (selected) c.primary else c.onSurface.copy(alpha = 0.07f), tween(160), label = "pill").value,
        border = if (focused) BorderStroke(2.dp, if (selected) c.onSurface else c.primary) else null,
        interactionSource = source
    ) {
        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 9.dp), fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) c.onPrimary else c.onSurface, maxLines = 1)
    }
}

/** Pastillas que se marcan y desmarcan cada una (varias a la vez). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChips(options: List<Pair<T, String>>, selected: Set<T>, onToggle: (T, Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> val on = v in selected; OptionPill(label, on) { onToggle(v, !on) } }
    }
}

/** Fila de opciones (una elegida). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> OptionPill(label, selected == v) { onSelect(v) } }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(enabled = LocalEditable.current) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Un poco más chico que el de Material (se veía tosco al lado del texto).
        // Sin el alto mínimo de 48 dp de Material (la fila entera se toca): las filas quedaban muy separadas.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(checked = checked, onCheckedChange = onChange, enabled = LocalEditable.current, modifier = Modifier.drawScale(0.85f))
        }
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 0.dp) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp).then(if (size > 0.dp) Modifier.width(size) else Modifier),
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color
        )
    }
}

/**
 * Franja fija bajo las pestañas cuando el control no está en Android. Se comporta como la guía: barra
 * que se llena al mantener Vista + Menú, "suelta y vuelve a mantener" tras cada cambio, "¡Bien! Una vez
 * más" y "Listo" en verde al llegar. Lleva a Android o al modo elegido en Inicio o en ajustes rápidos.
 */
@Composable
fun ModeStrip(s: AppState) {
    val mode = s.padMode
    val target = if (s.modeTarget != mode) s.modeTarget else PadMode.ANDROID
    // "Listo" en verde un momento al llegar al modo pedido.
    var done by remember { mutableStateOf<PadMode?>(null) }
    var last by remember { mutableStateOf(mode) }
    // Cambios que faltaban al empezar (para "¡Bien! Una vez más").
    var startSteps by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) { startSteps = mode?.takeIf { it != target }?.let { ModeGuide.steps(it, target) } ?: 0 }
    LaunchedEffect(mode) {
        val was = last
        last = mode
        if (mode == null || mode == target) startSteps = 0
        else if (startSteps == 0) startSteps = ModeGuide.steps(mode, target)
        if (mode != null && was != null && mode != was && mode == s.modeTarget) {
            done = mode
            s.modeTarget = PadMode.ANDROID
        }
    }
    LaunchedEffect(done) { if (done != null) { kotlinx.coroutines.delay(1600); done = null } }

    AnimatedVisibility(
        visible = done != null || (!s.connected && mode != null && mode != PadMode.ANDROID),
        enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()
    ) {
        val progress = remember { androidx.compose.animation.core.Animatable(0f) }
        val since = s.hold.holdSince
        LaunchedEffect(since) {
            if (since != null) {
                val start = ((android.os.SystemClock.uptimeMillis() - since).toFloat() / HOLD_MS).coerceIn(0f, 1f)
                progress.snapTo(start)
                progress.animateTo(
                    1f, androidx.compose.animation.core.tween(((1f - start) * HOLD_MS).toInt(), easing = androidx.compose.animation.core.LinearEasing)
                )
            } else progress.animateTo(0f, androidx.compose.animation.core.tween(250))
        }
        val c = MaterialTheme.colorScheme
        val ok = Color(0xFF2EB872)
        val d = done
        val fill = c.primary.copy(alpha = 0.22f)
        Row(
            Modifier.fillMaxWidth().background(if (d != null) ok.copy(alpha = 0.22f) else c.primaryContainer.copy(alpha = 0.55f))
                .drawBehind { if (d == null) drawRect(fill, size = size.copy(width = size.width * progress.value)) }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (d != null) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(ok), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Listo: el control está en ${d.label}", "Done: the controller is in ${d.label}"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                return@Row
            }
            val m = mode ?: return@Row
            val left = ModeGuide.steps(m, target)
            RoundKey(menu = false, small = true)
            Spacer(Modifier.width(4.dp))
            RoundKey(menu = true, small = true)
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        s.hold.release -> tr("Suelta Vista + Menú y vuelve a mantenerlos", "Release View + Menu and hold them again")
                        left == 1 && startSteps > 1 -> tr("¡Bien! Una vez más", "Nice! One more time")
                        else -> tr("Mantén Vista + Menú unos 2 segundos", "Hold View + Menu for about 2 seconds") +
                            (if (left > 1) tr(" ($left veces)", " ($left times)") else "")
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer
                )
                Text(
                    if (target == PadMode.ANDROID) tr("El control está en ${m.label}. Para configurarlo tiene que estar en Android.",
                        "The controller is in ${m.label}. It must be in Android mode to configure it.")
                    else tr("Para pasar de ${m.label} a ${target.label}.", "To switch from ${m.label} to ${target.label}."),
                    fontSize = 13.sp, color = c.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            if (target != PadMode.ANDROID) {
                Spacer(Modifier.width(14.dp))
                TextButton(onClick = { s.modeTarget = PadMode.ANDROID }) { Text(tr("Cancelar", "Cancel")) }
            }
        }
    }
}

/**
 * Color de acento: atajos en círculos y un círculo "personalizado" que abre el selector (tono, saturación, brillo y
 * código hex, con la muestra en vivo). Lo elegido lleva un anillo.
 */
@Composable
fun AccentPicker(current: Accent, onPick: (Accent) -> Unit) {
    val dark = isDarkTheme()
    var custom by remember { mutableStateOf(false) }
    val isPreset = current in Accent.PRESETS
    // FlowRow: en una fila, sin ancho para el código, el texto se partía letra por letra y dejaba un hueco alto.
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Con el control: el que tiene el foco crece y lleva un anillo del color de la app (antes no se notaba dónde estaba).
        @Composable fun dot(sel: Boolean, fill: Modifier, desc: String, onClick: () -> Unit) {
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val c = MaterialTheme.colorScheme
            Box(
                Modifier.size(36.dp).drawScale(if (focused) 1.18f else 1f)
                    .clip(CircleShape)
                    .clickable(interactionSource = src, indication = null, onClick = onClick).semantics { contentDescription = desc }
                    .border(if (focused || sel) 3.dp else 1.dp, if (focused) c.primary else if (sel) c.onSurface else c.outline, CircleShape)
                    .padding(if (focused || sel) 5.dp else 3.dp).clip(CircleShape).then(fill)
            )
        }
        Accent.PRESETS.forEach { acc -> dot(acc == current, Modifier.background(acc.color(!dark)), acc.hex) { onPick(acc) } }
        // Personalizado: arcoíris (o el color elegido si no es un atajo).
        dot(!isPreset, if (isPreset) Modifier.background(androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))) else Modifier.background(current.color(!dark)),
            tr("Personalizado", "Custom")) { custom = true }
        Text(current.base?.let { current.hex } ?: "#6EC9FF", Modifier.height(36.dp).wrapContentHeight(),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
    if (custom) CustomColorDialog(current, onDismiss = { custom = false }) { onPick(it); custom = false }
}

@Composable
private fun CustomColorDialog(start: Accent, onDismiss: () -> Unit, onDone: (Accent) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV((start.base ?: 0xFF6EC9FF).toInt(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFF
    var hexText by remember { mutableStateOf("#%06X".format(argb and 0xFFFFFF)) }
    // Al mover las barras el código se actualiza; al escribir un código válido, las barras.
    fun fromHex(t: String) { hexText = t; Accent.parse(t)?.base?.let { b -> FloatArray(3).also { android.graphics.Color.colorToHSV(b.toInt(), it); h = it[0]; s = it[1]; v = it[2] } } }
    fun sync() { hexText = "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xFFFFFF) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Color personalizado", "Custom color")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(argb)))
                Text(tr("Tono", "Hue"), fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                    (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })))
                Slider(h, { h = it; sync() }, valueRange = 0f..360f)
                Text(tr("Saturación", "Saturation"), fontSize = 13.sp)
                Slider(s, { s = it; sync() })
                Text(tr("Brillo", "Brightness"), fontSize = 13.sp)
                Slider(v, { v = it; sync() })
                OutlinedTextField(hexText, { fromHex(it) }, label = { Text(tr("Código hex", "Hex code")) }, singleLine = true,
                    isError = Accent.parse(hexText) == null)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(Accent(argb or 0xFF000000)) }) { Text(tr("Usar", "Use")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } }
    )
}

/** Lo que muestra el centro: la tarjeta de [section] (page null) o una de sus líneas abierta. */
private data class Screen(val dest: String, val section: Section, val page: Page?, val sub: String?, val only: Set<String>?)
