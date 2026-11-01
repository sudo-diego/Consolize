package io.github.sudodiego.g9control

import androidx.compose.ui.graphics.luminance

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta inspirada en los fondos de anime de la Y700 (solo los colores): índigo profundo (el pelo), celeste que
// brilla (el anillo y los detalles), blanco hielo y plateado (nieve, cielo) y azul marino (el traje, el fondo
// oscuro); toques de lila. Frío, azul y con brillo celeste.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FB2FF),
    onPrimary = Color(0xFF0B1A4A),
    primaryContainer = Color(0xFF23397F),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF5CC8FF),
    onSecondary = Color(0xFF002235),
    secondaryContainer = Color(0xFF0E3A55),
    onSecondaryContainer = Color(0xFFC9ECFF),
    tertiary = Color(0xFFB3A8FF),
    background = Color(0xFF0D1220),
    onBackground = Color(0xFFE6EBF5),
    surface = Color(0xFF0D1220),
    onSurface = Color(0xFFE6EBF5),
    surfaceVariant = Color(0xFF26324C),
    onSurfaceVariant = Color(0xFF9FAAC2),
    surfaceContainerLowest = Color(0xFF090D18),
    surfaceContainerLow = Color(0xFF111827),
    surfaceContainer = Color(0xFF151E31),
    surfaceContainerHigh = Color(0xFF1A2439),
    surfaceContainerHighest = Color(0xFF202B43),
    outline = Color(0xFF3A4763),
    outlineVariant = Color(0xFF2A3550),
    error = Color(0xFFFF6B7A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3A55C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0F1E5C),
    secondary = Color(0xFF1E8FD6),
    secondaryContainer = Color(0xFFD5EEFF),
    onSecondaryContainer = Color(0xFF04324D),
    tertiary = Color(0xFF7A6CE0),
    background = Color(0xFFEDF2F8),
    onBackground = Color(0xFF131A2B),
    surface = Color(0xFFEDF2F8),
    onSurface = Color(0xFF131A2B),
    surfaceVariant = Color(0xFFDCE3EE),
    onSurfaceVariant = Color(0xFF55617A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F8FC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE6ECF5),
    surfaceContainerHighest = Color(0xFFDDE5F0),
    outline = Color(0xFFAEB9CB),
    outlineVariant = Color(0xFFD2DAE6),
)

/**
 * Color de acento, aparte para la app y para el panel (y sus avisos y guías): [base] es el color elegido (ARGB) o
 * null para el celeste de siempre. De él salen sus tonos: más claro y suave en oscuro, más profundo en claro, y un
 * fondo apagado ([box]) para lo que va detrás del texto.
 */
