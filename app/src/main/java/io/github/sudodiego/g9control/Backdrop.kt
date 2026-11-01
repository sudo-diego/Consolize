package io.github.sudodiego.g9control

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInWindow
import android.graphics.ImageDecoder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fondo de la app, detrás de todas las páginas. Degradado: azul marino a índigo con dos brillos (celeste y lila) que
 * se mueven muy lento, como los fondos de la Y700. Imagen o GIF: el que se eligió, animado y desenfocado, con un velo
 * para que el texto se lea. Liso: el color del tema.
 */
@Composable
fun Backdrop() {
    val dark = isDarkTheme()
    val base = MaterialTheme.colorScheme.background
    when (ThemeState.background) {
        AppBackground.PLAIN -> Box(Modifier.fillMaxSize().background(base))
        // Consola: liso con rayitas horizontales finas (los dos colores se eligen en Apariencia).
        AppBackground.STRIPES -> PatternBackdrop(ThemeState.pattern, Color(ThemeState.stripeBase), Color(ThemeState.stripeLine))
        AppBackground.GRADIENT -> GradientBackdrop(dark)
        AppBackground.IMAGE -> {
            val uri = ThemeState.backgroundUri
            if (uri == null) GradientBackdrop(dark) else ImageBackdrop(uri, ThemeState.backgroundBlur, ThemeState.backgroundLoop, dark)
        }
        // El fondo de pantalla lo dibuja Android detrás de la ventana; el velo lo pinta el fondo de la ventana
        // (MainActivity), que cubre también la barra de estado. Aquí nada.
        AppBackground.WALLPAPER -> Unit
    }
}

/**
 * Fondo del panel de juego (siempre oscuro, va sobre el juego): el suyo propio, no el de la app. El degradado y la
 * imagen pueden ser translúcidos: el juego se ve a través de ellos (liquid glass).
 */
@Composable
/** [opaque]: sin la transparencia elegida (la cabecera del lateral va sobre fondo liso). */
fun PanelBackdrop(opaque: Boolean = false) {
    val see = if (!opaque && (ThemeState.panelBackground == PanelBackground.GRADIENT || ThemeState.panelBackground == PanelBackground.IMAGE))
        ThemeState.panelSee / 100f * 0.85f else 0f
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - see }) { PanelBackdropLayer() }
}

@Composable
private fun PanelBackdropLayer() {
    when (ThemeState.panelBackground) {
        PanelBackground.PLAIN -> Box(Modifier.fillMaxSize().background(Color(0xFF0E1424)))
        // Transparente: el juego se ve detrás, con el relleno elegido (un velo oscuro, de nada a casi opaco).
        PanelBackground.CLEAR -> Box(Modifier.fillMaxSize().background((if (ThemeState.panelLight) Color(0xFFF2F5FA) else Color(0xFF0A0F1F))
            .copy(alpha = ThemeState.panelFill / 100f * 0.9f)))
        PanelBackground.GRADIENT -> GradientBackdrop(true, ThemeState.panelAccent)
        PanelBackground.GAME -> FrozenGame(ThemeState.panelBlur)
        PanelBackground.IMAGE -> {
            val uri = ThemeState.panelUri
            if (uri == null) GradientBackdrop(true, ThemeState.panelAccent) else ImageBackdrop(uri, ThemeState.panelBlur, ThemeState.panelLoop, true, "panel",
                ThemeState.panelFit, ThemeState.panelFocus)
        }
    }
}

@Composable
internal fun GradientBackdrop(dark: Boolean, accent: Accent = ThemeState.appAccent) {
    val t = rememberInfiniteTransition(label = "fondo")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "brillo")
    val top = if (dark) Color(0xFF0A0F1F) else Color(0xFFEAF0FA)
    val bottom = if (dark) Color(0xFF172552) else Color(0xFFD6DFF6)
    // El brillo que se mueve, del color de acento (celeste, el de siempre).
    val cyan = (if (accent.base == null) Color(0xFF5CC8FF) else accent.color(false)).copy(alpha = if (dark) 0.27f else 0.2f)
    val lilac = if (dark) Color(0x338F7CFF) else Color(0x267A6CE0)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(top, bottom), Offset.Zero, Offset(size.width * 0.4f, size.height)))
        val r = size.maxDimension * 0.55f
        drawCircle(Brush.radialGradient(listOf(cyan, Color.Transparent), Offset(size.width * (0.78f + 0.08f * drift), size.height * (0.12f + 0.1f * drift)), r),
            r, Offset(size.width * (0.78f + 0.08f * drift), size.height * (0.12f + 0.1f * drift)))
        drawCircle(Brush.radialGradient(listOf(lilac, Color.Transparent), Offset(size.width * (0.15f - 0.06f * drift), size.height * (0.9f - 0.1f * drift)), r),
            r, Offset(size.width * (0.15f - 0.06f * drift), size.height * (0.9f - 0.1f * drift)))
    }
}

