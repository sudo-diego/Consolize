package io.github.sudodiego.g9control

import kotlin.math.roundToInt

/**
 * Paquetes del protocolo GCM (Xiaoji/GameSir) que usa el Legion G9 por USB.
 * Formato general: [comando, subcomando, largo, acción, datos..., checksum]
 * acción: 0 = leer, 1 = escribir, 2 = restaurar. checksum = suma de los bytes anteriores (mod 256).
 * Reconstruido a partir de la app oficial Legion 2.0.51.
 */
object Gcm {

    const val HOME_KEY: Int = 0xC2 // HID 450 ("Home") según KeyCodeUtil

    // Modos que acepta el G9: 1 Android, 6 DS5 (VID 054C, PID 0CE6), 8 y 12 X-Input ("Microsoft X-Box 360 pad").
    // Los demás de la tabla oficial (5, 7, 9, 10, 11) los rechaza.
    const val MODE_XINPUT = 8
    const val MODE_DS5 = 6

    // Ids de zona muerta (comando 7)
    const val DZ_L_INNER = 1
    const val DZ_R_INNER = 2
    const val DZ_L_OUTER = 3
    const val DZ_R_OUTER = 4
    const val DZ_LT_FRONT = 5
    const val DZ_RT_FRONT = 6
    const val DZ_LT_BACK = 7
    const val DZ_RT_BACK = 8

    fun pkt(vararg values: Int): ByteArray {
        val b = ByteArray(values.size) { values[it].toByte() }
        b[b.size - 1] = checksum(b)
        return b
    }

    fun checksum(b: ByteArray): Byte {
        var s = 0
        for (i in 0 until b.size - 1) s += b[i]
        return s.toByte()
    }


    /** Constructor genérico (GcmProtocol.y en la app oficial). */
    fun generic(cmd: Int, sub: Int, action: Int, payload: ByteArray? = null): ByteArray {
        val p = payload ?: ByteArray(0)
        val out = ByteArray(5 + p.size)
        out[0] = cmd.toByte(); out[1] = sub.toByte(); out[2] = out.size.toByte(); out[3] = action.toByte()
        p.copyInto(out, 4)
        out[out.size - 1] = checksum(out)
        return out
    }

    // ---- Modo ----
    fun readMode() = generic(8, 2, 0)
    fun setMode(main: Int, sub: Int = 1) = generic(8, 2, 1, byteArrayOf(main.toByte(), sub.toByte()))

    // ---- Zonas muertas ----
    fun readDeadZone(id: Int) = pkt(7, id, 6, 0, 0, 0)
    fun setDeadZone(id: Int, value: Int) = pkt(7, id, 6, 1, value.coerceIn(0, 100), 0)