data class Accent(val base: Long?) {
    private fun hsv() = FloatArray(3).also { android.graphics.Color.colorToHSV(base!!.toInt(), it) }
    private fun hsvColor(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    fun color(light: Boolean): Color {
        if (base == null) return Color(if (light) 0xFF2F8FD8 else 0xFF6EC9FF)
        val (h, s, v) = hsv().let { Triple(it[0], it[1], it[2]) }
        return if (light) hsvColor(h, s.coerceAtLeast(0.15f * s), minOf(v, 0.72f)) else hsvColor(h, s * 0.6f, maxOf(v, 0.95f))
    }
    fun box(light: Boolean): Color {
        if (base == null) return Color(if (light) 0xFFD5EEFF else 0xFF0E3A55)
        val (h, s) = hsv().let { it[0] to it[1] }
        return if (light) hsvColor(h, s * 0.18f, 0.98f) else hsvColor(h, s * 0.65f, 0.32f)
    }
    /** "#RRGGBB" (el celeste también, para el campo de código). */
    val hex get() = "#%06X".format(((base ?: 0xFF6EC9FF) and 0xFFFFFF))
    companion object {
        val SKY = Accent(null)
        /** Atajos: celeste (el de siempre), violeta, verde, naranja, rosa, rojo y gris. */
        val PRESETS = listOf(SKY, Accent(0xFF9C7CFF), Accent(0xFF3DBE7A), Accent(0xFFFF8A3D), Accent(0xFFFF6FA8), Accent(0xFFFF5A5A), Accent(0xFF9AA3AE))
        /** "#RRGGBB" o "RRGGBB"; null si no es un color. */
        fun parse(text: String): Accent? = text.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Accent(0xFF000000 or it) }
    }
}
/** Color de las tarjetas (mosaicos, temas, datos, dock), aparte del acento. Con [CUSTOM], el elegido en la rueda. */
enum class CardTone { NIGHT, GRAPHITE, BLACK, ACCENT, LIGHT, CUSTOM;
    val label get() = when (this) {
        NIGHT -> tr("Azul noche", "Night blue"); GRAPHITE -> tr("Grafito", "Graphite"); BLACK -> tr("Negro", "Black")
        ACCENT -> tr("Del acento", "From accent"); LIGHT -> tr("Claro", "Light"); CUSTOM -> tr("Propio", "Custom")
    }
}
/** Texto y dibujos directo sobre el fondo (la batería grande, el control, los títulos): según el fondo, claros u oscuros. */
enum class BackdropText { AUTO, LIGHT, DARK;
    val label get() = when (this) { AUTO -> tr("Según el fondo", "Match background"); LIGHT -> tr("Claros", "Light"); DARK -> tr("Oscuros", "Dark") }
}
/** Halo del foco: el borde de lo elegido con un brillo del color de acento que sube y baja, o sin él. */
enum class Halo { OFF, SLOW, FAST;
    val label get() = when (this) { OFF -> tr("Sin halo", "No halo"); SLOW -> tr("Lento", "Slow"); FAST -> tr("Rápido", "Fast") }
}
enum class AppTheme { SYSTEM, LIGHT, DARK;
    val label get() = when (this) { SYSTEM -> tr("Del sistema", "System"); LIGHT -> tr("Claro", "Light"); DARK -> tr("Oscuro", "Dark") }
}

/** El dibujo del tapiz "Consola" (con los dos colores elegidos). */
enum class Pattern { STRIPES, DOTS, CIRCLES, DIAGONAL, GRID, WAVES;
    val label get() = when (this) {
        STRIPES -> tr("Rayas", "Stripes"); DOTS -> tr("Puntos", "Dots"); CIRCLES -> tr("Círculos", "Circles")
        DIAGONAL -> tr("Diagonales", "Diagonals"); GRID -> tr("Cuadrícula", "Grid"); WAVES -> tr("Ondas", "Waves")
    }
}
enum class AppBackground { STRIPES, GRADIENT, IMAGE, WALLPAPER, PLAIN;
    val label get() = when (this) {
        STRIPES -> tr("Tapiz", "Pattern")
        GRADIENT -> tr("Degradado", "Gradient"); IMAGE -> tr("Imagen o GIF", "Image or GIF")
        WALLPAPER -> tr("Fondo de pantalla", "Wallpaper"); PLAIN -> tr("Liso", "Plain")
    }
}

/**
 * Ancho del panel al centro, en columnas de mosaicos (3 a 6); el alto lo pone el contenido. Antes eran proporciones
 * fijas (16:9, 16:10, 4:3, 1:1) que estiraban o aplastaban los mosaicos para llenar el alto: se veían mal. Los nombres
 * se mantienen por las preferencias guardadas (R4_3 = 4 columnas, como AUTO). [ratio] queda siempre en null.
 */
enum class PanelShape(val cols: Int, val ratio: Float? = null) {
    AUTO(4), R16_9(6), R16_10(5), R4_3(4), R1_1(3);
    val label get() = "$cols"
    companion object {
        /** Las que se ofrecen, de angosta a ancha. */
        val choices = listOf(R1_1, AUTO, R16_10, R16_9)
    }
}


/** Fondo del panel de juego (aparte del de la app): el fondo de pantalla no se puede, el juego está detrás. */
/** GAME: el juego congelado (una imagen al abrir) a pantalla completa y desenfocado detrás del panel. */
enum class PanelBackground { CLEAR, GRADIENT, IMAGE, GAME, PLAIN;
    val label get() = when (this) {
        CLEAR -> tr("Transparente", "Clear"); GRADIENT -> tr("Degradado", "Gradient")
        IMAGE -> tr("Imagen o GIF", "Image or GIF"); GAME -> tr("Juego en pausa", "Paused game"); PLAIN -> tr("Liso", "Plain")
    }
}

