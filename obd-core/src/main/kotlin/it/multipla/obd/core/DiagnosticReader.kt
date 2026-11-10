package it.multipla.obd.core

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant

/** Esito della lettura; i dati parziali non implicano un esito conclusivo. */
enum class SectionState { ACQUIRED, EMPTY, UNSUPPORTED, INCONCLUSIVE, NOT_PERFORMED, INTERRUPTED }

/** Fotografia immutabile di una sezione, con istante della lettura e limiti. */
data class DiagnosticSection<T>(
    val state: SectionState = SectionState.NOT_PERFORMED,
    val value: T? = null,
    val problems: List<String> = emptyList(),
    val readAt: Instant? = null,
) {
    val conclusive get() = state in setOf(SectionState.ACQUIRED, SectionState.EMPTY, SectionState.UNSUPPORTED)
}

/** Stato del monitor interpretato, distinto dagli errori della lettura. */
enum class MonitorState { COMPLETED, INCOMPLETE, UNSUPPORTED }

/** Monitor interpretati per accensione comandata o compressione. */
enum class MonitorId {
    MISFIRE,
    FUEL_SYSTEM,
    COMPONENTS,
    CATALYST,
    HEATED_CATALYST,
    EVAPORATIVE_SYSTEM,
    SECONDARY_AIR,
    OXYGEN_SENSOR,
    OXYGEN_HEATER,
    EGR,
    NMHC_CATALYST,
    NOX_SCR,
    BOOST,
    EXHAUST_GAS_SENSOR,
    PARTICULATE_FILTER,
}

/** Ambito temporale dichiarato dal servizio di stato. */
enum class ReadinessScope { SINCE_CODES_CLEARED, }

/** Supporto e completamento di un singolo monitor. */
data class EmissionMonitor(val id: MonitorId, val state: MonitorState)

/** Stato spia e monitor della stessa risposta di stato, senza conteggi ricostruiti. */
data class Readiness(val engine: EngineStatus, val monitors: List<EmissionMonitor>, val scope: ReadinessScope = ReadinessScope.SINCE_CODES_CLEARED)

/** Valori attuali acquisiti e parametri esclusi dalla mappa di supporto valida. */
data class ParameterSample(val readings: List<Reading>, val unsupportedPids: Set<Int>)

/** Il frame zero non fornisce data del guasto né identità della centralina con ATH0. */
data class FreezeFrame(
    val code: String?,
    val supported: Set<Int> = emptySet(),
    val readings: List<Reading> = emptyList(),
    val associationVerified: Boolean = false,
    val controlUnit: String? = null,
    val faultTime: Instant? = null,
)

/** Lettori riusati dalla sessione: errori di contenuto locali, perdita del trasporto propagata. */
class DiagnosticReader(private val elm: ElmSession, private val now: () -> Instant = Instant::now) {
    private suspend fun <T> capture(service: Int, block: suspend () -> T): DiagnosticSection<T> = try {
        DiagnosticSection(SectionState.ACQUIRED, block(), readAt = now())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        val unsupported = e is ObdNegativeResponse && e.service == service && e.reason in UNSUPPORTED_REASONS
        DiagnosticSection(if (unsupported) SectionState.UNSUPPORTED else SectionState.INCONCLUSIVE, problems = listOf(e.message ?: "Lettura fallita"), readAt = now())
    }

    suspend fun readiness(): DiagnosticSection<Readiness> = capture(CURRENT_SERVICE) { elm.readiness() }

    /** Un elenco valido vuoto conclude solo la categoria richiesta. */
    suspend fun codes(kind: DtcKind): DiagnosticSection<List<TroubleCode>> {
        val result = capture(kind.service) { elm.readDtcReading(kind) }
        val reading = result.value ?: return DiagnosticSection(result.state, problems = result.problems, readAt = result.readAt)
        val state = when {
            reading.problems.isNotEmpty() -> SectionState.INCONCLUSIVE
            reading.codes.isEmpty() -> SectionState.EMPTY
            else -> SectionState.ACQUIRED
        }
        return DiagnosticSection(state, reading.codes.map(DtcCatalog::describe), reading.problems, result.readAt)
    }

