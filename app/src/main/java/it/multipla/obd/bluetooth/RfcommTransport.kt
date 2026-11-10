package it.multipla.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import it.multipla.obd.core.ElmTransport
import it.multipla.obd.core.PromptConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellation/timeout actively closes the socket to unblock native blocking I/O. */
@SuppressLint("MissingPermission") // Permission is checked by the Activity before a real session.
class RfcommTransport(private val device: BluetoothDevice) : ElmTransport {
    private val lock = Any()
    private var closed = false
    private var socket: BluetoothSocket? = null
    private var wire: PromptConnection? = null
    override val disconnects: Flow<String> get() = synchronized(lock) { wire?.disconnections } ?: emptyFlow()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "elm-rfcomm").apply { isDaemon = true }
    }

    override suspend fun open() = withTimeout(15_000) {
        submit {
            val created = device.createRfcommSocketToServiceRecord(SPP_UUID)
            synchronized(lock) {
                if (closed) {
                    created.close()
                    throw IOException("Connessione annullata")
                }
                socket = created
            }
            created.connect()
            val connected = PromptConnection(created.inputStream, created.outputStream) { created.close() }
            synchronized(lock) {
                if (closed) { connected.close(); throw IOException("Connessione annullata") }
                wire = connected
            }
        }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        val current = synchronized(lock) { wire } ?: throw IOException("Adattatore scollegato")
        return current.exchange(command, timeoutMs)
    }

    private suspend fun <T> submit(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        try {
            worker.execute {
                try {
                    val result = block()
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } catch (e: RejectedExecutionException) {
            if (continuation.isActive) continuation.resumeWithException(IOException("Sessione chiusa", e))
        }
    }

    override fun close() {
        val current = synchronized(lock) {
            closed = true
            val pair = wire to socket
            wire = null; socket = null
            pair
        }
        current.first?.close()
        try { current.second?.close() } catch (_: IOException) { }
        worker.shutdownNow()
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