/**
 * Estilo del panel de juego: al centro (una tarjeta, con forma y tamaño a elección) o lateral, pegado a la derecha y de
 * alto completo, como el acceso rápido del Steam Deck (con la imagen o el GIF como cabecera).
 */
enum class PanelStyle { CENTER, SIDE;
    val label get() = when (this) { CENTER -> tr("Al centro", "Centered"); SIDE -> tr("Lateral", "Side") }
}

/** Cómo se acomoda la imagen o el GIF del panel, como los fondos de pantalla de Windows. */
enum class ImageFit { FILL, FIT, STRETCH, CENTER;
    val label get() = when (this) {
        FILL -> tr("Rellenar", "Fill"); FIT -> tr("Ajustar", "Fit"); STRETCH -> tr("Estirar", "Stretch"); CENTER -> tr("Centrar", "Center")
    }
}

/**
 * Cómo se ven las superficies de la app (hojas, tarjetas, barra lateral) sobre el fondo. Vidrio: translúcidas. Vidrio
 * líquido: además desenfocan lo que tienen detrás (por GPU, con RenderEffect; con el fondo de pantalla de Android no se
 * puede y queda como vidrio). Sólidas: opacas.
 */
enum class Surfaces { GLASS, LIQUID, SOLID;
    val label get() = when (this) { GLASS -> tr("Vidrio", "Glass"); LIQUID -> tr("Vidrio líquido", "Liquid glass"); SOLID -> tr("Sólidas", "Solid") }
}

/** Tema del panel de juego: oscuro (el de siempre), claro o el del sistema. Vale para todos sus fondos. */
enum class PanelTone { DARK, LIGHT, SYSTEM;
    val label get() = when (this) { DARK -> tr("Oscuro", "Dark"); LIGHT -> tr("Claro", "Light"); SYSTEM -> tr("Sistema", "System") }
}

