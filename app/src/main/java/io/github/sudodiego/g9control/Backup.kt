package io.github.sudodiego.g9control

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Respaldo completo: todos los ajustes de la app (sus archivos de preferencias, perfiles del G9 incluidos) en un JSON,
 * para no perder nada al reinstalar o cambiar de tablet. Las imágenes y la música elegidas son enlaces a archivos: tras
 * reinstalar hay que volver a elegirlas (Android no conserva el permiso), lo demás vuelve tal cual.
 */
object Backup {
    private const val KIND = "tbf-backup"
    /** Los archivos de preferencias de la app (los mismos nombres que usan Prefs, AutoRules, Sessions, etc.). */
    private val FILES = listOf("g9", "g9_perf", "sessions", "power_log", "recent_apps", "memory_history")

    fun export(ctx: Context): String {
        val prefs = JSONObject()
        for (f in FILES) {
            val o = JSONObject()
            ctx.getSharedPreferences(f, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                // Cada valor con su tipo, para devolverlo igual (un Int no puede volver como Long).
                val e = JSONObject()
                when (v) {
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is String -> e.put("t", "s").put("v", v)
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> return@forEach
                }
                o.put(k, e)
            }
            prefs.put(f, o)
        }
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        return JSONObject().put("kind", KIND).put("format", 1).put("app", version).put("prefs", prefs).toString(1)
    }

    /** Reemplaza los ajustes por los del archivo. Devuelve cuántos valores se cargaron; falla si no es un respaldo. */
    fun import(ctx: Context, text: String): Int {
        val root = JSONObject(text)
        require(root.optString("kind") == KIND) { "no es un respaldo" }
        val prefs = root.getJSONObject("prefs")
        var n = 0
        for (f in FILES) {
            val o = prefs.optJSONObject(f) ?: continue
            val ed = ctx.getSharedPreferences(f, Context.MODE_PRIVATE).edit().clear()
            o.keys().forEach { k ->
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, e.getString("v"))
                    "ss" -> { val a = e.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                }
                n++
            }
            ed.commit()
        }
        return n
    }
}
