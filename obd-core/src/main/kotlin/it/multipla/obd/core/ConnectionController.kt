package it.multipla.obd.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException

data class SavedTarget(val name: String, val address: String)

interface ConnectionStore {
    fun target(): SavedTarget?
    fun saveTarget(target: SavedTarget)
    fun manualRequired(): Boolean
    fun setManualRequired(required: Boolean)
}

class MemoryConnectionStore(private var saved: SavedTarget?, private var blocked: Boolean = false) : ConnectionStore {
    override fun target() = saved
    override fun saveTarget(target: SavedTarget) { saved = target }
    override fun manualRequired() = blocked
    override fun setManualRequired(required: Boolean) { blocked = required }
}

interface AdapterLink {
    val identity: String
    val disconnections: Flow<String>
    suspend fun vehicle(): VehicleInfo
    fun close()
}

enum class ConnectionPhase { IDLE, NEEDS_SETUP, CONNECTING, RETRY_WAIT, PROBING_VEHICLE, ADAPTER_READY, VEHICLE_READY, MANUAL_REQUIRED, CONNECTION_FAILED, STOPPED }
data class ConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val target: SavedTarget? = null,
    val attempt: Int = 0,
    val maxAttempts: Int = 3,
    val adapter: String? = null,
    val info: VehicleInfo? = null,
    val detail: String? = null
) {
    val connected get() = phase in setOf(ConnectionPhase.PROBING_VEHICLE, ConnectionPhase.ADAPTER_READY, ConnectionPhase.VEHICLE_READY)
    val busy get() = phase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.RETRY_WAIT, ConnectionPhase.PROBING_VEHICLE)
}

