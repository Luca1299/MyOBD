package it.multipla.obd

import it.multipla.obd.core.ConnectionController
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.ConnectionState
import it.multipla.obd.core.ConnectionStore
import it.multipla.obd.core.DemoTransport
import it.multipla.obd.core.DiagnosticReader
import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.ElmSession
import it.multipla.obd.core.EngineStatus
import it.multipla.obd.core.MemoryConnectionStore
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.Reading
import it.multipla.obd.core.SavedTarget
import it.multipla.obd.core.SectionState
import it.multipla.obd.core.VehicleInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class DtcResult(val kind: DtcKind, val codes: List<String> = emptyList(), val problem: String? = null)
data class DiagnosticState(
    val demo: Boolean = false,
    val diagnosis: Diagnosis? = null,
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
    val logs: List<String> = emptyList(),
) {
    val connected get() = connection.connected
}

/**
 * Sessione posseduta dal servizio, indipendente dalla ricreazione dell’interfaccia.
 * L’orologio fornisce i tempi del tentativo; richieste e attese restano nelle coroutine.
 */
class DiagnosticsSession(
    parentScope: CoroutineScope,
    private val realStore: ConnectionStore,
    private val prerequisites: (SavedTarget) -> String?,
    private val now: () -> Instant = Instant::now,
    private val results: DiagnosisResults = DiagnosisResults(),
    private val connector: suspend (SavedTarget, (String) -> Unit) -> ElmAdapterLink,
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
    private fun log(text: String) {
        mutable.update { it.copy(logs = (it.logs + "${timestamp()}  $text").takeLast(400)) }
    }

    private fun DiagnosticState.hasActiveDiagnosis() = diagnosis != null && diagnosis.phase != DiagnosisPhase.ENDED

    init {
        chooseMode(false)
    }

    private fun chooseMode(demo: Boolean) {
        interruptActiveDiagnosis("Cambio della modalità di sessione")
        modeGeneration++
        operationGeneration.incrementAndGet()
        action?.cancel()
        action = null
        watcher?.cancel()
        if (::controller.isInitialized) controller.close()
        mutable.value = DiagnosticState(demo = demo, diagnosis = results.last(demo))
        val token = modeGeneration
        val logForMode: (String) -> Unit = { if (token == modeGeneration) log(it) }
        controller = if (demo) {
            ConnectionController(scope, MemoryConnectionStore(SavedTarget("OBDII · DEMO", "DEMO")), { null }, log = logForMode) {
                val transport = DemoTransport()
                val elm = ElmSession(transport, logForMode)
                try {
                    ElmAdapterLink(elm, transport, elm.connectAdapter())
                } catch (e: Exception) {
                    elm.close()
                    throw e
                }
            }
        } else {
            ConnectionController(scope, realStore, prerequisites, log = logForMode) { connector(it, logForMode) }
        }
        val current = controller
        log("SESSIONE ${if (demo) "SIMULATA" else "REALE"} · Fiat Multipla 2005 · metano originale")
        watcher = scope.launch {
            current.state.collect { link ->
                if (token != modeGeneration) return@collect
                if (!link.connected && (action?.isActive == true || state.value.hasActiveDiagnosis())) {
                    interruptActiveDiagnosis(link.detail ?: "Collegamento perso prima della conclusione")
                    operationGeneration.incrementAndGet()
                    action?.cancel()
                    action = null
                }
                mutable.update {
                    val freshCycle = link.phase == ConnectionPhase.CONNECTING && link.attempt == 1
                    it.copy(
                        connection = link, adapterName = link.target?.name.orEmpty(), info = link.info,
                        busy = link.busy || action?.isActive == true || it.hasActiveDiagnosis(), phase = label(link),
                        engine = if (freshCycle) null else it.engine,
                        readings = if (freshCycle) emptyList() else it.readings,
                        dtcs = if (freshCycle) emptyList() else it.dtcs,
                        sampleTime = if (freshCycle) null else it.sampleTime,
                        dtcTime = if (freshCycle) null else it.dtcTime,
                        problems = if (freshCycle) emptyList() else it.problems,
                        error = if (freshCycle) null else it.error,
                    )
                }
                log("STATO ${label(link)}${link.detail?.let { " · $it" }.orEmpty()}")
            }
        }
    }

    fun appOpened() = controller.appOpened()
    fun saveTarget(target: SavedTarget): Deferred<Unit> {
        if (state.value.demo) chooseMode(false)
        controller.saveTarget(target)
        return controller.appOpened()
    }
    fun manualConnect() = controller.manualConnect()
    fun recheckVehicle() {
        if (!state.value.busy) controller.recheckVehicle()
    }
    fun disconnect() {
        interruptActiveDiagnosis("Sessione scollegata dall’utente")
        operationGeneration.incrementAndGet()
        action?.cancel()
        action = null
        controller.stop()
    }
    fun demo() {
        chooseMode(true)
        controller.appOpened()
    }
    fun exitDemo() {
        chooseMode(false)
    }
    fun message(text: String) {
        mutable.update { it.copy(error = text) }
    }
    fun fuel(value: String) {
        if (state.value.fuel == value || state.value.busy) return
        mutable.update { it.copy(fuel = value, engine = null, readings = emptyList(), sampleTime = null, problems = emptyList()) }
        log("NOTA Alimentazione indicata manualmente: $value")
    }

    fun readSnapshot() = runAction("Lettura parametri…") { current ->
        mutable.update { it.copy(engine = null, readings = emptyList(), problems = emptyList(), sampleTime = null) }
        val problems = mutableListOf<String>()
        try {
            val engine = current.status()
            mutable.update { it.copy(engine = engine) }
        } catch (e: Exception) {
            logicalOrThrow(e)
            problems += "Stato spia: ${e.message}"
        }
        val available = state.value.info?.supported.orEmpty()
        val readings = Parameters.all.filter { it.pid in available }.mapNotNull { parameter ->
            try {
                current.read(parameter)
            } catch (e: Exception) {
                logicalOrThrow(e)
                problems += "${parameter.label}: ${e.message}"
                null
            }
        }
        mutable.update { it.copy(readings = readings, problems = problems, sampleTime = timestamp()) }
        log("CAMPIONE ${state.value.fuel} · ${readings.size} parametri; ${problems.size} richieste senza risultato")
    }

    fun readErrors() = runAction("Lettura errori OBD…") { current ->
        mutable.update { it.copy(dtcs = emptyList(), dtcTime = null) }
        val results = DtcKind.entries.map { kind ->
            try {
                val reading = current.readDtcReading(kind)
                DtcResult(kind, reading.codes, reading.problems.takeIf { it.isNotEmpty() }?.joinToString("; "))
            } catch (e: Exception) {
                logicalOrThrow(e)
                DtcResult(kind, problem = e.message ?: "Lettura non riuscita")
            }
        }
        mutable.update { it.copy(dtcs = results, dtcTime = timestamp()) }
        log("SCANSIONE " + results.joinToString("; ") { "${it.kind.label}: ${it.summary()}" })
    }

    private fun DtcResult.summary(): String = buildList {
        if (codes.isNotEmpty()) add(codes.joinToString())
        if (problem != null) {
            add("Lettura non conclusiva: $problem")
        } else if (codes.isEmpty()) {
            add("nessun codice restituito")
        }
    }.joinToString("; ")

    /** Avvio esclusivamente manuale; usa lo stesso proprietario delle letture esistenti. */
    @Synchronized
    fun startDiagnosis() {
        val snapshot = state.value
        val link = controller.activeLink as? ElmAdapterLink ?: return
        if (snapshot.busy || snapshot.info == null) return
        val id = results.nextId()
        val diagnosis = Diagnosis(id, DiagnosisContext(snapshot.demo, snapshot.adapterName, snapshot.connection.adapter.orEmpty(), snapshot.info.protocol, snapshot.fuel, now()))
        runAction("Diagnosi in corso…", diagnosis, link) { elm ->
            val reader = DiagnosticReader(elm)
            if (!shouldStopDiagnosis(id)) {
                val readiness = reader.readiness()
                publishDiagnosis(id) { it.copy(readiness = readiness) }
            }
            for (kind in DtcKind.entries) {
                if (shouldStopDiagnosis(id)) break
                val stage = when (kind) {
                    DtcKind.STORED -> DiagnosisStage.STORED_CODES
                    DtcKind.PENDING -> DiagnosisStage.PENDING_CODES
                    DtcKind.PERMANENT -> DiagnosisStage.PERMANENT_CODES
                }
                publishDiagnosis(id) { it.copy(stage = stage) }
                val result = reader.codes(kind)
                publishDiagnosis(id) { it.copy(codes = it.codes + (kind to result)) }
            }
            if (!shouldStopDiagnosis(id)) {
                publishDiagnosis(id) { it.copy(stage = DiagnosisStage.FREEZE_FRAME) }
                val frame = reader.freezeFrame({ shouldStopDiagnosis(id) }) { partial -> publishDiagnosis(id) { it.copy(freezeFrame = partial) } }
                publishDiagnosis(id) { it.copy(freezeFrame = frame) }
            }
            if (!shouldStopDiagnosis(id)) {
                publishDiagnosis(id) { it.copy(stage = DiagnosisStage.CURRENT_PARAMETERS) }
                val parameters = reader.currentParameters({ shouldStopDiagnosis(id) }) { partial -> publishDiagnosis(id) { it.copy(parameters = partial) } }
                publishDiagnosis(id) { it.copy(parameters = parameters) }
            }
            finishDiagnosis(id)
        }
    }

    /** Non cancella la richiesta pendente: l’intento e la fase cambiano atomicamente. */
    fun interruptDiagnosis() {
        mutable.update { state ->
            val current = state.diagnosis
            if (current?.phase == DiagnosisPhase.ACQUIRING) {
                state.copy(diagnosis = current.copy(phase = DiagnosisPhase.STOPPING, reason = "Diagnosi interrotta dall’utente"))
            } else {
                state
            }
        }
    }

    private fun shouldStopDiagnosis(id: Long): Boolean {
        val current = state.value.diagnosis
        return current?.id != id || current.phase != DiagnosisPhase.ACQUIRING
    }

    private fun publishDiagnosis(id: Long, transform: (Diagnosis) -> Diagnosis) {
        mutable.update { state ->
            val current = state.diagnosis
            if (current?.id == id && current.phase != DiagnosisPhase.ENDED) {
                state.copy(diagnosis = transform(current).copy(phase = current.phase))
            } else {
                state
            }
        }
    }

    private fun finishDiagnosis(id: Long, interruptionReason: String? = null) {
        val snapshot = mutable.updateAndGet { state ->
            val current = state.diagnosis
            if (current == null || current.id != id || current.phase == DiagnosisPhase.ENDED) return@updateAndGet state
            val result = if (interruptionReason != null) current.withInterruptedSection(interruptionReason) else current
            val interrupted = interruptionReason != null || current.phase == DiagnosisPhase.STOPPING
            val complete = result.sectionStates.all { it in setOf(SectionState.ACQUIRED, SectionState.EMPTY, SectionState.UNSUPPORTED) }
            val outcome = when {
                interrupted -> DiagnosisOutcome.INTERRUPTED
                complete -> DiagnosisOutcome.COMPLETED
                else -> DiagnosisOutcome.PARTIAL
            }
            val reason = interruptionReason ?: current.reason ?: if (!complete) "Una o più sezioni hanno dati mancanti o letture non conclusive" else null
            state.copy(diagnosis = result.copy(phase = DiagnosisPhase.ENDED, outcome = outcome, endedAt = now(), reason = reason, logs = state.logs.toList()))
        }
        snapshot.diagnosis?.takeIf { it.id == id && it.phase == DiagnosisPhase.ENDED }?.let(results::retain)
    }

    private fun Diagnosis.withInterruptedSection(reason: String): Diagnosis {
        fun <T> interrupted(section: DiagnosticSection<T>) = section.copy(state = SectionState.INTERRUPTED, problems = section.problems + reason)
        return when (stage) {
            DiagnosisStage.READINESS -> copy(readiness = interrupted(readiness))
            DiagnosisStage.STORED_CODES -> copy(codes = codes + (DtcKind.STORED to interrupted(codes.getValue(DtcKind.STORED))))
            DiagnosisStage.PENDING_CODES -> copy(codes = codes + (DtcKind.PENDING to interrupted(codes.getValue(DtcKind.PENDING))))
            DiagnosisStage.PERMANENT_CODES -> copy(codes = codes + (DtcKind.PERMANENT to interrupted(codes.getValue(DtcKind.PERMANENT))))
            DiagnosisStage.FREEZE_FRAME -> copy(freezeFrame = interrupted(freezeFrame))
            DiagnosisStage.CURRENT_PARAMETERS -> copy(parameters = interrupted(parameters))
        }
    }

    private fun interruptActiveDiagnosis(reason: String) {
        state.value.diagnosis?.id?.let { finishDiagnosis(it, reason) }
    }

    fun exportDiagnosis(): String? = state.value.diagnosis?.let(DiagnosisReport::render)

    private fun logicalOrThrow(e: Exception) {
        if (e is CancellationException || e is IOException) throw e
    }

    /** Pubblica occupazione e tentativo insieme, rivalutando i prerequisiti sullo stato corrente. */
    private fun admitAction(phase: String, diagnosis: Diagnosis?, currentController: ConnectionController, link: ElmAdapterLink, token: Int): Boolean {
        while (true) {
            val snapshot = state.value
            if (closed.get() || !owner.isActive || token != operationGeneration.get() || controller !== currentController ||
                currentController.activeLink !== link || snapshot.busy || snapshot.info == null
            ) {
                return false
            }
            val admitted = snapshot.copy(busy = true, phase = phase, error = null, diagnosis = diagnosis ?: snapshot.diagnosis)
            if (mutable.compareAndSet(snapshot, admitted)) return true
        }
    }

    @Synchronized
    private fun runAction(phase: String, diagnosis: Diagnosis? = null, expectedLink: ElmAdapterLink? = null, block: suspend (ElmSession) -> Unit) {
        val currentController = controller
        val link = currentController.activeLink as? ElmAdapterLink ?: return
        if (state.value.busy || state.value.info == null) return
        if (expectedLink != null && link !== expectedLink) return
        val token = operationGeneration.incrementAndGet()
        if (!admitAction(phase, diagnosis, currentController, link, token)) return
        val diagnosisId = diagnosis?.id
        val pending = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block(link.elm)
            } catch (e: TimeoutCancellationException) {
                if (token == operationGeneration.get()) diagnosisId?.let { finishDiagnosis(it, "Tempo di risposta dell’adattatore scaduto") }
                currentController.transportFailed(link, "Tempo di risposta dell’adattatore scaduto")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (token == operationGeneration.get()) diagnosisId?.let { finishDiagnosis(it, e.message ?: "Collegamento perso") }
                currentController.transportFailed(link, e.message ?: "Collegamento perso")
            } catch (e: Exception) {
                if (token == operationGeneration.get()) diagnosisId?.let { finishDiagnosis(it, e.message ?: "Lettura interrotta") }
                if (token == operationGeneration.get()) message(e.message ?: "Lettura non riuscita")
            } finally {
                if (token == operationGeneration.get()) {
                    action = null
                    mutable.update { it.copy(busy = it.connection.busy, phase = label(it.connection)) }
                }
            }
        }
        action = pending
        if (token == operationGeneration.get()) {
            pending.start()
        } else {
            if (action === pending) action = null
            pending.cancel()
        }
    }

    fun exportText(): String {
        val current = state.value
        return buildString {
            appendLine("MULTIPLA OBD · 0.3.2 · ADR-0001 / ADR-0002")
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
            current.dtcs.forEach { appendLine("${it.kind.label}: ${it.summary()}") }
            appendLine("Diagnostica OBD generica; centraline Fiat/metano non identificate.")
            appendLine("Registro: ultime 400 voci, risposte originali incluse.")
            current.logs.forEach { appendLine(it) }
            current.diagnosis?.let { diagnosis ->
                DiagnosisReport.render(diagnosis)?.let { report ->
                    appendLine()
                    appendLine("DIAGNOSI CONCLUSA · CONTESTO E DATI ACQUISITI IN PRECEDENZA")
                    appendLine(report)
                }
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        interruptActiveDiagnosis("Sessione applicativa terminata")
        modeGeneration++
        operationGeneration.incrementAndGet()
        action?.cancel()
        watcher?.cancel()
        controller.close()
        owner.cancel()
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
