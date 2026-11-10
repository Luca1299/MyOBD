package it.multipla.obd.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Passive reader detects EOF even while the app is idle; it never polls the ECU. */
class PromptConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val closeWire: () -> Unit
) {
    private val closed = AtomicBoolean(false)
    private val pending = AtomicReference<CompletableDeferred<String>?>(null)
    private val mutex = Mutex()
    private val losses = MutableSharedFlow<String>(replay = 1)
    val disconnections = losses.asSharedFlow()
    private val workers = Executors.newFixedThreadPool(2) { task -> Thread(task, "elm-stream").apply { isDaemon = true } }

    init {
        workers.execute {
            try {
                while (!closed.get()) {
                    val response = PromptReader.read(input)
                    pending.getAndSet(null)?.complete(response)
                }
            } catch (e: Exception) {
                if (!closed.get()) terminate(e.message ?: "Collegamento perso", true)
            }
        }
    }

    suspend fun exchange(command: String, timeoutMs: Long): String = mutex.withLock {
        if (closed.get()) throw IOException("Sessione chiusa")
        val answer = CompletableDeferred<String>()
        pending.set(answer)
        try {
            workers.execute {
                try {
                    output.write((command + "\r").toByteArray(Charsets.US_ASCII)); output.flush()
                } catch (e: Exception) { terminate(e.message ?: "Scrittura Bluetooth fallita", true) }
            }
            withTimeout(timeoutMs) { answer.await() }
        } catch (e: CancellationException) {
            terminate("Richiesta interrotta o scaduta", true)
            throw e
        } catch (e: Exception) {
            terminate(e.message ?: "Collegamento perso", true)
            throw e
        } finally { pending.compareAndSet(answer, null) }
    }

    private fun terminate(message: String, reportLoss: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        pending.getAndSet(null)?.completeExceptionally(IOException(message))
        try { closeWire() } catch (_: Exception) { }
        if (reportLoss) losses.tryEmit(message)
        workers.shutdownNow()
    }
    fun close() = terminate("Sessione chiusa", false)
}
