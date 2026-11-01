package io.github.sudodiego.g9control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Puente entre el servicio de accesibilidad (que ve teclas del sistema, como encendido o volumen) y
 * la prueba de botones. Solo tiene un oyente mientras la app está en primer plano.
 */
object KeyBus {
    @Volatile var listener: ((KeyEvent) -> Unit)? = null
}

/** Modo que quiere cada app. */
enum class AutoTarget { XINPUT, DS5 }

/** Dónde aparecen los avisos pequeños (el de volver a Android y la etiqueta del modo). */
enum class NoticePos(val h: Int, val v: Int) {
    TOP_START(-1, -1), TOP(0, -1), TOP_END(1, -1),
    CENTER_START(-1, 0), CENTER(0, 0), CENTER_END(1, 0),
    BOTTOM_START(-1, 1), BOTTOM(0, 1), BOTTOM_END(1, 1);

    /** Nombre para lectores de pantalla: "Arriba a la izquierda"… */
    val label: String get() {
        val row = when (v) { -1 -> tr("Arriba", "Top"); 1 -> tr("Abajo", "Bottom"); else -> tr("Al medio", "Middle") }
        val col = when (h) { -1 -> tr("a la izquierda", "left"); 1 -> tr("a la derecha", "right"); else -> tr("al centro", "center") }
        return "$row $col"
    }

}

/** Sonido al sacar una captura. */
enum class ShotSound { NONE, SHUTTER, JINGLE;
    val label get() = when (this) { NONE -> tr("Sin sonido", "None"); SHUTTER -> tr("Obturador", "Shutter"); JINGLE -> tr("Jingle", "Jingle") }
}

/** Cómo entran y salen los avisos. */
enum class NoticeAnim { SMOOTH, SLIDE, NONE;
    val label get() = when (this) { SMOOTH -> tr("Suave", "Smooth"); SLIDE -> tr("Deslizar", "Slide"); NONE -> tr("Sin animación", "None") }
}

/**
 * Reglas del cambio automático. La app cambia el control desde Android a X-Input o DS5; desde esos
 * modos (y para volver a Android) se usa Start + Select, con una guía en pantalla.
 */