    // ---- Vibración (comando 37) ----
    fun readVibration() = pkt(37, 1, 13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    fun setVibration(v: Vibration) = pkt(
        37, 1, 13, 1,
        v.leftGrip, v.leftTrigger, v.leftTriggerPressed.bit(), v.leftTriggerSync.bit(),
        v.rightGrip, v.rightTrigger, v.rightTriggerPressed.bit(), v.rightTriggerSync.bit(),
        0
    )

    /**
     * Vibración inmediata (sin checksum): 04 fuerzaL durL fuerzaR durR fuerzaLT durLT fuerzaRT durRT.
     * La app oficial manda la misma fuerza en grip y gatillo, con duración 1, cada 200 ms
     * mientras se presiona el gatillo (DeviceSettingItemFragment). Fuerza 0–255.
     */
    fun rumble(left: Int, right: Int): ByteArray {
        val l = left.coerceIn(0, 255); val r = right.coerceIn(0, 255)
        val dl = if (l > 0) 1 else 0; val dr = if (r > 0) 1 else 0
        return byteArrayOf(4, l.toByte(), dl.toByte(), r.toByte(), dr.toByte(), l.toByte(), dl.toByte(), r.toByte(), dr.toByte())
    }

    // ---- Gatillo rápido (hair trigger) ----
    fun readTriggerQuick() = pkt(33, 0, 1, 1, 0)
    fun setTriggerQuick(left: Boolean, right: Boolean) =
        pkt(33, 1, if (left) 1 else 2, if (right) 1 else 2, 0)

    // ---- Funciones on/off (comando 35, GcmFuncEnableUtil en la app oficial) ----
    // 23 sub 06 acción valor chk; la respuesta trae el estado en el byte 4. Confirmado en el G9.
    const val FUNC_DPAD_DIAGONAL_LOCK = 1 // bloqueo de diagonales de la cruceta
    // El sub 3 (intercambio de sticks) no existe en el G9: acepta la escritura pero siempre relee 0.
    const val FUNC_LSTICK_SWAP_DPAD = 4   // intercambio stick izquierdo ↔ cruceta
    fun readFunc(sub: Int) = pkt(35, sub, 6, 0, 0, 0)
    fun setFunc(sub: Int, on: Boolean) = pkt(35, sub, 6, 1, if (on) 1 else 0, 0)

    // ---- Botones traseros (comando 31, sin byte de largo; ver BackKeys.kt) ----
    fun readBackKey(src: Int) = pkt(0x1F, 0, src, 0, 0, 0)
    fun setBackKey(src: Int, dst: Int) = pkt(0x1F, 1, src, dst, 0, 0)
    /** "Sin asignar": escribir destino 0 deja el botón como A; hay que restaurar (acción 2). */
    fun clearBackKey(src: Int) = pkt(0x1F, 2, src, 0, 0, 0)

    // ---- Información del control (sin checksum, como la app oficial) ----
    /** Responde "C9 …": hardware [8].[7], firmware [12].[11], protocolo GCM [18].[17]. */
    val READ_BASIC_INFO = byteArrayOf(0x01, 0x01)
    /** Responde "50 …" con el identificador único (bytes 1–16). */
    val READ_UUID = byteArrayOf(0x50)

    // ---- Restaurar valores de fábrica: acción 2, solo donde el G9 la declara (luces) ----
    fun restoreStrip(id: Int) = pkt(5, 12, 6, 2, id, 0)
    fun restoreHome() = pkt(5, 8, 6, 2, HOME_KEY, 0)

    // ---- Distribución de botones (comando 30): 10 normal, 04 = A↔B y X↔Y (como M + A). Confirmado. ----
    val READ_LAYOUT = pkt(0x1E, 0, 0)
    fun setLayout(swapped: Boolean) = pkt(0x1E, if (swapped) 0x04 else 0x10, 0)

    // ---- Luces ----
    fun readStrip(id: Int) = pkt(5, 12, 6, 0, id, 0)
    fun setStrip(s: Light) = pkt(
        5, 12, 12, 1, s.id,
        hueToByte(s.hue), s.sat, s.lum, s.effect, s.speed, s.brightness, 0
    )

    fun readHome() = pkt(5, 8, 6, 0, HOME_KEY, 0)
    fun setHome(h: Light) = pkt(
        5, 8, 11, 1, HOME_KEY,
        hueToByte(h.hue), h.sat, h.lum, h.effect, h.speed, 0
    )

    fun hueToByte(h: Int) = ((h.coerceIn(0, 360) / 360f) * 255).roundToInt()
    fun byteToHue(b: Int) = (((b and 0xFF) / 255f) * 360).roundToInt()

    private fun Boolean.bit() = if (this) 1 else 0

    fun hex(b: ByteArray?): String = b?.joinToString(" ") { "%02X".format(it) } ?: "—"

    fun parseHex(s: String): ByteArray? = try {
        val parts = s.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        ByteArray(parts.size) { parts[it].removePrefix("0x").toInt(16).toByte() }
    } catch (e: Exception) {
        null
    }
}

data class Vibration(
    val leftGrip: Int = 75, val leftTrigger: Int = 75,
    val leftTriggerPressed: Boolean = false, val leftTriggerSync: Boolean = false,
    val rightGrip: Int = 75, val rightTrigger: Int = 75,
    val rightTriggerPressed: Boolean = false, val rightTriggerSync: Boolean = false,
) {
    companion object {
        fun parse(r: ByteArray): Vibration? = if (r.size < 12) null else Vibration(
            r[4].u(), r[5].u(), r[6].toInt() == 1, r[7].toInt() == 1,
            r[8].u(), r[9].u(), r[10].toInt() == 1, r[11].toInt() == 1
        )
    }
}

/** id: 1 = aro izquierdo, 2 = aro derecho, 0 = botón Home. hue 0–360, sat/lum 0–100. */
data class Light(
    val id: Int,
    val hue: Int = 180, val sat: Int = 100, val lum: Int = 50,
    val effect: Int = 1, val speed: Int = 128, val brightness: Int = 255,
) {
    companion object {
        fun parseStrip(r: ByteArray): Light? = if (r.size < 11) null else Light(
            id = r[4].u(), hue = Gcm.byteToHue(r[5].toInt()), sat = r[6].u(), lum = r[7].u(),
            effect = r[8].u(), speed = r[9].u(), brightness = r[10].u()
        )

        fun parseHome(r: ByteArray): Light? = if (r.size < 10) null else Light(
            id = 0, hue = Gcm.byteToHue(r[5].toInt()), sat = r[6].u(), lum = r[7].u(),
            effect = r[8].u(), speed = if (r.size >= 11) r[9].u() else 128
        )
    }
}

fun Byte.u() = toInt() and 0xFF
