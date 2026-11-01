package io.github.sudodiego.g9control

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.WindowManager

/**
 * Rotar la pantalla desde el panel (o una combinación) sin depender del sensor: de a 90° o solo entre las dos
 * horizontales o las dos verticales. Con Shizuku cambia la rotación del sistema (rotación automática apagada); sin él,
 * una ventana invisible del servicio pide la orientación. "Automática" lo devuelve como estaba.
 */
object Rotation {
    enum class Cycle { ALL, LANDSCAPE, PORTRAIT;
        val label get() = when (this) {
            ALL -> tr("De a 90°", "90° steps"); LANDSCAPE -> tr("Solo horizontal", "Landscape only"); PORTRAIT -> tr("Solo vertical", "Portrait only")
        }
    }
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)
    fun cycle(ctx: Context) = runCatching { Cycle.valueOf(sp(ctx).getString("rotate_cycle", null)!!) }.getOrDefault(Cycle.LANDSCAPE)
    fun setCycle(ctx: Context, v: Cycle) = sp(ctx).edit().putString("rotate_cycle", v.name).apply()

    /** La rotación fijada ahora (0–3), o null si es la del sistema. */
    var locked: Int? = null
        private set
    private var view: android.view.View? = null

    private fun display(ctx: Context) = ctx.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)

    fun label(r: Int?, ctx: Context): String {
        if (r == null) return tr("Automática", "Automatic")
        val land = r in landscapeRots(ctx)
        val rev = r >= 2
        return when { land && !rev -> tr("Horizontal", "Landscape"); land -> tr("Horizontal invertida", "Reverse landscape")
            !rev -> tr("Vertical", "Portrait"); else -> tr("Vertical invertida", "Reverse portrait") }
    }

    /** Las rotaciones horizontales del equipo (dependen de si su orientación natural es horizontal). */
    private fun landscapeRots(ctx: Context): Set<Int> {
        val r = display(ctx)?.rotation ?: 0
        val landNow = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val naturalLand = (r == Surface.ROTATION_0 || r == Surface.ROTATION_180) == landNow
        return if (naturalLand) setOf(0, 2) else setOf(1, 3)
    }

    /** La siguiente rotación según el ciclo elegido. */
    suspend fun next(svc: android.accessibilityservice.AccessibilityService) {
        val cur = locked ?: display(svc)?.rotation ?: 0
        val land = landscapeRots(svc)
        val to = when (cycle(svc)) {
            Cycle.ALL -> (cur + 1) % 4
            Cycle.LANDSCAPE -> if (cur in land) land.first { it != cur } else land.min()
            Cycle.PORTRAIT -> { val port = (0..3).filter { it !in land }; if (cur in port) port.first { it != cur } else port.min() }
        }
        set(svc, to)
    }

    suspend fun set(svc: android.accessibilityservice.AccessibilityService, r: Int?) {
        locked = r
        if (Shell.state == Shell.State.READY) {
            removeView(svc)
            if (r == null) Shell.run(Shell.autoRotate(true))
            else { Shell.run(Shell.autoRotate(false)); Shell.run(Shell.userRotation(r)) }
            return
        }
        // Sin Shizuku: una ventana de 1 px que pide la orientación (las de las apps de rotación hacen lo mismo).
        svc.mainExecutor.execute {
            val wm = svc.getSystemService(WindowManager::class.java)
            if (r == null) { removeView(svc); return@execute }
            val land = r in landscapeRots(svc)
            val o = when { land && r < 2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; land -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                r < 2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; else -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT }
            val lp = (view?.layoutParams as? WindowManager.LayoutParams) ?: WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT)
            lp.screenOrientation = o
            val v = view
            if (v == null) { val nv = android.view.View(svc); if (runCatching { wm.addView(nv, lp) }.isSuccess) view = nv }
            else runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    private fun removeView(ctx: Context) {
        view?.let { v -> runCatching { ctx.getSystemService(WindowManager::class.java).removeView(v) } }
        view = null
    }
}