/**
 * Imágenes de fondo ya decodificadas, por lugar ("app" o "panel": un GIF animado solo puede estar en una vista a la
 * vez) y archivo. Así el panel abre con su imagen al instante (antes se veía un momento el degradado).
 */
object ImageCache {
    private val map = mutableMapOf<String, Drawable>()
    @Synchronized fun get(place: String, uri: String) = map["$place|$uri"]
    @Synchronized private fun put(place: String, uri: String, d: Drawable) { map.keys.removeAll { it.startsWith("$place|") }; map["$place|$uri"] = d }
    fun decode(ctx: android.content.Context, place: String, uri: String): Drawable? = get(place, uri)
        ?: runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.contentResolver, Uri.parse(uri))) }.getOrNull()
            ?.also { put(place, uri, it) }
    fun preload(ctx: android.content.Context, place: String, uri: String) {
        if (get(place, uri) == null) Thread { decode(ctx.applicationContext, place, uri) }.start()
    }
}

@Composable
/**
 * [fit] y [focus] (solo el panel): cómo se acomoda la imagen. Rellenar recorta con el encuadre elegido (arriba, centro
 * o abajo, con una matriz); Ajustar la muestra entera (con bordes); Estirar la deforma al tamaño; Centrar, tamaño real.
 */
private fun ImageBackdrop(uri: String, blur: Int, loop: Boolean, dark: Boolean, place: String = "app",
                          fit: ImageFit = ImageFit.FILL, focus: Float = 0.5f) {
    val ctx = LocalContext.current
    var drawable by remember(uri) { mutableStateOf(ImageCache.get(place, uri)) }
    LaunchedEffect(uri) {
        if (drawable == null) drawable = withContext(Dispatchers.IO) { ImageCache.decode(ctx, place, uri) }
    }
    Box(Modifier.fillMaxSize()) {
        val d = drawable
        // Dibujada por Compose (antes un ImageView): así el vidrio líquido puede capturarla y desenfocarla detrás de las
        // superficies.
        if (d == null) GradientBackdrop(dark) else DrawableImage(d, blur, loop, fit, focus, Modifier.fillMaxSize())
        // Velo: el texto se lee aunque la imagen sea clara u oscura.
        Box(Modifier.fillMaxSize().background(if (dark) Color(0x8C0A0F1F) else Color(0x80EAF0FA)))
    }
}

/**
 * La imagen o el GIF del panel dibujados por Compose (no con un ImageView): así le llegan los efectos de Compose,
 * como el fundido de la cabecera del lateral (una máscara; a una vista nativa no le llega). Mismo ajuste y encuadre
 * que el fondo ([ImageFit], foco) y mismo desenfoque. El GIF se redibuja cada cuadro mientras se ve.
 */
@Composable
/** [still]: un cuadro fijo aunque sea un GIF (el reflejo de la cabecera: dos GIF moviéndose mareaban). */
fun PanelImage(modifier: Modifier = Modifier, still: Boolean = false) {
    val ctx = LocalContext.current
    val uri = ThemeState.panelUri ?: return Box(modifier) { GradientBackdrop(true, ThemeState.panelAccent) }
    var drawable by remember(uri) { mutableStateOf(ImageCache.get("banner", uri)) }
    LaunchedEffect(uri) { if (drawable == null) drawable = withContext(Dispatchers.IO) { ImageCache.decode(ctx, "banner", uri) } }
    val d = drawable ?: return Box(modifier) { GradientBackdrop(true, ThemeState.panelAccent) }
    DrawableImage(d, ThemeState.panelBlur, ThemeState.panelLoop, ThemeState.panelFit, ThemeState.panelFocus, modifier, still)
}

/**
 * Una imagen o GIF dibujados con Compose: ajuste ([ImageFit]) y encuadre ([focus], al rellenar), desenfoque y, si es un
 * GIF, se redibuja cada cuadro mientras se ve. Lo usan el fondo de la app, el del panel y la cabecera del lateral.
 */
