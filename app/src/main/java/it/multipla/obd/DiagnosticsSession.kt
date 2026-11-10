package it.multipla.obd

import it.multipla.obd.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class DtcResult(val kind: DtcKind, val codes: List<String> = emptyList(), val problem: String? = null)
data class DiagnosticState(
    val demo: Boolean = false,
    val busy: Boolean = false,
    val phase: String = "Pronto per una nuova sessione",
    val adapterName: String = "",
    val connection: ConnectionState = ConnectionState(),
    val info: VehicleInfo? = null,
    val engine: EngineStatus? = null,
    val readings: List<Reading> = emptyList(),
    val dtcs: List<DtcResult> = emptyList(),
    val problems: List<String> = emptyList(),
    val error: String? = null,
    val fuel: String = "Non annotata",
    val sampleTime: String? = null,
    val dtcTime: String? = null,
    val logs: List<String> = emptyList()
) { val connected get() = connection.connected }

/** Service-owned session. Activity recreation/unbinding does not own or close the link. */
class DiagnosticsSession(
    parentScope: CoroutineScope,
    private val realStore: ConnectionStore,
    private val prerequisites: (SavedTarget) -> String?,
    private val connector: suspend (SavedTarget, (String) -> Unit) -> ElmAdapterLink
) {
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private val closed = AtomicBoolean()
    private val mutable = MutableStateFlow(DiagnosticState())
    val state = mutable.asStateFlow()
    @Volatile private lateinit var controller: ConnectionController
    val connectionState get() = controller.state.value
    private var watcher: Job? = null
    @Volatile private var action: Job? = null
    @Volatile private var modeGeneration = 0
    private val operationGeneration = AtomicInteger()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private fun timestamp() = LocalDateTime.now().format(clock)
    private fun log(text: String) { mutable.update { it.copy(logs = (it.logs + "${timestamp()}  $text").takeLast(400)) } }

    init { chooseMode(false) }

    private fun chooseMode(demo: Boolean) {
        modeGeneration++; operationGeneration.incrementAndGet()
        action?.cancel(); action = null; watcher?.cancel()
        if (::controller.isInitialized) controller.close()
        mutable.value = DiagnosticState(demo = demo)
        val token = modeGeneration
        val logForMode: (String) -> Unit = { if (token == modeGeneration) log(it) }
        controller = if (demo) {
            ConnectionController(scope, MemoryConnectionStore(SavedTarget("OBDII · DEMO", "DEMO")), { null }, log = logForMode) {
                val transport = DemoTransport()
                val elm = ElmSession(transport, logForMode)
                try { ElmAdapterLink(elm, transport, elm.connectAdapter()) }
                catch (e: Exception) { elm.close(); throw e }
            }
        } else ConnectionController(scope, realStore, prerequisites, log = logForMode) { connector(it, logForMode) }
        val current = controller
        log("SESSIONE ${if (demo) "SIMULATA" else "REALE"} · Fiat Multipla 2005 · metano originale")
        watcher = scope.launch {
            current.state.collect { link ->
                if (token != modeGeneration) return@collect
                if (!link.connected && action?.isActive == true) { operationGeneration.incrementAndGet(); action?.cancel(); action = null }
                mutable.update {
                    val freshCycle = link.phase == ConnectionPhase.CONNECTING && link.attempt == 1
                    it.copy(connection = link, adapterName = link.target?.name.orEmpty(), info = link.info,
                        busy = link.busy || action?.isActive == true, phase = label(link),
                        engine = if (freshCycle) null else it.engine,
                        readings = if (freshCycle) emptyList() else it.readings,
                        dtcs = if (freshCycle) emptyList() else it.dtcs,
                        sampleTime = if (freshCycle) null else it.sampleTime,
                        dtcTime = if (freshCycle) null else it.dtcTime,
                        problems = if (freshCycle) emptyList() else it.problems,
                        error = if (freshCycle) null else it.error)
                }
                log("STATO ${label(link)}${link.detail?.let { " · $it" }.orEmpty()}")
            }
        }
    }

    fun appOpened() = controller.appOpened()
    fun saveTarget(target: SavedTarget): Deferred<Unit> {
        if (state.value.demo) chooseMode(false)
        controller.saveTarget(target); return controller.appOpened()
    }
    fun manualConnect() = controller.manualConnect()
    fun recheckVehicle() = controller.recheckVehicle()
    fun disconnect() { operationGeneration.incrementAndGet(); action?.cancel(); action = null; controller.stop() }
    fun demo() { chooseMode(true); controller.appOpened() }
    fun exitDemo() { chooseMode(false) }
    fun message(text: String) { mutable.update { it.copy(error = text) } }
    fun fuel(value: String) {
        if (state.value.fuel == value || state.value.busy) return
        mutable.update { it.copy(fuel = value, engine = null, readings = emptyList(), sampleTime = null, problems = emptyList()) }
        log("NOTA Alimentazione indicata manualmente: $value")
    }

    fun readSnapshot() = runAction("Lettura parametri…") { current ->
        mutable.update { it.copy(engine = null, readings = emptyList(), problems = emptyList(), sampleTime = null) }
        val problems = mutableListOf<String>()
        try { val engine = current.status(); mutable.update { it.copy(engine = engine) }
        } catch (e: Exception) { logicalOrThrow(e); problems += "Stato spia: ${e.message}" }
        val available = state.value.info?.supported.orEmpty()
        val readings = Parameters.all.filter { it.pid in available }.mapNotNull { parameter ->
            try { current.read(parameter) } catch (e: Exception) {
                logicalOrThrow(e); problems += "${parameter.label}: ${e.message}"; null
            }
        }
        mutable.update { it.copy(readings = readings, problems = problems, sampleTime = timestamp()) }
        log("CAMPIONE ${state.value.fuel} · ${readings.size} parametri; ${problems.size} richieste senza risultato")
    }

    fun readErrors() = runAction("Lettura errori OBD…") { current ->
        mutable.update { it.copy(dtcs = emptyList(), dtcTime = null) }
        val results = DtcKind.entries.map { kind ->
            try { DtcResult(kind, current.readDtcs(kind)) } catch (e: Exception) {
                logicalOrThrow(e); DtcResult(kind, problem = e.message ?: "Lettura non riuscita")
            }
        }
        mutable.update { it.copy(dtcs = results, dtcTime = timestamp()) }
        log("SCANSIONE " + results.joinToString("; ") { "${it.kind.label}: ${it.problem ?: it.codes.joinToString().ifBlank { "nessun codice restituito" }}" })
    }

    private fun logicalOrThrow(e: Exception) { if (e is CancellationException || e is IOException) throw e }
    private fun runAction(phase: String, block: suspend (ElmSession) -> Unit) {
        val currentController = controller
        val link = currentController.activeLink as? ElmAdapterLink ?: return
        if (state.value.busy || state.value.info == null) return
        val token = operationGeneration.incrementAndGet()
        mutable.update { it.copy(busy = true, phase = phase, error = null) }
        action = scope.launch {
            try { block(link.elm)
            } catch (e: TimeoutCancellationException) {
                currentController.transportFailed(link, "Tempo di risposta dell’adattatore scaduto")
            } catch (e: CancellationException) { throw e
            } catch (e: IOException) { currentController.transportFailed(link, e.message ?: "Collegamento perso")
            } catch (e: Exception) { if (token == operationGeneration.get()) message(e.message ?: "Lettura non riuscita")
            } finally {
                if (token == operationGeneration.get()) {
                    action = null
                    mutable.update { it.copy(busy = it.connection.busy, phase = label(it.connection)) }
                }
            }
        }
    }

    fun exportText(): String {
        val current = state.value
        return buildString {
            appendLine("MULTIPLA OBD · 0.2.1 · ADR-0001 / ADR-0002")
            appendLine("Tipo sessione: ${if (current.demo) "SIMULATA — DATI INVENTATI" else "REALE"}")
            appendLine("Veicolo dichiarato: Fiat Multipla 2005, benzina/metano originale")
            appendLine("Adattatore: ${current.adapterName}")
            appendLine("Identificazione: ${current.connection.adapter ?: "non disponibile"}")
            appendLine("Protocollo: ${current.info?.protocol ?: "non disponibile"}")
            appendLine("Stato: ${current.phase}; ${current.connection.detail.orEmpty()}")
            appendLine("Alimentazione annotata manualmente: ${current.fuel}")
            appendLine("Data esportazione: ${LocalDateTime.now()}")
            appendLine("Orari riferiti al telefono. Campioni letti in sequenza, non simultaneamente.")
            appendLine("Campione completato alle: ${current.sampleTime ?: "non eseguito"}")
            current.engine?.let { appendLine("MIL: ${it.mil}; DTC confermati dichiarati: ${it.confirmedCount}") }
            current.readings.forEach { appendLine("${it.label}: ${it.display}") }
            current.problems.forEach { appendLine("LETTURA INCOMPLETA: $it") }
            appendLine("Scansione DTC completata alle: ${current.dtcTime ?: "non eseguita"}")
            current.dtcs.forEach { appendLine("${it.kind.label}: ${it.problem ?: it.codes.joinToString().ifBlank { "nessun codice restituito" }}") }
            appendLine("Diagnostica OBD generica; centraline Fiat/metano non identificate.")
            appendLine("Registro: ultime 400 voci, risposte originali incluse.")
            current.logs.forEach { appendLine(it) }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        modeGeneration++; operationGeneration.incrementAndGet()
        action?.cancel(); watcher?.cancel(); controller.close(); owner.cancel()
    }

    companion object {
        fun label(state: ConnectionState): String = when (state.phase) {
            ConnectionPhase.IDLE -> "Pronto per una nuova sessione"
            ConnectionPhase.NEEDS_SETUP -> "Configurazione necessaria"
            ConnectionPhase.CONNECTING -> "Collegamento · tentativo ${state.attempt}/${state.maxAttempts}"
            ConnectionPhase.RETRY_WAIT -> "Riprovo tra 3 secondi · prossimo tentativo ${state.attempt + 1}/3"
            ConnectionPhase.PROBING_VEHICLE -> "Adattatore collegato · verifica centralina…"
            ConnectionPhase.ADAPTER_READY -> "Adattatore collegato · verifica centralina non completata"
            ConnectionPhase.VEHICLE_READY -> "Centralina OBD raggiunta"
            ConnectionPhase.MANUAL_REQUIRED -> "Connessione manuale richiesta"
            ConnectionPhase.CONNECTION_FAILED -> "Collegamento manuale non riuscito"
            ConnectionPhase.STOPPED -> "Sessione interrotta"
        }
    }
}
