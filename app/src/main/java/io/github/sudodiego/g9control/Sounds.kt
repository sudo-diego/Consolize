package io.github.sudodiego.g9control

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Sonidos de navegación (moverse, elegir, volver, cambiar de pestaña): WAV de res/raw (nav_*.wav), hechos una vez en
 * el PC como los de captura (no se generan en la app). Van con SoundPool y **sin pedir el foco de audio**: se
 * mezclan encima de lo que suene, así la música de un launcher (que la pausa cuando otra app pide el audio) no se corta. En la
 * app si están encendidos; en el panel de juego solo si además se pidió ([AutoRules]-like prefs en [Prefs]).
 */
/**
 * Juegos de sonidos de navegación (tools/sounds/make_nav_sounds.py los genera; "Clásico", los de siempre). Las referencias
 * a R.raw van escritas (no por nombre): así la reducción de recursos de la versión publicada no los borra.
 */
enum class NavPack {
    CLASSIC, BUBBLE, GLASS, WOOD, SOFT, DIGITAL;
    val label get() = when (this) {
        CLASSIC -> tr("Clásico", "Classic"); BUBBLE -> tr("Burbuja", "Bubble"); GLASS -> tr("Cristal", "Glass")
        WOOD -> tr("Madera", "Wood"); SOFT -> tr("Suave", "Soft"); DIGITAL -> tr("Digital", "Digital")
    }
    /** Mover, elegir, volver, sección, abrir el panel y salir. */
    val raw: List<Int> get() = when (this) {
        CLASSIC -> listOf(R.raw.nav_move, R.raw.nav_select, R.raw.nav_back, R.raw.nav_tab, R.raw.nav_open, R.raw.nav_exit)
        BUBBLE -> listOf(R.raw.nav_burbuja_move, R.raw.nav_burbuja_select, R.raw.nav_burbuja_back, R.raw.nav_burbuja_tab, R.raw.nav_burbuja_open, R.raw.nav_burbuja_exit)
        GLASS -> listOf(R.raw.nav_cristal_move, R.raw.nav_cristal_select, R.raw.nav_cristal_back, R.raw.nav_cristal_tab, R.raw.nav_cristal_open, R.raw.nav_cristal_exit)
        WOOD -> listOf(R.raw.nav_madera_move, R.raw.nav_madera_select, R.raw.nav_madera_back, R.raw.nav_madera_tab, R.raw.nav_madera_open, R.raw.nav_madera_exit)
        SOFT -> listOf(R.raw.nav_suave_move, R.raw.nav_suave_select, R.raw.nav_suave_back, R.raw.nav_suave_tab, R.raw.nav_suave_open, R.raw.nav_suave_exit)
        DIGITAL -> listOf(R.raw.nav_digital_move, R.raw.nav_digital_select, R.raw.nav_digital_back, R.raw.nav_digital_tab, R.raw.nav_digital_open, R.raw.nav_digital_exit)
    }
}

object UiSounds {
    /** MOVE: se movió la selección; SELECT: A; BACK: se cerró o volvió algo; TAB: otra pestaña o sección; OPEN: se abrió el panel; EXIT: se salió de la app. */
    enum class Kind { MOVE, SELECT, BACK, TAB, OPEN, EXIT }

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Kind, Int>()
    private var lastMove = 0L

    private fun ensure(ctx: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            // Por el volumen de juegos/multimedia: el de "sonidos del sistema" suele estar en 0 en la tablet (no se oía).
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        val app = ctx.applicationContext
        val raw = Prefs.navPack(ctx).raw
        Kind.entries.forEachIndexed { i, k -> ids[k] = p.load(app, raw[i], 1) }
        pool = p
        return p
    }

    /** Otro juego elegido: se sueltan los cargados y se cargan los nuevos (y suena una muestra cuando están). */
    fun reload(ctx: Context, sample: Kind? = Kind.SELECT) {
        pool?.release(); pool = null; ids.clear()
        val p = runCatching { ensure(ctx) }.getOrNull() ?: return
        val want = sample?.let { ids[it] } ?: return
        p.setOnLoadCompleteListener { sp, id, _ -> if (id == want) { val v = Prefs.navVolume(ctx) / 100f; sp.play(id, v, v, 1, 0, 1f) } }
    }

    /** Cargar los sonidos de antemano (SoundPool carga en segundo plano: el primero se perdía). */
    fun preload(ctx: Context) { if (Prefs.navSounds(ctx)) runCatching { ensure(ctx) } }

    /** [panel]: viene del panel de juego (suena solo con "También en el panel"). */
    fun play(ctx: Context, kind: Kind, panel: Boolean = false) {
        if (!Prefs.navSounds(ctx) || (panel && !Prefs.navSoundsPanel(ctx))) return
        // Moverse rápido (mantener la cruceta) no hace un zumbido: como mucho uno cada 45 ms.
        if (kind == Kind.MOVE) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastMove < 45) return
            lastMove = now
        }
        val v = Prefs.navVolume(ctx) / 100f
        runCatching { ensure(ctx).let { p -> ids[kind]?.let { p.play(it, v, v, 1, 0, 1f) } } }
    }
}

/**
 * Música ambiental de la app: un archivo de audio que se elige (no se incluye música: derechos de autor), en bucle,
 * solo mientras la app está adelante. Pide el foco de audio al empezar (lo que suene detrás se pausa) y
 * lo suelta al salir, para que lo de detrás vuelva. Si otra app pide el audio, se pausa.
 */
object AmbientMusic {
    /** Para Ajustes: encendida y con archivo (cambia al elegir uno, sin volver a abrir la página). */
    var on by androidx.compose.runtime.mutableStateOf(false)
    var hasFile by androidx.compose.runtime.mutableStateOf(false)
    fun load(ctx: Context) { on = Prefs.music(ctx); hasFile = Prefs.musicUri(ctx) != null }

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    fun start(ctx: Context) {
        val uri = Prefs.musicUri(ctx)
        if (!Prefs.music(ctx) || uri == null) return
        // Ya estaba: sigue donde iba (antes se creaba de nuevo y la canción volvía al principio cada vez que la app
        // perdía el frente un momento, como al pedir un permiso al entrar a Biblioteca).
        player?.let { p -> focus?.let { f -> if (ctx.getSystemService(AudioManager::class.java).requestAudioFocus(f) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) p.start() }; return }
        val audio = ctx.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        runCatching {
            val p = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(ctx.applicationContext, Uri.parse(uri))
                isLooping = true
                val v = Prefs.musicVolume(ctx) / 100f
                setVolume(v, v)
                prepare()
            }
            val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
                        AudioManager.AUDIOFOCUS_GAIN -> player?.start()
                    }
                }.build()
            if (audio.requestAudioFocus(f) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) p.start()
            player = p; focus = f
        }.onFailure { android.util.Log.w("TBF", "música ambiental", it) }
    }

    /** La app dejó el frente: en pausa, sin perder dónde iba; suelta el audio para que vuelva lo de detrás. */
    fun stop(ctx: Context) {
        player?.runCatching { if (isPlaying) pause() }
        focus?.let { ctx.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
    }

    private fun release(ctx: Context) {
        player?.runCatching { stop(); release() }
        player = null
        focus?.let { ctx.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    /** Volumen cambiado en Ajustes: se aplica al instante. */
    fun setVolume(ctx: Context) { val v = Prefs.musicVolume(ctx) / 100f; player?.setVolume(v, v) }

    /** Encendida, apagada o con otro archivo: se reinicia. */
    fun restart(ctx: Context) { release(ctx); start(ctx) }
}