@Composable
fun DrawableImage(d: Drawable, blur: Int, loop: Boolean, fit: ImageFit, focus: Float, modifier: Modifier = Modifier, still: Boolean = false) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(d, loop, still) {
        if (!still) (d as? AnimatedImageDrawable)?.let { gif ->
            gif.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
            gif.start()
            while (true) { withFrameNanos { }; frame++ }
        }
    }
    Canvas(modifier.then(if (Build.VERSION.SDK_INT >= 31 && blur > 0) Modifier.graphicsLayer {
        renderEffect = RenderEffect.createBlurEffect(blur.toFloat(), blur.toFloat(), Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else Modifier)) {
        if (!still) frame   // se lee para redibujar cada cuadro del GIF
        val w = size.width; val h = size.height
        val dw = d.intrinsicWidth.toFloat().coerceAtLeast(1f); val dh = d.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val (sx, sy) = when (fit) {
            ImageFit.FILL -> maxOf(w / dw, h / dh).let { it to it }
            ImageFit.FIT -> minOf(w / dw, h / dh).let { it to it }
            ImageFit.STRETCH -> (w / dw) to (h / dh)
            ImageFit.CENTER -> 1f to 1f
        }
        val left = (w - dw * sx) / 2f
        val top = if (fit == ImageFit.FILL) (h - dh * sy) * focus.coerceIn(0f, 1f) else (h - dh * sy) / 2f
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.clipRect(0f, 0f, w, h)
            nc.translate(left, top)
            nc.scale(sx, sy)
            d.setBounds(0, 0, dw.toInt(), dh.toInt())
            d.draw(nc)
            nc.restore()
        }
    }
}

/**
 * El fondo de la app grabado en una capa de GPU (lo dibuja [CapturedBackdrop]): el vidrio líquido la vuelve a dibujar,
 * desenfocada, detrás de cada superficie. Null fuera de la app (el panel de juego no la usa).
 */
val LocalBackdropLayer = compositionLocalOf<androidx.compose.ui.graphics.layer.GraphicsLayer?> { null }

/** El fondo de la app, grabado en [layer] cada vez que se dibuja (también cada cuadro de un GIF). */
@Composable
fun CapturedBackdrop(layer: androidx.compose.ui.graphics.layer.GraphicsLayer) {
    Box(Modifier.fillMaxSize().drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }) { Backdrop() }
}

/**
 * Superficie de la app (hoja, tarjeta, barra) según la apariencia elegida. Vidrio: translúcida con un borde que brilla
 * arriba. Vidrio líquido: además, detrás, el fondo desenfocado por GPU (la capa de [LocalBackdropLayer] desplazada a donde
 * está la superficie) y un brillo suave arriba, como un cristal grueso. Sólida: opaca.
 */
@Composable
fun GlassSurface(shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier, tint: Color? = null, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val layer = LocalBackdropLayer.current
    val liquid = ThemeState.liquid && layer != null
    val glass = ThemeState.glass
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val base = tint ?: c.surfaceContainer
    // Vidrio líquido, con lo elegido en Ajustes: tinte (cuánto color tapa lo de atrás), desenfoque, refracción en el
    // borde, brillo arriba y fuerza del borde.
    val lq = ThemeState.liquidParams
    val fill = when {
        liquid -> base.copy(alpha = lq.tint / 100f * (if (dark) 0.85f else 0.95f))
        glass -> base.copy(alpha = if (dark) 0.58f else 0.66f)
        else -> base
    }
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot(); size = it.size.toSize() }.bump(shape).clip(shape)) {
        if (liquid) Box(Modifier.matchParentSize().graphicsLayer {
            renderEffect = liquidEffect(lq, size, radiusPx)?.asComposeRenderEffect()
        }.drawBehind { translate(-origin.x, -origin.y) { drawLayer(layer!!) } })
        Box(Modifier.matchParentSize().background(fill))
        if (liquid && lq.highlight > 0) Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.White.copy(alpha = lq.highlight / 100f * (if (dark) 0.22f else 0.4f)), 0.4f to Color.Transparent)))
        if (glass) {
            val rim = if (liquid) lq.rim / 100f else 0.5f
            Box(Modifier.matchParentSize().border((1f + rim * 1.5f).dp, Brush.verticalGradient(listOf(
                Color.White.copy(alpha = (if (dark) 0.36f else 0.9f) * rim + 0.04f),
                Color.White.copy(alpha = (if (dark) 0.08f else 0.3f) * rim))), shape))
        }
        // Lo de adentro con el color del texto de las tarjetas (con tarjetas claras, oscuro aunque la app sea oscura).
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c.onSurface) { Box { content() } }
    }
}

