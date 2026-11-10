package it.multipla.obd

import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.DemoTransport
import it.multipla.obd.core.ElmSession
import it.multipla.obd.core.ElmTransport
import it.multipla.obd.core.MemoryConnectionStore
import it.multipla.obd.core.SavedTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionDiagnosticsTest {
    private val target = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")

    @Test fun `T09 export records attempts suspension and manual rearming`() = runTest {
        var calls = 0
        val store = MemoryConnectionStore(target)
        val session = DiagnosticsSession(backgroundScope, store, { null }) { _, log ->
            if (++calls <= 3) throw IOException("fuori portata")
            val transport = DemoTransport()
            val elm = ElmSession(transport, log)
            ElmAdapterLink(elm, transport, elm.connectAdapter())
        }
        try {
            session.appOpened()
            runCurrent()
            advanceTimeBy(6000)
            runCurrent()
            assertTrue(store.manualRequired())
            session.manualConnect()
            runCurrent()
            advanceTimeBy(3000)
            runCurrent()
            assertFalse(store.manualRequired())
            assertEquals(4, calls)
            val report = session.exportText()
            for (attempt in 1..3) assertTrue(report.contains("CONNESSIONE AUTOMATICA · tentativo $attempt/3"))
            assertTrue(report.contains("AUTOCONNESSIONE SOSPESA · Tre tentativi automatici falliti"))
            assertTrue(report.contains("CONNESSIONE MANUALE · tentativo 1/1"))
            assertTrue(report.contains("AUTOCONNESSIONE RIABILITATA · Collegamento manuale riuscito"))
            assertTrue(report.indexOf("AUTOCONNESSIONE SOSPESA") < report.indexOf("AUTOCONNESSIONE RIABILITATA"))
        } finally {
            session.close()
        }
    }

    @Test fun `T09 failed manual attempt with no lock reports automation still enabled`() = runTest {
        val store = MemoryConnectionStore(target)
        var calls = 0
        val session = DiagnosticsSession(backgroundScope, store, { null }) { _, _ ->
            calls++
            throw IOException("fuori portata")
        }
        try {
            session.manualConnect()
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()
            assertFalse(store.manualRequired())
            assertEquals(1, calls)
            assertFalse(session.exportText().contains("AUTOCONNESSIONE SOSPESA"))
            assertTrue(session.exportText().contains("Autoconnessione abilitata"))
            assertNotEquals("Connessione manuale richiesta", session.state.value.phase)
        } finally {
            session.close()
        }
    }

    @Test fun `T09 invalid response is never labelled ECU unreachable`() = runTest {
        val demo = DemoTransport()
        val transport = object : ElmTransport {
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long) = if (command == "0100") "41ZZ\r>" else demo.exchange(command, timeoutMs)
            override fun close() = demo.close()
        }
        val session = DiagnosticsSession(backgroundScope, MemoryConnectionStore(target), { null }) { _, log ->
            val elm = ElmSession(transport, log)
            ElmAdapterLink(elm, transport, elm.connectAdapter())
        }
        try {
            session.appOpened()
            runCurrent()
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(session.state.value.connected)
            assertEquals(ConnectionPhase.ADAPTER_READY, session.state.value.connection.phase)
            assertFalse(session.state.value.phase.contains("non raggiungibile"))
            assertTrue(session.exportText().contains("Lettura centralina non riuscita:"))
        } finally {
            session.close()
        }
    }

    @Test fun `T09 NO DATA remains distinct from a malformed response`() = runTest {
        val demo = DemoTransport()
        val transport = object : ElmTransport {
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long) = if (command == "0100") "NO DATA\r>" else demo.exchange(command, timeoutMs)
            override fun close() = demo.close()
        }
        val session = DiagnosticsSession(backgroundScope, MemoryConnectionStore(target), { null }) { _, log ->
            val elm = ElmSession(transport, log)
            ElmAdapterLink(elm, transport, elm.connectAdapter())
        }
        try {
            session.appOpened()
            runCurrent()
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(session.state.value.connected)
            assertTrue(session.exportText().contains("Centralina non raggiungibile:"))
            assertFalse(session.exportText().contains("Lettura centralina non riuscita:"))
        } finally {
            session.close()
        }
    }
}
