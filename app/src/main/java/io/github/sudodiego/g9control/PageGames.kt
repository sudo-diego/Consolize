package io.github.sudodiego.g9control

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Emuladores y ports conocidos: cuentan como juego aunque Android no los marque así. */
val GAME_APPS = listOf(
    "com.retroarch", "com.retroarch.aarch64", "dev.eden.eden_emulator", "org.azahar_emu.azahar", "org.dolphinemu.dolphinemu",
    "xyz.aethersx2.android", "info.cemu.cemu", "me.magnum.melondualds", "me.magnum.melonds", "org.vita3k.emulator",
    "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold", "com.github.stenzek.duckstation", "app.gamenative", "com.winlator",
    "com.dishii.soh", "com.dishii.mm", "dev.twilitrealm.dusk", "org.citra.emu", "io.github.lime3ds.android",
)

private class GameEntry(val pkg: String, val label: String, val icon: ImageBitmap, val game: Boolean)

/** Lista de la última vez: se muestra al instante mientras se vuelve a leer. */
private object GamesCache { @Volatile var apps: List<GameEntry>? = null }

/**
 * Juegos: tu biblioteca y, al elegir uno, todo lo suyo junto (Hz, proporción, qué hace tocar la guía, modo del G9).
 * Sirve en cualquier Android: un juego es lo que Android marca como juego (categoría de la app), un emulador conocido,
 * algo jugado (sesiones) o una app con ajustes propios; "Todas las apps" deja configurar cualquier otra. Reemplaza las
 * listas que estaban repartidas (Proporción por juego en Pantalla, Tocar la guía en Atajos, la grilla del G9).
 */
