package it.multipla.obd.diagnostics

import it.multipla.obd.DiagnosisResults
import it.multipla.obd.DiagnosticsSession
import it.multipla.obd.ElmAdapterLink
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.DemoTransport
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.ElmSession
import it.multipla.obd.core.ElmTransport
import it.multipla.obd.core.MemoryConnectionStore
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.SavedTarget
import it.multipla.obd.core.SectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant

/** Solo il trasporto è simulato; sessione, parser e coordinatore sono quelli applicativi. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosisFixture(now: () -> Instant = Instant::now, val results: DiagnosisResults = DiagnosisResults()) {
    val scope = TestScope()
    val store = MemoryConnectionStore(SavedTarget("OBDII", "AA:BB:CC:DD:EE:01"))
    val responses = mutableMapOf("020200" to "4202000171>", "020000" to "42000000180000>", "020C00" to "420C001000>", "020D00" to "420D0014>", "0A" to "4A00000000>")
    val commands = mutableListOf<String>()
    val losses = MutableSharedFlow<String>(extraBufferCapacity = 1)
    var hold: String? = null
    var release = CompletableDeferred<Unit>()
    var failOn: String? = null
    var ignoreCancellation = false
    val requestTimes = mutableMapOf<String, Long>()
    var opens = 0
    var closes = 0
    val session = DiagnosticsSession(scope.backgroundScope, store, { null }, now = now, results = results) { _, log ->
        opens++
        val demo = DemoTransport()
        val transport = object : ElmTransport {
            override val disconnects = losses
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                commands += command
                requestTimes[command] = scope.testScheduler.currentTime
                if (command == failOn) throw java.io.IOException("perdita sintetica")
                if (command == hold) {
                    val gate = release
                    if (ignoreCancellation) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
                    } else {
                        kotlinx.coroutines.withTimeout(timeoutMs) { gate.await() }
                    }
                }
                return responses[command] ?: demo.exchange(command, timeoutMs)
            }
            override fun close() {
                closes++
                demo.close()
            }
        }
        val elm = ElmSession(transport, log)
        ElmAdapterLink(elm, transport, elm.connectAdapter())
    }
    fun tick(ms: Long = 5000) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }
    fun connect() {
        session.appOpened()
        tick()
        assertNotNull(session.state.value.info)
    }
    fun close() {
        session.close()
        scope.runCurrent()
    }
}
