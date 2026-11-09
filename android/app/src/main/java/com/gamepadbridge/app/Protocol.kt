package com.gamepadbridge.app

/** Bitmask tombol. HARUS sama dengan docs/PROTOCOL.md di sisi PC. */
object Buttons {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LB = 1 shl 4
    const val RB = 1 shl 5
    const val L3 = 1 shl 6
    const val R3 = 1 shl 7
    const val START = 1 shl 8
    const val BACK = 1 shl 9
    const val GUIDE = 1 shl 10
    const val DPAD_UP = 1 shl 11
    const val DPAD_DOWN = 1 shl 12
    const val DPAD_LEFT = 1 shl 13
    const val DPAD_RIGHT = 1 shl 14
}

/** Sumbu: -32768..32767, Y positif = atas. Trigger: 0..255. */
data class GamepadState(
    val buttons: Int = 0,
    val lt: Int = 0,
    val rt: Int = 0,
    val lx: Int = 0,
    val ly: Int = 0,
    val rx: Int = 0,
    val ry: Int = 0,
) {
    fun withButton(mask: Int, pressed: Boolean): GamepadState =
        copy(buttons = if (pressed) buttons or mask else buttons and mask.inv())
}

object Protocol {
    const val SERVICE_UUID = "6f1c2a40-5b7e-4a53-9c1e-3b8d2f4a7c10"
    const val INPUT_LENGTH = 15
    const val FEEDBACK_LENGTH = 5
    const val INPUT_HEADER = 0xAA
    const val FEEDBACK_HEADER = 0x55

    fun crc8(data: ByteArray, length: Int): Int {
        var crc = 0
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    private fun putShort(out: ByteArray, offset: Int, value: Int) {
        val v = value.coerceIn(-32768, 32767)
        out[offset] = (v and 0xFF).toByte()
        out[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    fun encodeInput(s: GamepadState, seq: Int, out: ByteArray) {
        out[0] = INPUT_HEADER.toByte()
        out[1] = (seq and 0xFF).toByte()
        out[2] = (s.buttons and 0xFF).toByte()
        out[3] = ((s.buttons shr 8) and 0xFF).toByte()
        out[4] = s.lt.coerceIn(0, 255).toByte()
        out[5] = s.rt.coerceIn(0, 255).toByte()
        putShort(out, 6, s.lx)
        putShort(out, 8, s.ly)
        putShort(out, 10, s.rx)
        putShort(out, 12, s.ry)
        out[14] = crc8(out, 14).toByte()
    }
}

/** Parser aliran byte untuk paket rumble (PC -> HP). Sinkron ulang bila ada sampah. */
class FeedbackParser(private val onFeedback: (large: Int, small: Int) -> Unit) {
    private val buf = ByteArray(Protocol.FEEDBACK_LENGTH)
    private var count = 0

    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            val b = data[i]
            if (count == 0 && (b.toInt() and 0xFF) != Protocol.FEEDBACK_HEADER) continue
            buf[count++] = b
            if (count < Protocol.FEEDBACK_LENGTH) continue

            if (Protocol.crc8(buf, 4) == (buf[4].toInt() and 0xFF)) {
                onFeedback(buf[2].toInt() and 0xFF, buf[3].toInt() and 0xFF)
                count = 0
            } else {
                resync()
            }
        }
    }

    private fun resync() {
        var start = 1
        while (start < count && (buf[start].toInt() and 0xFF) != Protocol.FEEDBACK_HEADER) start++
        val remain = count - start
        System.arraycopy(buf, start, buf, 0, remain)
        count = remain
    }
}