    /** Interroga solo il catalogo attuale dichiarato; verifica l'interruzione fra richieste. */
    suspend fun currentParameters(shouldStop: () -> Boolean = { false }, onProgress: (DiagnosticSection<ParameterSample>) -> Unit = {}): DiagnosticSection<ParameterSample> {
        val available = elm.vehicleInfo.supported
        val readings = mutableListOf<Reading>()
        val problems = mutableListOf<String>()
        var interrupted = false
        for (parameter in Parameters.all.filter { it.pid in available }) {
            if (shouldStop()) {
                interrupted = true
                break
            }
            val result = capture(CURRENT_SERVICE) { elm.read(parameter) }
            result.value?.let(readings::add)
            problems += result.problems.map { "${parameter.label}: $it" }
            onProgress(DiagnosticSection(SectionState.INCONCLUSIVE, ParameterSample(readings.toList(), Parameters.all.map { it.pid }.filterNot { it in available }.toSet()), problems.toList(), now()))
        }
        return DiagnosticSection(
            when {
                interrupted -> SectionState.INTERRUPTED
                problems.isNotEmpty() -> SectionState.INCONCLUSIVE
                else -> SectionState.ACQUIRED
            },
            ParameterSample(readings.toList(), Parameters.all.map { it.pid }.filterNot { it in available }.toSet()),
            problems.toList(),
            now(),
        )
    }

    /** Supporto e valori sono letti nel contesto 02/frame 00, senza usare dati attuali. */
    suspend fun freezeFrame(shouldStop: () -> Boolean = { false }, onProgress: (DiagnosticSection<FreezeFrame>) -> Unit = {}): DiagnosticSection<FreezeFrame> {
        val codeResult = capture(FREEZE_SERVICE) { elm.freezeCode() }
        if (codeResult.state != SectionState.ACQUIRED) return DiagnosticSection(codeResult.state, problems = codeResult.problems, readAt = codeResult.readAt)
        val code = codeResult.value
        if (code == null) return DiagnosticSection(SectionState.EMPTY, readAt = now())
        var frame = FreezeFrame(code)
        onProgress(DiagnosticSection(SectionState.INCONCLUSIVE, frame, readAt = now()))
        if (shouldStop()) return DiagnosticSection(SectionState.INTERRUPTED, frame, readAt = now())
        val support = capture(FREEZE_SERVICE) { elm.freezeSupport() }
        if (support.value == null) return DiagnosticSection(SectionState.INCONCLUSIVE, frame, support.problems, now())
        frame = frame.copy(supported = support.value)
        onProgress(DiagnosticSection(SectionState.INCONCLUSIVE, frame, readAt = now()))
        val readings = mutableListOf<Reading>()
        val problems = mutableListOf<String>()
        var interrupted = false
        for (parameter in Parameters.all.filter { it.pid in frame.supported }) {
            if (shouldStop()) {
                interrupted = true
                break
            }
            val result = capture(FREEZE_SERVICE) { elm.freezeRead(parameter) }
            result.value?.let(readings::add)
            problems += result.problems.map { "${parameter.label}: $it" }
            onProgress(DiagnosticSection(SectionState.INCONCLUSIVE, frame.copy(readings = readings.toList()), problems.toList(), now()))
        }
        return DiagnosticSection(
            when {
                interrupted -> SectionState.INTERRUPTED
                problems.isNotEmpty() -> SectionState.INCONCLUSIVE
                else -> SectionState.ACQUIRED
            },
            frame.copy(readings = readings.toList()),
            problems.toList(),
            now(),
        )
    }

    companion object {
        private const val CURRENT_SERVICE = 0x01
        private const val FREEZE_SERVICE = 0x02
        private val UNSUPPORTED_REASONS = setOf(0x11, 0x12)
    }
}