/** Tema y fondo elegidos en Ajustes. */
object ThemeState {
    var theme by androidx.compose.runtime.mutableStateOf(AppTheme.SYSTEM)
    var background by androidx.compose.runtime.mutableStateOf(AppBackground.STRIPES)
    /** Fondo "Consola": el color de fondo y el de las rayitas (se eligen en Apariencia). */
    var stripeBase by androidx.compose.runtime.mutableLongStateOf(0xFFEEF2F6)
    var stripeLine by androidx.compose.runtime.mutableLongStateOf(0xFFE3E9F0)
    val consoleLook get() = background == AppBackground.STRIPES
    var pattern by androidx.compose.runtime.mutableStateOf(Pattern.STRIPES)
    /** El tapiz encima del fondo de pantalla o de la imagen (null = ninguno). */
    var overlay by androidx.compose.runtime.mutableStateOf<Pattern?>(Pattern.STRIPES)
    var overlayLine by androidx.compose.runtime.mutableStateOf(0xFFFFFFFF)
    var backgroundUri by androidx.compose.runtime.mutableStateOf<String?>(null)
    var backgroundBlur by androidx.compose.runtime.mutableIntStateOf(24)
    var backgroundLoop by androidx.compose.runtime.mutableStateOf(true)
    var surfaces by androidx.compose.runtime.mutableStateOf(Surfaces.GLASS)
    var liquidParams by androidx.compose.runtime.mutableStateOf(LiquidParams())
    /** Con el fondo de pantalla: cuánto se oscurece (0..100) para que el texto se lea. No se puede desenfocar: la
     *  tablet no tiene desenfoque entre ventanas (ro.surface_flinger.supports_background_blur = 0). */
    var backgroundDim by androidx.compose.runtime.mutableIntStateOf(45)
    var panelBackground by androidx.compose.runtime.mutableStateOf(PanelBackground.GRADIENT)
    var panelUri by androidx.compose.runtime.mutableStateOf<String?>(null)
    var panelBlur by androidx.compose.runtime.mutableIntStateOf(24)
    var panelLoop by androidx.compose.runtime.mutableStateOf(true)
    /** Transparente: cuánto relleno oscuro lleva (0 = nada, se ve el juego entero detrás). */
    var panelFill by androidx.compose.runtime.mutableIntStateOf(25)
    /** Degradado o imagen: cuánto se ve el juego a través (0 = nada, el fondo tapa; 100 = casi invisible). */
    var panelSee by androidx.compose.runtime.mutableIntStateOf(0)
    var panelShape by androidx.compose.runtime.mutableStateOf(PanelShape.AUTO)
    /** Tamaño del panel en % (todo: mosaicos y letras); 100 = el diseño original, que ocupaba casi todo el alto. */
    var panelScale by androidx.compose.runtime.mutableIntStateOf(85)
    var panelTone by androidx.compose.runtime.mutableStateOf(PanelTone.DARK)
    var panelStyle by androidx.compose.runtime.mutableStateOf(PanelStyle.CENTER)
    var panelFit by androidx.compose.runtime.mutableStateOf(ImageFit.FILL)
    /** Colores de acento: el de la app y el del panel (avisos y guías incluidos). */
    /** Mosaicos del panel: nuevos (ícono en una insignia, más sobrios) o los clásicos (píldoras llenas de color). */
    var panelClassic by androidx.compose.runtime.mutableStateOf(false)
    /** Juego en pausa: cuánto se oscurece (0..90). */
    var panelPauseDim by androidx.compose.runtime.mutableIntStateOf(35)
    /** Congelar el juego al abrir el panel, con cualquier fondo (con "Juego en pausa" siempre). */
    var panelFreeze by androidx.compose.runtime.mutableStateOf(false)
    internal var panelHidden by androidx.compose.runtime.mutableStateOf(emptySet<PanelTile>())
    var cardTone by androidx.compose.runtime.mutableStateOf(CardTone.NIGHT)
    var cardCustom by androidx.compose.runtime.mutableLongStateOf(0xFF2A3B4F)
    var halo by androidx.compose.runtime.mutableStateOf(Halo.SLOW)
    var backdropText by androidx.compose.runtime.mutableStateOf(BackdropText.AUTO)
    /** Orden de las secciones del panel (barras, juego, recientes…) y de los mosaicos dentro de cada una. */
    internal var panelSections by androidx.compose.runtime.mutableStateOf(PANEL_SECTIONS)
    internal var panelTileOrder by androidx.compose.runtime.mutableStateOf(PanelTile.entries.toList())
    internal var panelStatItems by androidx.compose.runtime.mutableStateOf(PanelStat.DEFAULT)
    val freezesGame get() = panelFreeze || panelBackground == PanelBackground.GAME
    /** Mosaicos del panel de vidrio líquido (sobre el fondo propio del panel). */
    var panelLiquid by androidx.compose.runtime.mutableStateOf(false)
    /** Mini monitor arriba del panel (estado, para que la vista previa lo muestre al cambiarlo). */
    var panelStats by androidx.compose.runtime.mutableStateOf(true)
    /** Imagen propia para el fondo de la vista previa del panel (null = el degradado). Queda solo en la tablet. */
    var previewUri by androidx.compose.runtime.mutableStateOf<String?>(null)
    var appAccent by androidx.compose.runtime.mutableStateOf(Accent.SKY)
    var panelAccent by androidx.compose.runtime.mutableStateOf(Accent.SKY)
    /** Lateral: la imagen como cabecera (true) o de fondo de todo el panel. */
    var panelHeader by androidx.compose.runtime.mutableStateOf(true)
    /** Lateral con cabecera: alto de la cabecera (dp) y si la transparencia también aclara la imagen. */
    var panelHeaderH by androidx.compose.runtime.mutableIntStateOf(150)
    var panelSeeImage by androidx.compose.runtime.mutableStateOf(false)
    /** Lateral con cabecera: cuánto se funde el borde de abajo de la imagen con el panel (0 = borde recto, 100 = toda). */
    var panelFade by androidx.compose.runtime.mutableIntStateOf(55)
    /** Lateral con cabecera: la imagen arriba y el resto liso (con su transparencia). El degradado va siempre de fondo. */
    val sideBanner get() = panelSide && panelHeader && panelBackground == PanelBackground.IMAGE
    /** Rellenar: qué parte de la imagen queda a la vista (0 = arriba, 0,5 = centro, 1 = abajo). */
    var panelFocus by androidx.compose.runtime.mutableFloatStateOf(0.5f)
    /** El panel va pegado a la derecha (estilo Steam). */
    val panelSide get() = panelStyle == PanelStyle.SIDE
    /** El sistema está en modo oscuro (para el tema "Sistema"); lo actualiza el panel al abrirse. */
    var systemDark by androidx.compose.runtime.mutableStateOf(true)
    /** El panel va en claro. */
    val panelLight get() = panelTone == PanelTone.LIGHT || (panelTone == PanelTone.SYSTEM && !systemDark)
    /** El panel deja ver el juego detrás: transparente, o un fondo con transparencia. */
    val panelSeeThrough get() = panelBackground == PanelBackground.CLEAR ||
        (panelBackground != PanelBackground.PLAIN && panelSee > 0)

