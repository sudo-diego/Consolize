package io.github.sudodiego.g9control

import android.content.Context
import android.provider.Settings
import android.view.Surface

/**
 * Rotar la pantalla desde el panel (o una combinación con la guía), sin el sensor. Se eligen las orientaciones entre
 * las que va (por ejemplo, solo horizontal ↔ vertical, o horizontal ↔ horizontal invertida). Necesita Shizuku: cambia la
 * rotación del sistema (con la automática apagada). "Como estaba" deja la rotación y el giro automático de antes.
 */
object Rotation {
    /** Las cuatro orientaciones, en el orden en que se recorren. */
    enum class Orient { LANDSCAPE, PORTRAIT, REVERSE_LANDSCAPE, REVERSE_PORTRAIT;
        val label get() = when (this) {
            LANDSCAPE -> tr("Horizontal", "Landscape"); PORTRAIT -> tr("Vertical", "Portrait")
            REVERSE_LANDSCAPE -> tr("Horizontal invertida", "Reverse landscape"); REVERSE_PORTRAIT -> tr("Vertical invertida", "Reverse portrait")
        }
    }
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)
    fun chosen(ctx: Context): List<Orient> = sp(ctx).getString("rotate_set", null)?.split(',')?.mapNotNull { runCatching { Orient.valueOf(it) }.getOrNull() }
        ?.takeIf { it.size >= 2 } ?: listOf(Orient.LANDSCAPE, Orient.PORTRAIT)
    fun setChosen(ctx: Context, v: Set<Orient>) = sp(ctx).edit().putString("rotate_set", Orient.entries.filter { it in v }.joinToString(",") { it.name }).apply()

    val available get() = Shell.state == Shell.State.READY

    /** Lo que había antes de la primera rotación (automática sí/no y rotación), para volver a eso. */
    private var before: Pair<Int, Int>? = null
    var current: Orient? = null
        private set

    private fun display(ctx: Context) = ctx.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)

    /** Rotación del sistema (0–3) de cada orientación: depende de si la tablet es horizontal de fábrica. */
    private fun rotationOf(ctx: Context, o: Orient): Int {
        val r = display(ctx)?.rotation ?: 0
        val landNow = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val naturalLand = (r == Surface.ROTATION_0 || r == Surface.ROTATION_180) == landNow
        return if (naturalLand) when (o) { Orient.LANDSCAPE -> 0; Orient.PORTRAIT -> 1; Orient.REVERSE_LANDSCAPE -> 2; Orient.REVERSE_PORTRAIT -> 3 }
        else when (o) { Orient.PORTRAIT -> 0; Orient.LANDSCAPE -> 1; Orient.REVERSE_PORTRAIT -> 2; Orient.REVERSE_LANDSCAPE -> 3 }
    }
    private fun orientNow(ctx: Context): Orient {
        val r = display(ctx)?.rotation ?: 0
        return Orient.entries.firstOrNull { rotationOf(ctx, it) == r } ?: Orient.LANDSCAPE
    }

    /** La siguiente de las elegidas (desde la de ahora). Devuelve a cuál fue, o null si no se pudo. */
    suspend fun next(ctx: Context): Orient? {
        if (!available) return null
        val list = chosen(ctx)
        val now = current ?: orientNow(ctx)
        val to = list.getOrNull((list.indexOf(now) + 1) % list.size) ?: list.first()
        if (before == null) before = runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION) to Settings.System.getInt(ctx.contentResolver, Settings.System.USER_ROTATION)
        }.getOrNull()
        Shell.run(Shell.autoRotate(false)) ?: return null
        Shell.run(Shell.userRotation(rotationOf(ctx, to))) ?: return null
        current = to
        return to
    }

    /** Vuelve a como estaba antes de rotar con la app (incluido el bloqueo de orientación que tenía). */
    suspend fun restore() {
        val b = before ?: return
        Shell.run(Shell.userRotation(b.second.coerceIn(0, 3)))
        Shell.run(Shell.autoRotate(b.first == 1))
        before = null; current = null
    }
}