/** Ajustes del vidrio líquido (0..100 cada uno; el desenfoque en px de radio). */
data class LiquidParams(val blur: Int = 38, val refraction: Int = 40, val tint: Int = 50, val highlight: Int = 50, val rim: Int = 55)

/**
 * Desenfoque y, en Android 13+, refracción: cerca del borde lo de atrás se dobla hacia afuera, como el canto de un
 * cristal grueso (un shader AGSL sobre la capa ya desenfocada).
 */
private fun liquidEffect(p: LiquidParams, size: androidx.compose.ui.geometry.Size, radius: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < 31) return null
    val blur = if (p.blur > 0) RenderEffect.createBlurEffect(p.blur.toFloat(), p.blur.toFloat(), Shader.TileMode.CLAMP) else null
    if (Build.VERSION.SDK_INT < 33 || p.refraction <= 0 || size.width <= 0f) return blur
    val sh = android.graphics.RuntimeShader(REFRACTION_AGSL)
    sh.setFloatUniform("size", size.width, size.height)
    sh.setFloatUniform("radius", radius)
    // Fuerte: sobre lo ya desenfocado un desplazamiento chico no se notaba.
    sh.setFloatUniform("strength", p.refraction / 100f * 180f)
    val refr = RenderEffect.createRuntimeShaderEffect(sh, "content")
    return if (blur != null) RenderEffect.createChainEffect(refr, blur) else refr
}