    /** Lo elegido, desde la app o desde el servicio (el panel se puede abrir sin haber abierto la app). */
    fun load(ctx: android.content.Context) {
        theme = Prefs.theme(ctx); background = Prefs.background(ctx); backgroundUri = Prefs.backgroundUri(ctx)
        backgroundBlur = Prefs.backgroundBlur(ctx); backgroundLoop = Prefs.backgroundLoop(ctx); surfaces = Prefs.surfaces(ctx); liquidParams = Prefs.liquidParams(ctx); backgroundDim = Prefs.backgroundDim(ctx)
        panelBackground = Prefs.panelBackground(ctx); panelUri = Prefs.panelUri(ctx); panelBlur = Prefs.panelBlur(ctx); panelLoop = Prefs.panelLoop(ctx); panelFill = Prefs.panelFill(ctx); panelSee = Prefs.panelSee(ctx)
        panelShape = Prefs.panelShape(ctx); panelScale = Prefs.panelScale(ctx); panelTone = Prefs.panelTone(ctx); panelStyle = Prefs.panelStyle(ctx); panelFit = Prefs.panelFit(ctx); appAccent = Prefs.accent(ctx, "app"); panelClassic = Prefs.panelClassic(ctx); panelPauseDim = Prefs.panelPauseDim(ctx); panelFreeze = Prefs.panelFreeze(ctx); panelHidden = Prefs.panelHidden(ctx); cardTone = Prefs.cardTone(ctx); cardCustom = Prefs.cardCustom(ctx); halo = Prefs.halo(ctx); stripeBase = Prefs.stripeBase(ctx); stripeLine = Prefs.stripeLine(ctx); pattern = Prefs.pattern(ctx); overlay = Prefs.overlay(ctx); overlayLine = Prefs.overlayLine(ctx); backdropText = Prefs.backdropText(ctx); panelSections = Prefs.panelSections(ctx); panelTileOrder = Prefs.panelTileOrder(ctx); panelStatItems = Prefs.panelStatItems(ctx); panelLiquid = Prefs.panelLiquid(ctx); panelStats = Prefs.panelStats(ctx); previewUri = Prefs.previewUri(ctx); panelAccent = Prefs.accent(ctx, "panel"); panelHeader = Prefs.panelHeader(ctx); panelHeaderH = Prefs.panelHeaderH(ctx); panelSeeImage = Prefs.panelSeeImage(ctx); panelFade = Prefs.panelFade(ctx); panelFocus = Prefs.panelFocus(ctx)
        // La imagen del panel ya decodificada: al abrirlo no aparece primero el fondo liso.
        panelUri?.takeIf { panelBackground == PanelBackground.IMAGE }?.let { ImageCache.preload(ctx, "panel", it) }
    }
    /** Con fondo (degradado o imagen) las tarjetas son de vidrio: translúcidas con un borde claro. */
    val glass get() = background != AppBackground.PLAIN && background != AppBackground.STRIPES && surfaces != Surfaces.SOLID
    /** Vidrio líquido de verdad: con un fondo que dibuja la app (degradado o imagen) y Android 12+. */
    val liquid get() = surfaces == Surfaces.LIQUID && android.os.Build.VERSION.SDK_INT >= 31 &&
        (background == AppBackground.GRADIENT || background == AppBackground.IMAGE)
}

