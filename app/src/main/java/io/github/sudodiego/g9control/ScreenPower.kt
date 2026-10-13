package io.github.sudodiego.g9control

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder

/**
 * Apaga o enciende el panel de la tablet sin apagar el sistema: el monitor sigue
 * mostrando la imagen. Corre como proceso aparte con permisos de shell (Shizuku): `app_process` con esta APK.
 * Dos formas: `power 0|2` apaga el panel o lo deja normal; `light -1|<0..1>` apaga solo la luz
 * (brillo -1) o la devuelve. El botón de encendido también lo vuelve a encender.
 */
@SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi") // corre fuera de la app, como shell
object ScreenPower {
    @JvmStatic
    fun main(args: Array<String>) {
        val sc = Class.forName("android.view.SurfaceControl")
        val value = args.getOrNull(1) ?: "2"
        when (args.firstOrNull()) {
            "light" -> {
                val set = sc.getMethod("setDisplayBrightness", IBinder::class.java, Float::class.javaPrimitiveType)
                for (token in tokens(sc)) set.invoke(null, token, value.toFloat())
            }
            else -> {
                val set = sc.getMethod("setDisplayPowerMode", IBinder::class.java, Int::class.javaPrimitiveType)
                for (token in tokens(sc)) set.invoke(null, token, value.toInt())
            }
        }
        // Al salir normal, libandroid_servers se cae en sus destructores (SIGSEGV en ~String16): se corta aquí.
        System.out.flush()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** Pantalla interna: en Android 14+ los tokens salen de DisplayControl (services.jar); antes, de SurfaceControl. */
    private fun tokens(sc: Class<*>): List<IBinder> {
        if (Build.VERSION.SDK_INT < 34) {
            return listOf(sc.getMethod("getInternalDisplayToken").invoke(null) as IBinder)
        }
        val loader = Class.forName("com.android.internal.os.ClassLoaderFactory")
            .getDeclaredMethod("createClassLoader", String::class.java, String::class.java, String::class.java,
                ClassLoader::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, String::class.java)
            .invoke(null, "/system/framework/services.jar", null, null, ClassLoader.getSystemClassLoader(), 0, true, null) as ClassLoader
        val dc = loader.loadClass("com.android.server.display.DisplayControl")
        Runtime::class.java.getDeclaredMethod("loadLibrary0", Class::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(Runtime.getRuntime(), dc, "android_servers")
        val ids = dc.getMethod("getPhysicalDisplayIds").invoke(null) as LongArray
        // Solo la primera (la interna): el monitor externo tiene su propio id y debe seguir encendido.
        return ids.take(1).map { dc.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType).invoke(null, it) as IBinder }
    }
}
