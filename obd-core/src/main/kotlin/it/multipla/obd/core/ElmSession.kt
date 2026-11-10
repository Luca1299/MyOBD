package it.multipla.obd.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class VehicleInfo(val adapter: String, val protocol: String, val isCan: Boolean?, val supported: Set<Int>)
data class EngineStatus(val mil: Boolean, val confirmedCount: Int)
data class Reading(val pid: Int, val label: String, val display: String)

/** Codici validi e limiti conservati separatamente nelle risposte aggregate. */
data class DtcReading(val codes: List<String>, val problems: List<String> = emptyList())

enum class DtcKind(val service: Int, val label: String) {
    STORED(0x03, "Memorizzati"),
    PENDING(0x07, "In attesa"),
    PERMANENT(0x0A, "Permanenti"),
}

data class Parameter(val pid: Int, val label: String, val byteCount: Int, val decode: (List<Int>) -> String)

object Parameters {
    private fun number(value: Double, unit: String) = String.format(Locale.ITALY, "%.1f %s", value, unit)
    val all = listOf(
        Parameter(0x0C, "Giri motore", 2) { "${(it[0] * 256 + it[1]) / 4.0} giri/min" },
        Parameter(0x05, "Temperatura refrigerante", 1) { "${it[0] - 40} °C" },
        Parameter(0x0D, "Velocità", 1) { "${it[0]} km/h" },
        Parameter(0x06, "Correzione carburazione breve · banco 1", 1) { number((it[0] - 128) * 100.0 / 128, "%") },
        Parameter(0x07, "Correzione carburazione lunga · banco 1", 1) { number((it[0] - 128) * 100.0 / 128, "%") },
        Parameter(0x0B, "Pressione assoluta collettore", 1) { "${it[0]} kPa" },
        Parameter(0x11, "Posizione farfalla", 1) { number(it[0] * 100.0 / 255, "%") },
    )
}

/** All commands are serialized; there is deliberately no arbitrary-command or Mode 04 API. */
class ElmSession(private val transport: ElmTransport, private val log: (String) -> Unit) {
    private val mutex = Mutex()
    private var info: VehicleInfo? = null
    private var adapterIdentity: String? = null

    private suspend fun request(command: String, timeout: Long = 5_000): String {
        require(isAllowed(command)) { "Comando fuori dal perimetro di lettura" }
        log("TX  $command")
        return try {
            transport.exchange(command, timeout).also { log("RX  ${it.trim()}") }
        } catch (e: Exception) {
            log("ERR ${e.message ?: e.javaClass.simpleName}")
            throw e
        }
    }

    private fun atText(raw: String, command: String): String = raw.replace(">", "")
        .lineSequence().map { it.trim() }.filter { it.isNotBlank() && it.replace(" ", "") != command }
        .joinToString(" ")

    suspend fun connectAdapter(): String = mutex.withLock {
        info = null
        adapterIdentity = null
        transport.open()
        request("ATZ", 8_000)
        val adapter = atText(request("ATI"), "ATI")
        if (adapter.isBlank() || adapter == "?") throw ElmResponseException("Identificazione adattatore fallita")
        for (command in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
            if (atText(request(command), command) != "OK") {
                throw ElmResponseException("Configurazione $command non accettata")
            }
        }
        adapterIdentity = adapter
        adapter
    }

    suspend fun initialize(): VehicleInfo {
        connectAdapter()
        return discoverVehicle()
    }

