package io.github.sudodiego.g9control

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display

/**
 * Monitor externo (por USB-C/DP o HDMI). La pantalla 16:9 sirve en modo espejo (la tablet se ve en el monitor):
 * así la imagen llena un monitor 16:9 sin bordes. En modo extendido (escritorio de ZUI) el monitor tiene su propia
 * resolución y no hace falta. Hay una elección para todos los monitores y cada uno puede tener la suya; los que se
 * han conectado quedan en una lista (fabricante + modelo, de la propia pantalla).
 *
 * Con un monitor en espejo, la pantalla de la tablet puede quedar oscura o apagada: el monitor
 * sigue mostrando la imagen.
 */
object Monitor {
    /** Un monitor: [key] lo identifica entre conexiones; [name] es el que reporta (su nombre comercial). */
    data class Info(val key: String, val name: String)

    /** Proporción con un monitor: la de todos, o una propia. */
    enum class Fit { ALL, WIDE, NATIVE }

    /** La pantalla de la tablet con un monitor en espejo. */
    enum class Screen { NORMAL, DIM, NO_LIGHT, OFF }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    /** El monitor conectado ahora, o null. */
    fun current(ctx: Context): Info? {
        val d = ctx.getSystemService(DisplayManager::class.java).displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && (it.flags and Display.FLAG_PRESENTATION) != 0 } ?: return null
        val info = if (Build.VERSION.SDK_INT >= 31) d.deviceProductInfo else null
        val key = info?.let { "${it.manufacturerPnpId}:${it.productId}" } ?: d.name
        val name = info?.name?.takeIf { it.isNotBlank() } ?: d.name
        return Info(key, name)
    }

    /** ZUI está en modo espejo (no en el extendido/escritorio). */
    fun mirror(ctx: Context) =
        runCatching { Settings.System.getInt(ctx.contentResolver, MODE_KEY, 0) }.getOrDefault(0) != 1
    const val MODE_KEY = "zui_dp_display_pc_mode"

    /** 16:9 en cualquier monitor (salvo los que tengan la suya). */
    fun all169(ctx: Context) = sp(ctx).getBoolean("monitor_all_169", false)
    fun setAll169(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("monitor_all_169", v).apply()

    fun fit(ctx: Context, key: String): Fit {
        val p = sp(ctx)
        p.getString("monitor_fit_$key", null)?.let { s -> return Fit.entries.firstOrNull { it.name == s } ?: Fit.ALL }
        // La primera versión guardaba solo sí/no por monitor.
        return if (p.contains("monitor_169_$key")) (if (p.getBoolean("monitor_169_$key", false)) Fit.WIDE else Fit.NATIVE) else Fit.ALL
    }
    fun setFit(ctx: Context, key: String, f: Fit) =
        sp(ctx).edit().putString("monitor_fit_$key", f.name).remove("monitor_169_$key").apply()

    /** Pantalla 16:9 con este monitor. */
    fun wants169(ctx: Context, key: String) = when (fit(ctx, key)) {
        Fit.WIDE -> true
        Fit.NATIVE -> false
        Fit.ALL -> all169(ctx)
    }

    /** Los monitores que se han conectado, el último primero. */
    fun known(ctx: Context): List<Info> {
        val p = sp(ctx)
        return p.getStringSet("monitors", emptySet())!!
            .sortedByDescending { p.getLong("monitor_seen_$it", 0) }
            .map { Info(it, p.getString("monitor_name_$it", it)!!) }
    }
    fun remember(ctx: Context, m: Info) {
        val p = sp(ctx)
        p.edit().putStringSet("monitors", p.getStringSet("monitors", emptySet())!! + m.key)
            .putString("monitor_name_${m.key}", m.name).putLong("monitor_seen_${m.key}", System.currentTimeMillis()).apply()
    }
    fun forget(ctx: Context, key: String) {
        val p = sp(ctx)
        p.edit().putStringSet("monitors", p.getStringSet("monitors", emptySet())!! - key)
            .remove("monitor_name_$key").remove("monitor_seen_$key").remove("monitor_fit_$key").remove("monitor_169_$key").apply()
    }

    /** G9 Control puso el 16:9 por el monitor: al desconectarlo lo deshace (solo lo que hizo él). */
    fun applied(ctx: Context) = sp(ctx).getBoolean("monitor_applied", false)
    fun setApplied(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("monitor_applied", v).apply()

    /** Cambio a mano con un monitor en espejo: queda como la elección de ese monitor (o vuelve a "la de todos" si coincide). */
    fun rememberChoice(ctx: Context, wide: Boolean) {
        val m = current(ctx) ?: return
        if (!mirror(ctx)) return
        setFit(ctx, m.key, if (wide == all169(ctx)) Fit.ALL else if (wide) Fit.WIDE else Fit.NATIVE)
        setApplied(ctx, wide)
    }

    /** La tablet se encendió a mano (botón de encendido o panel de juego) con el monitor conectado: no se oscurece sola. */
    @Volatile var woken = false

    fun screen(ctx: Context) = sp(ctx).getString("monitor_screen", null)
        ?.let { s -> Screen.entries.firstOrNull { it.name == s } } ?: Screen.NORMAL
    fun setScreen(ctx: Context, v: Screen) = sp(ctx).edit().putString("monitor_screen", v.name).apply()
}
