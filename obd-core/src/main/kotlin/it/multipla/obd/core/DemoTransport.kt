package it.multipla.obd.core

import kotlinx.coroutines.delay
import java.io.IOException

/** Synthetic data only. No Android/Bluetooth dependency and no vehicle access. */
class DemoTransport : ElmTransport {
    private var open = false
    override suspend fun open() {
        open = true
        delay(150)
    }
    override suspend fun exchange(command: String, timeoutMs: Long): String {
        if (!open) throw IOException("Simulatore chiuso")
        delay(80)
        return when (command) {
            "ATZ", "ATI" -> "ELM327 DEMO\r>"

            "ATDP" -> "ISO 9141-2 · SIMULATO\r>"

            "ATDPN" -> "A3\r>"

            "0100" -> {
                val supported = setOf(1) + Parameters.all.map { it.pid }
                val bytes = (0..3).map { block ->
                    (0..7).sumOf { bit -> if (block * 8 + bit + 1 in supported) 1 shl (7 - bit) else 0 }
                }
                "41 00 " + bytes.joinToString(" ") { "%02X".format(it) } + "\r>"
            }

            "0101" -> "41 01 81 07 65 04\r>"

            "010C" -> "41 0C 0C D0\r>"

            "0105" -> "41 05 80\r>"

            "010D" -> "41 0D 00\r>"

            "0106" -> "41 06 82\r>"

            "0107" -> "41 07 88\r>"

            "010B" -> "41 0B 22\r>"

            "0111" -> "41 11 08\r>"

            "03" -> "43 01 71 00 00 00 00\r>"

            "07" -> "47 00 00 00 00 00 00\r>"

            "0A" -> "NO DATA\r>"

            else -> if (command.startsWith("AT")) "OK\r>" else "NO DATA\r>"
        }
    }
    override fun close() {
        open = false
    }
}