    suspend fun discoverVehicle(): VehicleInfo = mutex.withLock {
        val adapter = checkNotNull(adapterIdentity) { "Adattatore non inizializzato" }
        info = null
        // A valid 0100 response, not ATI/OK, establishes communication with the vehicle.
        val supported = mutableSetOf<Int>()
        supported.addAll(ObdParser.supported(request("0100", 25_000), 0))
        var base = 0x20
        while (base <= 0xE0 && base in supported) {
            try {
                supported.addAll(ObdParser.supported(request("01%02X".format(base)), base))
            } catch (e: NoDataException) {
                log("NOTA Mappa PID parziale: pagina %02X senza dati".format(base))
                break
            } catch (e: ObdNegativeResponse) {
                log("NOTA Mappa PID parziale: ${e.message}")
                break
            }
            base += 0x20
        }
        val protocol = atText(request("ATDP"), "ATDP")
        val number = atText(request("ATDPN"), "ATDPN").uppercase()
            .removePrefix("A").toIntOrNull(16)
        val isCan = when (number) {
            in 6..9 -> true
            in 1..5 -> false
            else -> null
        }
        VehicleInfo(adapter, protocol, isCan, supported.toSet()).also { info = it }
    }

    /** Informazioni valide solo dopo la verifica della centralina. */
    val vehicleInfo: VehicleInfo get() = checkNotNull(info) { "Sessione OBD non inizializzata" }

    suspend fun status(): EngineStatus = mutex.withLock {
        checkNotNull(info) { "Sessione OBD non inizializzata" }
        val bytes = ObdParser.pid(request("0101"), 0x01, 4)
        EngineStatus(bytes[0] and 0x80 != 0, bytes[0] and 0x7F)
    }

    suspend fun read(parameter: Parameter): Reading = mutex.withLock {
        val current = checkNotNull(info) { "Sessione OBD non inizializzata" }
        if (parameter.pid !in current.supported) throw NoDataException("PID non dichiarato disponibile")
        val bytes = ObdParser.pid(request("01%02X".format(parameter.pid)), parameter.pid, parameter.byteCount)
        Reading(parameter.pid, parameter.label, parameter.decode(bytes))
    }

    suspend fun readDtcs(kind: DtcKind): List<String> {
        val reading = readDtcReading(kind)
        if (reading.problems.isNotEmpty()) throw ElmResponseException(reading.problems.joinToString("; "))
        return reading.codes
    }

    /** Mantiene i codici validi anche quando altre risposte rendono parziale la categoria. */
    suspend fun readDtcReading(kind: DtcKind): DtcReading = mutex.withLock {
        val current = checkNotNull(info) { "Sessione OBD non inizializzata" }
        val isCan = current.isCan ?: throw ElmResponseException("Protocollo non identificato: interpretazione DTC sospesa")
        ObdParser.dtcReading(request("%02X".format(kind.service), 10_000), kind.service, isCan)
    }

    /** Stato dei monitor dall'ultima cancellazione; la spia non determina il completamento. */
    suspend fun readiness(): Readiness = mutex.withLock {
        checkNotNull(info) { "Sessione OBD non inizializzata" }
        ReadinessDecoder.decode(ObdParser.pid(request("0101"), 0x01, 4))
    }

    /** Frame zero: nessuna richiesta a frame ulteriori o associazione fra centraline. */
    suspend fun freezeCode(): String? = mutex.withLock {
        checkNotNull(info) { "Sessione OBD non inizializzata" }
        val bytes = ObdParser.freezePid(request("020200"), 0x02, 2)
        ObdParser.troubleCode(bytes[0], bytes[1])
    }

    suspend fun freezeSupport(): Set<Int> = mutex.withLock {
        checkNotNull(info) { "Sessione OBD non inizializzata" }
        val bytes = ObdParser.freezePid(request("020000"), 0x00, 4)
        ObdParser.supportedBytes(bytes, 0)
    }

    suspend fun freezeRead(parameter: Parameter): Reading = mutex.withLock {
        checkNotNull(info) { "Sessione OBD non inizializzata" }
        val bytes = ObdParser.freezePid(request("02%02X00".format(parameter.pid)), parameter.pid, parameter.byteCount)
        Reading(parameter.pid, parameter.label, parameter.decode(bytes))
    }

    fun close() {
        transport.close()
    }

    companion object {
        internal fun isAllowed(command: String): Boolean = command in setOf(
            "ATZ", "ATI", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATDP", "ATDPN", "03", "07", "0A",
        ) || command.matches(Regex("01[0-9A-F]{2}")) || command.matches(Regex("02[0-9A-F]{2}00"))
    }
}