@Composable
fun GamesPage(s: AppState, a: MainActivity) {
    var all by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(GamesCache.apps) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { a.requestAppListPermission() }
    LaunchedEffect(s.appsReload) { apps = withContext(Dispatchers.IO) { loadGames(a) }.also { GamesCache.apps = it } }
    val list = apps?.filter { all || it.game }.orEmpty()
    if (selected == null || list.none { it.pkg == selected }) selected = list.firstOrNull()?.pkg
    TwoPaneWeighted(1.25f, first = {
        Panel {
            ChoiceChips(listOf(false to tr("Juegos", "Games"), true to tr("Todas las apps", "All apps")), all) { all = it }
            if (apps == null) Text(tr("Cargando…", "Loading…"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (list.isEmpty()) Text(tr("No hay juegos instalados.", "No games installed."), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { g -> GameTile(a, s, g, g.pkg == selected, Modifier.weight(1f)) { selected = g.pkg } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }, second = {
        list.firstOrNull { it.pkg == selected }?.let { GameDetail(s, a, it) }
    })
}

/**
 * Dos columnas con otro reparto que [TwoPane] (la biblioteca más ancha que el detalle). El detalle se queda a la vista
 * al bajar por la biblioteca (como la vista previa del panel, con [LocalPageScroll]).
 */
@Composable
private fun TwoPaneWeighted(firstWeight: Float, first: @Composable ColumnScope.() -> Unit, second: @Composable ColumnScope.() -> Unit) {
    if (LocalWide.current) {
        var rowH by remember { mutableIntStateOf(0) }
        var sideH by remember { mutableIntStateOf(0) }
        val scroll = LocalPageScroll.current
        Row(Modifier.onGloballyPositioned { rowH = it.size.height }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(firstWeight), verticalArrangement = Arrangement.spacedBy(16.dp), content = first)
            Column(Modifier.weight(1f).onGloballyPositioned { sideH = it.size.height }
                .graphicsLayer { translationY = (scroll?.value ?: 0).coerceAtMost((rowH - sideH).coerceAtLeast(0)).toFloat() },
                verticalArrangement = Arrangement.spacedBy(16.dp), content = second)
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { second(); first() }
}

/** Las apps del cajón; las que cuentan como juego marcadas. */
private fun loadGames(ctx: Context): List<GameEntry> {
    val pm = ctx.packageManager
    val sp = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)
    // Lo que ya tiene algo propio también es un juego para esta lista (Hz, guía, proporción, modo del G9, sesiones).
    val known = buildSet {
        addAll(GAME_APPS)
        sp.all.keys.forEach { k ->
            when {
                k.startsWith("hz_") && k != "hz_games" -> add(k.removePrefix("hz_"))
                k.startsWith("guide_tap_app_") -> add(k.removePrefix("guide_tap_app_"))
            }
        }
        addAll(AutoRules.rules(ctx).keys)
        addAll(GameAspect.apps(ctx))
        runCatching { Sessions.list(ctx).forEach { add(it.pkg) } }
    }
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0).map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
        .filter { it.packageName != ctx.packageName }
        .map { info ->
            @Suppress("DEPRECATION")
            val game = info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0 || info.packageName in known
            GameEntry(info.packageName, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap(), game)
        }
        .sortedBy { it.label.lowercase() }
}

/** Un juego de la biblioteca: ícono y nombre; lo que tiene propio, en una línea chica (Hz, proporción, modo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTile(a: MainActivity, s: AppState, g: GameEntry, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val tags = listOfNotNull(
        RefreshRate.forApp(a, g.pkg)?.let { "$it Hz" },
        GameAspect.get(a, g.pkg).takeIf { it != GameAspect.Ratio.FULL }?.label,
        s.autoRules[g.pkg]?.let { if (it == AutoTarget.XINPUT) "X-Input" else "DS5" }.takeIf { s.g9Known },
    ).joinToString(" · ")
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = if (sel) c.primaryContainer else c.onSurface.copy(alpha = 0.05f),
        border = if (sel) BorderStroke(2.dp, c.primary) else null
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(g.icon, null, Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text(g.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            Text(tags.ifEmpty { " " }, fontSize = 11.sp, maxLines = 1, color = c.primary)
        }
    }
}

/** Todo lo de un juego en una tarjeta. Lo que depende de algo que no hay (Game Assistant, un G9) no sale. */
@Composable
private fun GameDetail(s: AppState, a: MainActivity, g: GameEntry) {
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(g.icon, null, Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Text(g.label, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Button(onClick = { a.packageManager.getLaunchIntentForPackage(g.pkg)?.let { runCatching { a.startActivity(it) } } }) {
                Text(tr("Jugar", "Play"))
            }
        }
        HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f))

        // Hz: los de este juego o, sin elegir, los de todos los juegos (General).
        key(g.pkg) {
            var hz by remember { mutableIntStateOf(RefreshRate.forApp(a, g.pkg) ?: 0) }
            val def = RefreshRate.forGames(a)?.let { "$it Hz" } ?: tr("Sistema", "System")
            Text(tr("Frecuencia", "Refresh rate"), fontSize = 14.sp)
            ChoiceChips(listOf(0 to tr("Como todos ($def)", "Like all ($def)")) + RefreshRate.supported(a).map { it to "$it Hz" }, hz) {
                hz = it; RefreshRate.setForApp(a, g.pkg, it.takeIf { v -> v > 0 })
            }

            // Proporción con la vista de juego de ZUI: solo donde está (Game Assistant).
            if (ZuiGame.installed(a)) {
                var ratio by remember { mutableStateOf(GameAspect.get(a, g.pkg)) }
                Text(tr("Proporción", "Aspect ratio"), fontSize = 14.sp)
                ChoiceChips(GameAspect.Ratio.entries.map { it to it.label }, ratio) { r ->
                    scope.launch { if (GameAspect.set(a, g.pkg, r)) ratio = r else s.fail(tr("Necesita Shizuku.", "Needs Shizuku.")) }
                }
            }

            var tap by remember { mutableStateOf(AutoRules.guideTap(a, g.pkg)) }
            Text(tr("Tocar la guía", "Tap the guide"), fontSize = 14.sp)
            ChoiceChips(AutoRules.GuideTap.entries.map { it to it.label }, tap) { tap = it; AutoRules.setGuideTap(a, g.pkg, it) }
            if (tap == AutoRules.GuideTap.PASS) {
                var byGame by remember { mutableStateOf(AutoRules.shotByGame(a, g.pkg)) }
                SwitchRow(tr("Guía + RB: captura del juego", "Guide + RB: game's screenshot"), byGame) { byGame = it; AutoRules.setShotByGame(a, g.pkg, it) }
            }

            // Modo del G9 al abrirlo: solo si hay (o hubo) un G9.
            if (s.g9Known) {
                Text(tr("Modo del G9", "G9 mode"), fontSize = 14.sp)
                ChoiceChips(listOf<Pair<AutoTarget?, String>>(null to "Android", AutoTarget.XINPUT to "X-Input", AutoTarget.DS5 to "DS5"), s.autoRules[g.pkg]) { t ->
                    val next = if (t == null) s.autoRules - g.pkg else s.autoRules + (g.pkg to t)
                    s.autoRules = next; AutoRules.setRules(a, next)
                }
            }
        }
    }
}