object AutoRules {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean("auto_enabled", true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto_enabled", v).apply()

    /** Recordar volver a Android al salir de una app con regla. */
    fun remindReturn(ctx: Context) = sp(ctx).getBoolean("auto_remind_return", true)
    fun setRemindReturn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto_remind_return", v).apply()

    /** Mientras la guía grande pide Vista + Menú: bloquear botones del control y pantalla táctil. */
    fun guideBlock(ctx: Context) = sp(ctx).getBoolean("guide_block", false)
    fun setGuideBlock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("guide_block", v).apply()

    /** Con bloqueo, también sticks y gatillos: la guía toma el foco (el juego puede pausarse). */
    fun blockSticks(ctx: Context) = sp(ctx).getBoolean("block_sticks", false)
    fun setBlockSticks(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("block_sticks", v).apply()
    /** Lo mismo, para el aviso pequeño. */
    fun miniBlockSticks(ctx: Context) = sp(ctx).getBoolean("mini_block_sticks", false)
    fun setMiniBlockSticks(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mini_block_sticks", v).apply()

    /** Bloquear también con el aviso pequeño (por ejemplo, al salir de un juego). */
    fun miniBlock(ctx: Context) = sp(ctx).getBoolean("mini_block", false)
    fun setMiniBlock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mini_block", v).apply()

    /** Al abrir una app con otro modo: tarjeta en el centro (false) o directamente el aviso pequeño (true). */
    fun guideSmall(ctx: Context) = sp(ctx).getBoolean("guide_small", false)
    fun setGuideSmall(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("guide_small", v).apply()

    /** Mosaico resaltado "Activar Shizuku" en el panel cuando Shizuku está instalado pero no activo. */
    fun shizukuTile(ctx: Context) = sp(ctx).getBoolean("shizuku_tile", true)
    fun setShizukuTile(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("shizuku_tile", v).apply()

    /**
     * La captura la saca el emulador (solo con "Pasar al juego"): Guía + RB le llega y él guarda su propia imagen, sin
     * shaders (con BFI o CRT beam simulator la captura de Android sale con un cuadro a medias). La app solo muestra el
     * aviso y el sonido. Por defecto en RetroArch.
     */
    fun shotByGame(ctx: Context, pkg: String?): Boolean = pkg != null && guideTap(ctx, pkg) == GuideTap.PASS &&
        sp(ctx).getBoolean("shot_by_game_$pkg", pkg.startsWith("com.retroarch"))
    fun setShotByGame(ctx: Context, pkg: String, v: Boolean) = sp(ctx).edit().putBoolean("shot_by_game_$pkg", v).apply()

    /** Aviso de RAOfflineProxy al abrir un emulador sin internet con el proxy apagado. */
    fun raProxyHint(ctx: Context) = sp(ctx).getBoolean("ra_proxy_hint", true)
    fun setRaProxyHint(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ra_proxy_hint", v).apply()

    /** Al empezar a transmitir desde el PC, recordar que las capturas van con M dos veces o L3 + R3. */
    fun streamTip(ctx: Context) = sp(ctx).getBoolean("stream_tip", true)
    fun setStreamTip(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("stream_tip", v).apply()

    /** Aviso de batería baja (tarjeta a 15, 10 y 5 % sin cargador). */
    fun lowBattery(ctx: Context) = sp(ctx).getBoolean("low_battery", true)
    fun setLowBattery(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("low_battery", v).apply()
    val LOW_LEVELS = listOf(20, 15, 10, 5)
    /** A qué niveles avisa (se eligen; por defecto 15, 10 y 5). */
    fun lowLevels(ctx: Context): Set<Int> = sp(ctx).getString("low_levels", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(15, 10, 5)
    fun setLowLevels(ctx: Context, v: Set<Int>) = sp(ctx).edit().putString("low_levels", v.joinToString(",")).apply()
    /** Ajuste fino (dp, + sube) del aviso encima del de Android: cada tablet lo pone un poco distinto. */
    fun toastNudge(ctx: Context) = sp(ctx).getInt("toast_nudge", 0)
    fun setToastNudge(ctx: Context, v: Int) = sp(ctx).edit().putInt("toast_nudge", v).apply()
    /** El aviso de batería baja abajo al centro, encima del toast de ZUI (que no se puede quitar). */
    fun lowBatteryOverToast(ctx: Context) = sp(ctx).getBoolean("low_battery_over_toast", true)
    fun setLowBatteryOverToast(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("low_battery_over_toast", v).apply()
    /** Al enchufar: un aviso con lo que falta para el límite (el propio o el de ZUI) o para 100 %. */
    fun chargeNotice(ctx: Context) = sp(ctx).getBoolean("charge_notice", true)
    /** Al desenchufar (tras 3 s seguros sin cargador): el nivel y cuánto dura. */
    fun unplugNotice(ctx: Context) = sp(ctx).getBoolean("unplug_notice", true)
    fun setUnplugNotice(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("unplug_notice", v).apply()
    fun setChargeNotice(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("charge_notice", v).apply()
    /** Jingle al enchufar (res/raw/charge.wav), apenas se conecta el cargador. */
    fun chargeSound(ctx: Context) = sp(ctx).getBoolean("charge_sound", true)
    fun setChargeSound(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("charge_sound", v).apply()
    fun lowBatterySound(ctx: Context) = sp(ctx).getBoolean("low_battery_sound", true)
    fun setLowBatterySound(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("low_battery_sound", v).apply()

    /** Ráfaga de capturas: mantener RB (con el botón guía) sigue sacando una por segundo. */
    fun burstShots(ctx: Context) = sp(ctx).getBoolean("burst_shots", true)
    fun setBurstShots(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("burst_shots", v).apply()

    /** Al salir de un juego: aviso pequeño (false, lo de siempre) o la tarjeta en el centro (true). */
    fun exitBig(ctx: Context) = sp(ctx).getBoolean("exit_big", false)
    fun setExitBig(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("exit_big", v).apply()

    /** Mostrar la etiqueta del modo cuando la app lo cambia sola. */
    fun showLabel(ctx: Context) = sp(ctx).getBoolean("show_label", true)
    fun setShowLabel(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("show_label", v).apply()

    /** Botones que se pueden juntar en los atajos de dos botones (L3 + R3, R3 + A…). La clave se guarda. */
    enum class PadKey(val code: Int, val label: String) {
        L3(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3"), R3(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3"),
        LB(KeyEvent.KEYCODE_BUTTON_L1, "LB"), RB(KeyEvent.KEYCODE_BUTTON_R1, "RB"),
        SELECT(KeyEvent.KEYCODE_BUTTON_SELECT, "Select"), START(KeyEvent.KEYCODE_BUTTON_START, "Start"),
        A(KeyEvent.KEYCODE_BUTTON_A, "A"), B(KeyEvent.KEYCODE_BUTTON_B, "B"), X(KeyEvent.KEYCODE_BUTTON_X, "X"), Y(KeyEvent.KEYCODE_BUTTON_Y, "Y");
        companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
    }

    /**
     * Atajos de dos botones, para controles sin botón guía (o con una guía que no llega, como en un GameSir que solo
     * la usa para remapear): abrir el panel, sacar una captura, Atrás de Android. Cada uno con sus dos botones y en qué
     * apps funciona ([Where]: todas, solo las elegidas o todas menos las elegidas; las apps se eligen aquí o en la ficha
     * de cada juego de la biblioteca).
     */
    enum class Chord(val id: String, val defA: PadKey, val defB: PadKey) {
        PANEL("panel", PadKey.L3, PadKey.R3), SHOT("shot", PadKey.R3, PadKey.A), BACK("back", PadKey.START, PadKey.SELECT);
        val label get() = when (this) {
            PANEL -> tr("Panel de juego", "Game panel"); SHOT -> tr("Captura", "Screenshot"); BACK -> tr("Atrás", "Back")
        }
        val short get() = when (this) { PANEL -> tr("Panel", "Panel"); SHOT -> tr("Captura", "Screenshot"); BACK -> tr("Atrás", "Back") }
    }
    enum class Where { ALL, ONLY, EXCEPT;
        val label get() = when (this) { ALL -> tr("Todas las apps", "All apps"); ONLY -> tr("Solo en las elegidas", "Only in chosen apps"); EXCEPT -> tr("Todas menos las elegidas", "All but chosen apps") }
    }
    /**
     * Cómo se activa el del panel: tocar (el segundo botón no llega al juego; mantenerlos 0,5 s saca la captura) o
     * mantener 0,6 s (los dos llegan al juego: L3 + R3 sigue sirviendo en juegos que lo usan, como Skyward Sword).
     */
    enum class Press { TAP, HOLD;
        val label get() = when (this) { TAP -> tr("Tocar", "Tap"); HOLD -> tr("Mantener", "Hold") }
    }

    fun chordOn(ctx: Context, c: Chord) = when (c) {
        // Las claves de antes (L3 + R3 y Start + Select), para no perder lo que ya estaba elegido.
        Chord.PANEL -> sp(ctx).getBoolean("game_panel", true)
        Chord.BACK -> sp(ctx).getBoolean("stream_back", true)
        Chord.SHOT -> sp(ctx).getBoolean("chord_shot_on", false)
    }
    fun setChordOn(ctx: Context, c: Chord, v: Boolean) = sp(ctx).edit().putBoolean(when (c) {
        Chord.PANEL -> "game_panel"; Chord.BACK -> "stream_back"; Chord.SHOT -> "chord_shot_on" }, v).apply()
    fun chordKeys(ctx: Context, c: Chord): Pair<PadKey, PadKey> {
        val p = sp(ctx)
        val a = runCatching { PadKey.valueOf(p.getString("chord_${c.id}_a", null)!!) }.getOrDefault(c.defA)
        val b = runCatching { PadKey.valueOf(p.getString("chord_${c.id}_b", null)!!) }.getOrDefault(c.defB)
        return if (a == b) c.defA to c.defB else a to b
    }
    fun setChordKeys(ctx: Context, c: Chord, a: PadKey, b: PadKey) =
        sp(ctx).edit().putString("chord_${c.id}_a", a.name).putString("chord_${c.id}_b", b.name).apply()
    fun chordPress(ctx: Context) = runCatching { Press.valueOf(sp(ctx).getString("chord_panel_press", null)!!) }.getOrDefault(Press.TAP)
    fun setChordPress(ctx: Context, v: Press) = sp(ctx).edit().putString("chord_panel_press", v.name).apply()

    /** Dónde funciona. Antes: L3 + R3 "solo transmitiendo" (= solo en las apps de transmisión); Start + Select, solo ahí. */
    fun chordWhere(ctx: Context, c: Chord): Where = runCatching { Where.valueOf(sp(ctx).getString("chord_${c.id}_where", null)!!) }
        .getOrElse {
            when (c) {
                Chord.PANEL -> if (sp(ctx).getBoolean("chord_streaming_only", false)) Where.ONLY else Where.ALL
                Chord.BACK -> Where.ONLY
                Chord.SHOT -> Where.ALL
            }
        }
    fun setChordWhere(ctx: Context, c: Chord, v: Where) = sp(ctx).edit().putString("chord_${c.id}_where", v.name).apply()
    fun chordApps(ctx: Context, c: Chord): Set<String> = sp(ctx).getString("chord_${c.id}_apps", null)
        ?.split(',')?.filter { it.isNotBlank() }?.toSet()
        ?: emptySet()  // Sin lista de fábrica: cada uno elige sus apps.
    fun setChordApps(ctx: Context, c: Chord, v: Set<String>) = sp(ctx).edit().putString("chord_${c.id}_apps", v.sorted().joinToString(",")).apply()

    /** Funciona en esta app (encendido y según dónde). */
    fun chordActive(ctx: Context, c: Chord, pkg: String?): Boolean {
        if (!chordOn(ctx, c)) return false
        val inList = pkg != null && pkg in chordApps(ctx, c)
        return when (chordWhere(ctx, c)) { Where.ALL -> true; Where.ONLY -> inList; Where.EXCEPT -> !inList }
    }
    /** Desde la ficha de un juego: que funcione o no en esa app (ajusta la lista y, si hace falta, el "dónde"). */
    fun setChordHere(ctx: Context, c: Chord, pkg: String, on: Boolean) {
        if (chordActive(ctx, c, pkg) == on) return
        val apps = chordApps(ctx, c)
        when (chordWhere(ctx, c)) {
            Where.ALL -> { setChordWhere(ctx, c, Where.EXCEPT); setChordApps(ctx, c, setOf(pkg)) }
            Where.EXCEPT -> setChordApps(ctx, c, if (on) apps - pkg else apps + pkg)
            Where.ONLY -> setChordApps(ctx, c, if (on) apps + pkg else apps - pkg)
        }
    }


    /** En DS5, M dos veces (llega como un toque del touchpad) saca la captura limpia del panel de juego. */
    fun touchpadShot(ctx: Context) = sp(ctx).getBoolean("touchpad_shot", true)
    fun setTouchpadShot(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("touchpad_shot", v).apply()

    /**
     * Game Assistant oculto (appops, con Shizuku): G9 Control lo muestra solo mientras lo usa y después lo vuelve
     * a dejar así. Null mientras no se sabe (se toma de cómo esté la primera vez).
     */
    fun gaHidden(ctx: Context): Boolean? = sp(ctx).takeIf { it.contains("ga_hidden") }?.getBoolean("ga_hidden", false)
    fun setGaHidden(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ga_hidden", v).apply()

    /**
     * Bypass automático propio (como el inteligente de Game Assistant, pero con los rangos que se elijan): enchufada,
     * con batería ≥ [smartBypassLevel] y temperatura ≥ [smartBypassTemp] (0 = sin mínimo) — las dos, o con
     * [smartBypassAny] cualquiera de las dos (con calor aunque tenga poca batería) — y si [smartBypassGames], solo en
     * juegos. Necesita Shizuku.
     */
    fun smartBypass(ctx: Context) = sp(ctx).getBoolean("smart_bypass", false)
    fun setSmartBypass(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("smart_bypass", v).apply()
    fun smartBypassLevel(ctx: Context) = sp(ctx).getInt("smart_bypass_level", 40)
    fun setSmartBypassLevel(ctx: Context, v: Int) = sp(ctx).edit().putInt("smart_bypass_level", v).apply()
    fun smartBypassTemp(ctx: Context) = sp(ctx).getInt("smart_bypass_temp", 0)
    fun setSmartBypassTemp(ctx: Context, v: Int) = sp(ctx).edit().putInt("smart_bypass_temp", v).apply()
    fun smartBypassAny(ctx: Context) = sp(ctx).getBoolean("smart_bypass_any", false)
    fun setSmartBypassAny(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("smart_bypass_any", v).apply()
    fun smartBypassGames(ctx: Context) = sp(ctx).getBoolean("smart_bypass_games", true)
    fun setSmartBypassGames(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("smart_bypass_games", v).apply()

    /** Avisos en Inicio: el cambio automático está apagado (hay reglas pero no el servicio) / Shizuku no está listo. */
    fun warnService(ctx: Context) = sp(ctx).getBoolean("warn_service", true)
    fun setWarnService(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("warn_service", v).apply()
    fun warnShizuku(ctx: Context) = sp(ctx).getBoolean("warn_shizuku", true)
    fun setWarnShizuku(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("warn_shizuku", v).apply()

    /** Botones de volumen de la tablet invertidos (el de abajo sube, el de arriba baja). Los del control no cambian. */
    fun swapVolume(ctx: Context) = sp(ctx).getBoolean("swap_volume", false)
    fun setSwapVolume(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("swap_volume", v).apply()
    /**
     * En qué orientaciones se invierte (Surface.ROTATION_*): según dónde queden los botones, en unas el de arriba
     * sube y en otras baja. Por defecto horizontal (90) y vertical invertido (180), como en la Y700.
     */
    fun swapRotations(ctx: Context): Set<Int> = (sp(ctx).getString("swap_volume_rotations", null) ?: "1,2")
        .split(',').mapNotNull { it.toIntOrNull() }.toSet()
    fun setSwapRotations(ctx: Context, v: Set<Int>) = sp(ctx).edit().putString("swap_volume_rotations", v.sorted().joinToString(",")).apply()

    /** Botón Legion (Home del control): tocar = Atrás, mantener = panel de juego. */
    fun legionKeys(ctx: Context) = sp(ctx).getBoolean("legion_keys", true)

    /** Cada atajo del botón guía, por separado (con el botón guía activado). */
    enum class Guide(val key: String) { TAP_BACK("guide_tap_back"), HOLD_PANEL("guide_hold_panel"), RB_SHOT("guide_rb_shot"), LB_BACK("guide_lb_back"), HOLD_LOCK("guide_hold_lock"), LT_REC("guide_lt_rec"), RT_SHADOW("guide_rt_shadow") }

    /** Lo que puede hacer Guía + RB / LB / LT / RT (cada una se elige en Botón guía, manteniendo su tarjeta). */
    enum class ComboAction { SHOT, BACK_HOLD, RECORD, SHADOW, PANEL, HOME, RECENTS, ROTATE;
        val label get() = when (this) {
            SHOT -> tr("Captura", "Screenshot"); BACK_HOLD -> tr("Mantener Atrás", "Hold Back"); RECORD -> tr("Grabar", "Record")
            SHADOW -> "Shadow Rec"; PANEL -> tr("Panel de juego", "Game panel"); HOME -> tr("Inicio", "Home"); RECENTS -> tr("Recientes", "Recents")
            ROTATE -> tr("Rotar", "Rotate")
        }
    }
    /** Las combinaciones con la guía: su interruptor ([Guide]), el botón y lo que hacen por defecto. */
    enum class GuideCombo(val guide: Guide, val code: Int, val key: String, val def: ComboAction) {
        RB(Guide.RB_SHOT, KeyEvent.KEYCODE_BUTTON_R1, "RB", ComboAction.SHOT),
        LB(Guide.LB_BACK, KeyEvent.KEYCODE_BUTTON_L1, "LB", ComboAction.BACK_HOLD),
        LT(Guide.LT_REC, KeyEvent.KEYCODE_BUTTON_L2, "LT", ComboAction.RECORD),
        RT(Guide.RT_SHADOW, KeyEvent.KEYCODE_BUTTON_R2, "RT", ComboAction.SHADOW);
        companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
    }
    fun comboAction(ctx: Context, c: GuideCombo) = runCatching { ComboAction.valueOf(sp(ctx).getString("combo_action_${c.name}", null)!!) }.getOrDefault(c.def)
    fun setComboAction(ctx: Context, c: GuideCombo, v: ComboAction) = sp(ctx).edit().putString("combo_action_${c.name}", v.name).apply()

    /** Qué puede hacer el botón guía al tocarlo, tocarlo dos veces o mantenerlo. La clave se guarda. */
    enum class GuideAction { NONE, BACK, HOME, RECENTS, PANEL, SHOT, NOTIFICATIONS, QUICK_SETTINGS;
        val label get() = when (this) {
            NONE -> tr("Nada", "Nothing"); BACK -> tr("Atrás", "Back"); HOME -> tr("Inicio", "Home"); RECENTS -> tr("Recientes", "Recents")
            PANEL -> tr("Panel de juego", "Game panel"); SHOT -> tr("Captura", "Screenshot")
            NOTIFICATIONS -> tr("Notificaciones", "Notifications"); QUICK_SETTINGS -> tr("Ajustes rápidos", "Quick settings")
        }
    }
    enum class GuidePress { TAP, DOUBLE, HOLD;
        val label get() = when (this) { TAP -> tr("Tocar", "Tap"); DOUBLE -> tr("Tocar dos veces", "Double tap"); HOLD -> tr("Mantener", "Hold") }
    }
    /**
     * Por defecto tocar = Atrás, dos veces = nada (así tocar no espera a ver si viene otro) y mantener = panel. Si antes
     * se había apagado "Tocar: Atrás" o "Mantener: panel", queda en Nada.
     */
    fun guideAction(ctx: Context, p: GuidePress): GuideAction = runCatching { GuideAction.valueOf(sp(ctx).getString("guide_action_${p.name.lowercase()}", null)!!) }
        .getOrElse {
            when (p) {
                GuidePress.TAP -> if (guide(ctx, Guide.TAP_BACK)) GuideAction.BACK else GuideAction.NONE
                GuidePress.HOLD -> if (guide(ctx, Guide.HOLD_PANEL)) GuideAction.PANEL else GuideAction.NONE
                GuidePress.DOUBLE -> GuideAction.NONE
            }
        }
    fun setGuideAction(ctx: Context, p: GuidePress, v: GuideAction) = sp(ctx).edit().putString("guide_action_${p.name.lowercase()}", v.name).apply()

    /**
     * Qué hace tocar la guía en una app: Atrás (lo normal), nada, o pasarla al juego (la app no la toma: el emulador
     * recibe la guía real del G9 y la puede asignar; mantener tampoco abre el panel, se usa L3 + R3). Enviar botones
     * desde la app no sirve: Android 15 los marca como de un dispositivo "Virtual" y RetroArch no los toma como del
     * jugador 1 (probado el 12 oct 2026).
     */
    enum class GuideTap { BACK, PASS, NOTHING;
        val label get() = when (this) {
            BACK -> tr("Como siempre", "As usual"); PASS -> tr("Pasar al juego", "Pass to the game"); NOTHING -> tr("Nada", "Nothing")
        }
    }
    /** RetroArch: pasar la guía al juego (se asigna en RetroArch al menú o a salir). */
    fun guideTap(ctx: Context, pkg: String?): GuideTap = pkg?.let { p ->
        runCatching { GuideTap.valueOf(sp(ctx).getString("guide_tap_app_$p", null)!!) }.getOrNull()
            ?: if (p.startsWith("com.retroarch")) GuideTap.PASS else null
    } ?: GuideTap.BACK
    fun setGuideTap(ctx: Context, pkg: String, v: GuideTap) = sp(ctx).edit().putString("guide_tap_app_$pkg", v.name).apply()
    fun guide(ctx: Context, g: Guide) = sp(ctx).getBoolean(g.key, true)
    fun setGuide(ctx: Context, g: Guide, v: Boolean) = sp(ctx).edit().putBoolean(g.key, v).apply()

    /**
     * Apps de streaming de PC o consola: ahí el botón Legion pasa normal (es el botón guía del PC: modo Xbox, Game
     * Bar). En Moonlight y Artemis solo durante la transmisión ([STREAM_ACTIVITY]); en su lista de PCs sigue siendo
     * Atrás, para poder salir. Si la app toma el control por USB (Artemis en X-Input), Android ni lo ve y da igual.
     */
    val STREAMING = setOf(
        "com.limelight", "com.limelight.noir", "com.limelight.unofficial",   // Moonlight, Artemis
        "com.valvesoftware.steamlink", "com.microsoft.xboxone.smartglass", "com.gamepass",
        "com.metallic.chiaki", "com.playstation.remoteplay", "tv.parsec.client", "com.nvidia.geforcenow",
    )
    /** La pantalla de transmisión de Moonlight y sus derivados (Artemis). */
    const val STREAM_ACTIVITY = "com.limelight.Game"
    fun setLegionKeys(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("legion_keys", v).apply()

    /** Aviso de captura: dónde sale (por defecto arriba a la izquierda, como en la Switch) y con qué sonido. */
    fun shotPos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("shot_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_START)
    fun setShotPos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("shot_pos", v.name).apply()
    /** Monitor de rendimiento sobre el juego (se enciende desde el panel) y su posición. */
    fun perfHud(ctx: Context) = sp(ctx).getBoolean("perf_hud", false)
    fun setPerfHud(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("perf_hud", v).apply()
    /** El mosaico del monitor (Inicio y panel): Apagado → Barra → Columna → Apagado. */
    fun cycleHud(ctx: Context) = when {
        !perfHud(ctx) -> { setPerfList(ctx, false); setPerfHud(ctx, true) }
        !perfList(ctx) -> setPerfList(ctx, true)
        else -> setPerfHud(ctx, false)
    }
    fun hudLabel(ctx: Context) = when {
        !perfHud(ctx) -> tr("Apagado", "Off")
        perfList(ctx) -> tr("Columna", "Column")
        else -> tr("Barra", "Bar")
    }
    /** Barra completa (dos líneas: además watts, tiempo restante, Hz y calor de la tablet) o compacta. */
    fun perfFull(ctx: Context) = sp(ctx).getBoolean("perf_full", true)
    fun setPerfFull(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("perf_full", v).apply()
    /** Estilo lista (como MangoHUD: una fila por dato, con el gráfico de tiempos de cuadro) en vez de la barra. */
    fun perfList(ctx: Context) = sp(ctx).getBoolean("perf_list", false)
    fun setPerfList(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("perf_list", v).apply()
    /** Datos del monitor, a elección (los dos estilos muestran los mismos). */
    enum class PerfItem(val es: String, val en: String) {
        FPS("FPS", "FPS"), GRAPH("Gráfico", "Graph"), CPU("CPU", "CPU"), GPU("GPU", "GPU"), MAX("Tope de CPU", "CPU cap"),
        BAT("Batería", "Battery"), RAM("RAM", "RAM"), PWR("Watts", "Watts"), LEFT("Tiempo", "Time"), HZ("Hz", "Hz");
        val label get() = tr(es, en)
    }
    fun perfItems(ctx: Context): Set<PerfItem> {
        val saved = sp(ctx).getStringSet("perf_items", null)
        // Sin elegir todavía: todo, salvo lo que "Más datos" apagaba (RAM, W, tiempo, Hz y tope) si estaba apagado.
        if (saved == null) return if (perfFull(ctx)) PerfItem.entries.toSet() else setOf(PerfItem.FPS, PerfItem.GRAPH, PerfItem.CPU, PerfItem.GPU, PerfItem.BAT)
        return saved.mapNotNull { runCatching { PerfItem.valueOf(it) }.getOrNull() }.toSet()
    }
    fun setPerfItems(ctx: Context, v: Set<PerfItem>) = sp(ctx).edit().putStringSet("perf_items", v.map { it.name }.toSet()).apply()
    fun perfPos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("perf_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_START)
    fun setPerfPos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("perf_pos", v.name).apply()
    fun shotSound(ctx: Context) = runCatching { ShotSound.valueOf(sp(ctx).getString("shot_sound", null)!!) }
        .getOrDefault(ShotSound.JINGLE)
    fun setShotSound(ctx: Context, v: ShotSound) = sp(ctx).edit().putString("shot_sound", v.name).apply()
    /**
     * Aviso de TBF también con las capturas de Android (botones de la tablet, ajustes rápidos): se ve aparecer la imagen
     * en Pictures/Screenshots. Necesita el permiso de fotos, que se pide solo para esto: sin él, como apagada.
     */
    fun anyShot(ctx: Context) = sp(ctx).getBoolean("any_shot", false) && hasMediaPermission(ctx)
    fun setAnyShot(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("any_shot", v).apply()
    val MEDIA_PERMISSION = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
    fun hasMediaPermission(ctx: Context) = ctx.checkSelfPermission(MEDIA_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    /** Captura que sacó Android (botón de captura del control): el aviso abajo al centro, encima del toast de Android. */
    fun shotOverToast(ctx: Context) = sp(ctx).getBoolean("shot_over_toast", true)
    fun setShotOverToast(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("shot_over_toast", v).apply()
    fun shotAnim(ctx: Context) = runCatching { NoticeAnim.valueOf(sp(ctx).getString("shot_anim", null)!!) }
        .getOrDefault(NoticeAnim.SLIDE)
    fun setShotAnim(ctx: Context, v: NoticeAnim) = sp(ctx).edit().putString("shot_anim", v.name).apply()

    fun noticePos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("notice_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_END)
    fun setNoticePos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("notice_pos", v.name).apply()

    /** Posición de la guía pequeña (volver a Android); por defecto arriba al centro, como antes. */
    fun guidePos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("guide_pos", null)!!) }
        .getOrDefault(NoticePos.TOP)
    fun setGuidePos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("guide_pos", v.name).apply()

    fun noticeAnim(ctx: Context) = runCatching { NoticeAnim.valueOf(sp(ctx).getString("notice_anim", null)!!) }
        .getOrDefault(NoticeAnim.SMOOTH)
    fun setNoticeAnim(ctx: Context, v: NoticeAnim) = sp(ctx).edit().putString("notice_anim", v.name).apply()

    /** Pide al servicio mostrar un ejemplo del aviso (para ver posición y animación). */
    fun preview(ctx: Context, kind: String) =
        ctx.sendBroadcast(Intent(ACTION_PREVIEW).setPackage(ctx.packageName).putExtra("kind", kind))
    const val ACTION_PREVIEW = "io.github.sudodiego.g9control.PREVIEW_NOTICE"

    /** Cambio pedido a mano (ajustes rápidos): el servicio cambia por USB o muestra el aviso de Vista + Menú. */
    fun requestSwitch(ctx: Context, to: PadMode) =
        ctx.sendBroadcast(Intent(ACTION_SWITCH).setPackage(ctx.packageName).putExtra("target", to.name))
    const val ACTION_SWITCH = "io.github.sudodiego.g9control.SWITCH_MODE"

    /** Desde la app: volver a oscurecer la tablet con el monitor (después de encenderla con el botón). */
    fun darkenTablet(ctx: Context) = ctx.sendBroadcast(Intent(ACTION_DARKEN).setPackage(ctx.packageName))
    const val ACTION_DARKEN = "io.github.sudodiego.g9control.DARKEN_TABLET"

    fun rules(ctx: Context): Map<String, AutoTarget> {
        val x = sp(ctx).getStringSet("auto_xinput", emptySet()).orEmpty().associateWith { AutoTarget.XINPUT }
        val d = sp(ctx).getStringSet("auto_ds5", emptySet()).orEmpty().associateWith { AutoTarget.DS5 }
        return x + d
    }

    fun setRules(ctx: Context, v: Map<String, AutoTarget>) = sp(ctx).edit()
        .putStringSet("auto_xinput", v.filterValues { it == AutoTarget.XINPUT }.keys)
        .putStringSet("auto_ds5", v.filterValues { it == AutoTarget.DS5 }.keys)
        .apply()

    /** true si el servicio de accesibilidad de la app está activado en los ajustes de Android. */
    fun serviceEnabled(ctx: Context): Boolean {
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, AutoModeService::class.java)
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Permiso de las ROMs chinas para leer la lista de apps; en Android estándar no existe. */
    const val PERM_APP_LIST = "com.android.permission.GET_INSTALLED_APPS"

    fun openServiceSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