@Composable
fun isDarkTheme() = if (ThemeState.consoleLook) Color(ThemeState.stripeBase).luminance() < 0.4f else when (ThemeState.theme) {
    AppTheme.SYSTEM -> isSystemInDarkTheme()
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

/**
 * El color de las tarjetas (Apariencia → Tarjetas) en todo el tema: las superficies (tarjetas, hojas, diálogos, menús,
 * el dock) salen de ese color, y el texto y los íconos encima se ponen claros u oscuros según qué tan claro sea. El fondo
 * de la app no cambia. Con "Azul noche" queda el tema de siempre.
 */
private fun withCards(s: ColorScheme): ColorScheme {
    val base = when (ThemeState.cardTone) {
        CardTone.NIGHT -> if (ThemeState.consoleLook) {
            val bg = Color(ThemeState.stripeBase)
            if (bg.luminance() > 0.4f) Color.White else androidx.compose.ui.graphics.lerp(bg, Color.White, 0.08f)
        } else return s
        CardTone.GRAPHITE -> Color(0xFF2A2C31)
        CardTone.BLACK -> Color(0xFF0D0D10)
        CardTone.ACCENT -> androidx.compose.ui.graphics.lerp(s.primary, Color(0xFF141A2C), 0.78f)
        CardTone.LIGHT -> Color(0xFFF2F5FA)
        CardTone.CUSTOM -> Color(ThemeState.cardCustom)
    }
    val light = base.luminance() > 0.5f
    val on = if (light) Color(0xFF14203A) else Color.White
    fun shade(k: Float) = if (light) androidx.compose.ui.graphics.lerp(base, Color.Black, k) else androidx.compose.ui.graphics.lerp(base, Color.White, k)
    fun deep(k: Float) = if (light) androidx.compose.ui.graphics.lerp(base, Color.White, k) else androidx.compose.ui.graphics.lerp(base, Color.Black, k)
    return s.copy(
        surfaceContainerLowest = deep(0.3f), surfaceContainerLow = deep(0.15f), surfaceContainer = deep(0.06f),
        surfaceContainerHigh = base, surfaceContainerHighest = shade(0.07f), surfaceVariant = shade(0.07f),
        surface = deep(0.1f), onSurface = on, onSurfaceVariant = on.copy(alpha = 0.66f),
        outline = on.copy(alpha = 0.3f), outlineVariant = on.copy(alpha = 0.15f),
    )
}

@Composable
fun G9Theme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val acc = ThemeState.appAccent
    // Celeste: los colores originales (índigo y celeste). Otro acento: el principal y el secundario de ese color.
    val scheme = (if (dark) DarkScheme else LightScheme).let { s ->
        if (acc == Accent.SKY) s else s.copy(
            primary = acc.color(!dark), onPrimary = if (dark) acc.box(false) else Color.White,
            primaryContainer = acc.box(!dark), onPrimaryContainer = if (dark) acc.box(true) else acc.box(false),
            secondary = acc.color(!dark), onSecondary = if (dark) acc.box(false) else Color.White,
            secondaryContainer = acc.box(!dark), onSecondaryContainer = if (dark) acc.box(true) else acc.box(false),
            tertiary = acc.color(!dark),
        )
    }
    MaterialTheme(
        colorScheme = withCards(scheme),
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content
    )
}

/** Fuente de la app: Nunito (redondeada, de consola; licencia OFL). */
val Nunito = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.nunito_regular, androidx.compose.ui.text.font.FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.nunito_medium, androidx.compose.ui.text.font.FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.nunito_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.nunito_bold, androidx.compose.ui.text.font.FontWeight.Bold),
)

val AppTypography: Typography = Typography().let { t ->
    fun androidx.compose.ui.text.TextStyle.n() = copy(fontFamily = Nunito)
    t.copy(
        displayLarge = t.displayLarge.n(), displayMedium = t.displayMedium.n(), displaySmall = t.displaySmall.n(),
        headlineLarge = t.headlineLarge.n(), headlineMedium = t.headlineMedium.n(), headlineSmall = t.headlineSmall.n(),
        titleLarge = t.titleLarge.n(), titleMedium = t.titleMedium.n(), titleSmall = t.titleSmall.n(),
        bodyLarge = t.bodyLarge.n(), bodyMedium = t.bodyMedium.n(), bodySmall = t.bodySmall.n(),
        labelLarge = t.labelLarge.n(), labelMedium = t.labelMedium.n(), labelSmall = t.labelSmall.n(),
    )
}

/** Para las ventanas del servicio (panel, avisos), que no pasan por G9Theme. */
@Composable
fun AppFont(content: @Composable () -> Unit) {
    MaterialTheme(typography = AppTypography) {
        ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = Nunito), content)
    }
}