/** One event owner serializes all decisions. I/O and timers never mutate policy state. */
class ConnectionController(
    parentScope: CoroutineScope,
    private val store: ConnectionStore,
    private val prerequisites: (SavedTarget) -> String?,
    private val log: (String) -> Unit = {},
    private val connector: suspend (SavedTarget) -> AdapterLink
) {
    private sealed interface Event {
        data object Open : Event
        data object Manual : Event
        data object Stop : Event
        data object ProbeAgain : Event
        data class Barrier(val done: CompletableDeferred<Unit>) : Event
        data class Save(val target: SavedTarget) : Event
        data class Ready(val generation: Long, val link: AdapterLink) : Event
        data class Failed(val generation: Long, val message: String) : Event
        data class Retry(val generation: Long) : Event
        data class Lost(val generation: Long, val link: AdapterLink, val message: String) : Event
        data class Probed(val generation: Long, val link: AdapterLink, val info: VehicleInfo?, val issue: String?) : Event
    }
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = mutable.asStateFlow()
    private val lifecycleLock = Any()
    @Volatile private var closed = false
    @Volatile var activeLink: AdapterLink? = null
        private set
    @Volatile private var generation = 0L
    private var attempt = 0
    private var automatic = true
    private var stopped = false
    private var connectJob: Job? = null
    private var retryJob: Job? = null
    private var probeJob: Job? = null
    private var lossJob: Job? = null

    init {
        scope.launch {
            for (event in events) {
                if (closed) { if (event is Event.Ready) event.link.close(); continue }
                try { handle(event) } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    halt()
                    stopped = true
                    mutable.value = mutable.value.copy(phase = ConnectionPhase.STOPPED, info = null, adapter = null,
                        detail = "Operazione interrotta: ${e.message}")
                }
            }
        }
    }

    private fun command(event: Event): Deferred<Unit> {
        val done = CompletableDeferred<Unit>()
        if (closed || events.trySend(event).isFailure || events.trySend(Event.Barrier(done)).isFailure) done.cancel()
        return done
    }
    fun appOpened() = command(Event.Open)
    fun saveTarget(target: SavedTarget) = command(Event.Save(target))
    fun manualConnect() = command(Event.Manual)
    fun stop() = command(Event.Stop)
    fun recheckVehicle() = command(Event.ProbeAgain)
    fun transportFailed(link: AdapterLink, message: String) {
        events.trySend(Event.Lost(generation, link, message))
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Barrier -> event.done.complete(Unit)
            Event.Open -> {
                if (stopped || activeLink != null || mutable.value.busy) return
                if (store.manualRequired()) {
                    log("AUTOCONNESSIONE SOSPESA · Blocco salvato; collegamento manuale richiesto")
                    manualState("Tre tentativi esauriti. Un collegamento manuale riuscito riabilita l’autoconnessione.")
                } else begin(true)
            }
            Event.Manual -> {
                if (activeLink != null || mutable.value.busy) return
                stopped = false
                begin(false)
            }
            is Event.Save -> {
                if (activeLink != null || mutable.value.busy) return
                store.saveTarget(event.target)
                mutable.value = mutable.value.copy(target = event.target)
            }
            Event.Stop -> {
                stopped = true; halt()
                mutable.value = mutable.value.copy(phase = ConnectionPhase.STOPPED, adapter = null, info = null, detail = "Sessione interrotta dall’utente")
            }
            is Event.Failed -> {
                if (event.generation != generation) return
                connectJob = null
                log("COLLEGAMENTO FALLITO · ${event.message}")
                if (automatic && attempt < 3) {
                    mutable.value = mutable.value.copy(phase = ConnectionPhase.RETRY_WAIT, detail = event.message)
                    val token = generation
                    retryJob = scope.launch { delay(3_000); events.send(Event.Retry(token)) }
                } else if (automatic) {
                    store.setManualRequired(true)
                    log("AUTOCONNESSIONE SOSPESA · Tre tentativi automatici falliti")
                    manualState("Tre tentativi automatici falliti: ${event.message}. Un collegamento manuale riuscito riabilita l’autoconnessione.")
                } else if (store.manualRequired()) {
                    manualState("Collegamento manuale non riuscito: ${event.message}. Autoconnessione ancora sospesa.")
                } else {
                    val detail = "Collegamento manuale non riuscito: ${event.message}. Autoconnessione abilitata alle prossime aperture."
                    mutable.value = ConnectionState(ConnectionPhase.CONNECTION_FAILED, store.target(), attempt,
                        maxAttempts = 1, detail = detail)
                    log(detail)
                }
            }
            is Event.Retry -> {
                if (event.generation == generation && mutable.value.phase == ConnectionPhase.RETRY_WAIT) {
                    retryJob = null
                    val target = mutable.value.target ?: return
                    val issue = prerequisites(target)
                    if (issue != null) needsSetup(target, issue) else launchAttempt(target)
                }
            }
            is Event.Ready -> {
                if (event.generation != generation) { event.link.close(); return }
                connectJob = null; retryJob?.cancel(); retryJob = null
                val installed = synchronized(lifecycleLock) {
                    if (closed) false else { activeLink = event.link; true }
                }
                if (!installed) { event.link.close(); return }
                store.setManualRequired(false)
                log("ADATTATORE COLLEGATO · ${event.link.identity}")
                if (!automatic) log("AUTOCONNESSIONE RIABILITATA · Collegamento manuale riuscito")
                mutable.value = mutable.value.copy(phase = ConnectionPhase.ADAPTER_READY, adapter = event.link.identity, detail = null)
                val token = generation
                lossJob = scope.launch { event.link.disconnections.firstOrNull()?.let { events.send(Event.Lost(token, event.link, it)) } }
                probe(event.link)
            }
            is Event.Probed -> {
                if (event.generation != generation || activeLink !== event.link) return
                probeJob = null
                log(if (event.info != null) "CENTRALINA RAGGIUNTA · ${event.info.protocol}" else "CENTRALINA · ${event.issue}")
                mutable.value = mutable.value.copy(
                    phase = if (event.info != null) ConnectionPhase.VEHICLE_READY else ConnectionPhase.ADAPTER_READY,
                    info = event.info, detail = event.issue
                )
            }
            is Event.Lost -> {
                if (event.generation != generation || activeLink !== event.link) return
                log("COLLEGAMENTO PERSO · ${event.message}")
                halt()
                if (!stopped) begin(true)
            }
            Event.ProbeAgain -> {
                val link = activeLink ?: return
                if (!mutable.value.busy) probe(link)
            }
        }
    }

    private fun begin(isAutomatic: Boolean) {
        val target = store.target()
        if (target == null) { needsSetup(null, "Salva il dispositivo OBDII da utilizzare"); return }
        val issue = prerequisites(target)
        if (issue != null) { needsSetup(target, issue); return }
        generation++; attempt = 0; automatic = isAutomatic
        mutable.value = ConnectionState(target = target, maxAttempts = if (automatic) 3 else 1)
        launchAttempt(target)
    }

    private fun launchAttempt(target: SavedTarget) {
        attempt++
        log("CONNESSIONE ${if (automatic) "AUTOMATICA" else "MANUALE"} · tentativo $attempt/${if (automatic) 3 else 1}")
        mutable.value = mutable.value.copy(phase = ConnectionPhase.CONNECTING, attempt = attempt, detail = null, adapter = null, info = null)
        val token = generation
        connectJob = scope.launch {
            var unpublished: AdapterLink? = null
            try {
                unpublished = connector(target)
                ensureActive()
                if (events.trySend(Event.Ready(token, unpublished)).isSuccess) unpublished = null
            } catch (e: TimeoutCancellationException) {
                events.trySend(Event.Failed(token, "Tempo di collegamento scaduto"))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                events.trySend(Event.Failed(token, e.message ?: "Collegamento fallito"))
            } finally { unpublished?.close() }
        }
    }

    private fun probe(link: AdapterLink) {
        val token = generation
        log("CENTRALINA · Verifica in corso sull’adattatore collegato")
        mutable.value = mutable.value.copy(phase = ConnectionPhase.PROBING_VEHICLE, detail = null, info = null)
        probeJob = scope.launch {
            try { events.send(Event.Probed(token, link, link.vehicle(), null))
            } catch (e: TimeoutCancellationException) {
                events.trySend(Event.Lost(token, link, "Adattatore senza risposta"))
            } catch (e: CancellationException) { throw e
            } catch (e: IOException) { events.trySend(Event.Lost(token, link, e.message ?: "Collegamento perso"))
            } catch (e: NoDataException) {
                events.trySend(Event.Probed(token, link, null, "Centralina non raggiungibile: ${e.message}"))
            } catch (e: Exception) {
                events.trySend(Event.Probed(token, link, null, "Lettura centralina non riuscita: ${e.message}"))
            }
        }
    }

    private fun manualState(message: String) {
        mutable.value = ConnectionState(ConnectionPhase.MANUAL_REQUIRED, store.target(), attempt,
            maxAttempts = if (automatic) 3 else 1, detail = message)
    }
    private fun needsSetup(target: SavedTarget?, message: String) {
        mutable.value = ConnectionState(ConnectionPhase.NEEDS_SETUP, target = target, detail = message)
    }
    private fun halt() {
        generation++
        connectJob?.cancel(); connectJob = null
        retryJob?.cancel(); retryJob = null
        probeJob?.cancel(); probeJob = null
        lossJob?.cancel(); lossJob = null
        synchronized(lifecycleLock) { activeLink.also { activeLink = null } }?.close()
    }
    fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            activeLink?.close(); activeLink = null
        }
        owner.cancel()
        events.close()
        while (true) {
            val pending = events.tryReceive().getOrNull() ?: break
            if (pending is Event.Ready) pending.link.close()
            if (pending is Event.Barrier) pending.done.cancel()
        }
    }
}