private const val REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float strength;
half4 main(float2 p) {
    float2 c = size * 0.5;
    float2 q = abs(p - c) - (c - radius);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float band = radius * 3.0 + 1.0;
    float edge = clamp(1.0 + d / band, 0.0, 1.0);
    float2 dir = normalize(p - c + float2(0.001, 0.001));
    float2 off = -dir * edge * edge * strength;
    return content.eval(clamp(p + off, float2(0.0), size));
}
"""

/**
 * Vidrio líquido en el panel de juego: el fondo propio del panel (degradado o imagen) se graba en [layer] y cada mosaico
 * lo dibuja detrás, desenfocado y con refracción. Lo de detrás de la ventana (el juego) no se puede: la tablet no tiene
 * desenfoque entre ventanas.
 */
class PanelGlass(val layer: androidx.compose.ui.graphics.layer.GraphicsLayer) { var origin by mutableStateOf(Offset.Zero) }
val LocalPanelGlass = compositionLocalOf<PanelGlass?> { null }

/** Graba lo que dibuja este nodo (el fondo del panel) en la capa de [g] y lo muestra igual. */
fun Modifier.recordPanelGlass(g: PanelGlass?): Modifier = if (g == null) this else this
    .onGloballyPositioned { g.origin = it.positionInRoot() }
    .drawWithContent { g.layer.record { this@drawWithContent.drawContent() }; drawLayer(g.layer) }

/** Detrás de un mosaico del panel: el fondo del panel desenfocado y refractado (con [LocalPanelGlass]). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.LiquidBehind(radius: androidx.compose.ui.unit.Dp) {
    val g = LocalPanelGlass.current ?: return
    var o by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val r = with(androidx.compose.ui.platform.LocalDensity.current) { radius.toPx() }
    val p = ThemeState.liquidParams
    Box(Modifier.matchParentSize().onGloballyPositioned { o = it.positionInRoot(); size = it.size.toSize() }
        .graphicsLayer { renderEffect = liquidEffect(p, size, r)?.asComposeRenderEffect() }
        .drawBehind { translate(g.origin.x - o.x, g.origin.y - o.y) { drawLayer(g.layer) } })
    if (p.highlight > 0) Box(Modifier.matchParentSize().background(Brush.verticalGradient(
        0f to Color.White.copy(alpha = p.highlight / 100f * 0.2f), 0.45f to Color.Transparent)))
}

/**
 * Fondo de un elemento del panel (mini monitor, barras, botones): con vidrio líquido, el fondo del panel desenfocado y
 * refractado detrás y el color encima más suave; sin él, el color de siempre. Va después del clip (toma su forma).
 */
@Composable
fun Modifier.glassBg(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier {
    val g = LocalPanelGlass.current ?: return this.background(color)
    val inner = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var o by remember { mutableStateOf(Offset.Zero) }
    val r = with(androidx.compose.ui.platform.LocalDensity.current) { radius.toPx() }
    val p = ThemeState.liquidParams
    return this.onGloballyPositioned { o = it.positionInRoot() }.drawBehind {
        inner.renderEffect = liquidEffect(p, size, r)?.asComposeRenderEffect()
        inner.record { translate(g.origin.x - o.x, g.origin.y - o.y) { drawLayer(g.layer) } }
        drawLayer(inner)
        drawRect(color.copy(alpha = color.alpha * p.tint / 100f))
        if (p.highlight > 0) drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = p.highlight / 100f * 0.2f), 0.5f to Color.Transparent))
    }
}

/** La vista previa del panel: su juego de muestra, para el fondo "Juego en pausa" (no hay imagen real). */
val LocalPreviewScene = compositionLocalOf<(@Composable () -> Unit)?> { null }

/** La imagen del juego tomada al abrir el panel con el fondo "Juego en pausa" (null si no hay). */
object PanelFreeze { var frame by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

/**
 * Detrás del panel: la parte del juego congelado que queda debajo de él (la imagen es de toda la pantalla; se corre
 * según dónde está el panel en la pantalla), desenfocada. Sin imagen, oscuro.
 */
@Composable
fun FrozenGame(blur: Int) {
    // En la vista previa no hay imagen del juego: el juego de muestra, desenfocado.
    val f = PanelFreeze.frame ?: return LocalPreviewScene.current?.let { scene ->
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (Build.VERSION.SDK_INT >= 31) maxOf(blur, 20).toFloat().let { r -> renderEffect = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP).asComposeRenderEffect() }
        }) { scene() }
    } ?: Box(Modifier.fillMaxSize().background(Color(0xFF0A0F1F)))
    val view = androidx.compose.ui.platform.LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned {
        val loc = IntArray(2); view.getLocationOnScreen(loc)
        origin = it.positionInWindow() + Offset(loc[0].toFloat(), loc[1].toFloat())
    }.graphicsLayer {
        val r = maxOf(blur, 20).toFloat()
        if (Build.VERSION.SDK_INT >= 31) renderEffect = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }) {
        translate(-origin.x, -origin.y) { drawImage(f) }
    }
}
/**
 * El tapiz: el color de fondo y un dibujo fino con el otro color (rayas, puntos, círculos, diagonales, cuadrícula u
 * ondas). Es fijo (no se anima): no gasta batería.
 */
@Composable
fun PatternBackdrop(p: Pattern, bg: Color, line: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(bg)
        val w = size.width; val h = size.height
        when (p) {
            Pattern.STRIPES -> { val step = 6.dp.toPx(); val t = 3.dp.toPx(); var y = step - t
                while (y < h) { drawRect(line, Offset(0f, y), androidx.compose.ui.geometry.Size(w, t)); y += step } }
            Pattern.DOTS -> { val step = 18.dp.toPx(); val r = 2.5.dp.toPx(); var y = step / 2; var row = 0
                while (y < h) { var x = if (row % 2 == 0) step / 2 else step; while (x < w) { drawCircle(line, r, Offset(x, y)); x += step }; y += step * 0.866f; row++ } }
            Pattern.CIRCLES -> { val rnd = java.util.Random(7); val st = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                repeat(26) { val r = (24 + rnd.nextInt(90)).dp.toPx(); drawCircle(line, r, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), style = st) } }
            Pattern.DIAGONAL -> { val step = 12.dp.toPx(); val t = 3.dp.toPx(); var x = -h
                while (x < w) { drawLine(line, Offset(x, h), Offset(x + h, 0f), t); x += step } }
            Pattern.GRID -> { val step = 28.dp.toPx(); val t = 1.5.dp.toPx(); var x = step
                while (x < w) { drawLine(line, Offset(x, 0f), Offset(x, h), t); x += step }
                var y = step; while (y < h) { drawLine(line, Offset(0f, y), Offset(w, y), t); y += step } }
            Pattern.WAVES -> { val step = 22.dp.toPx(); val amp = 5.dp.toPx(); val len = 60.dp.toPx(); val st = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                var y = step / 2
                while (y < h + amp) {
                    val path = androidx.compose.ui.graphics.Path(); var x = 0f; path.moveTo(0f, y)
                    while (x <= w) { x += 6f; path.lineTo(x, y + amp * kotlin.math.sin(x / len * 2 * Math.PI).toFloat()) }
                    drawPath(path, line, style = st); y += step
                } }
        }
    }
}
