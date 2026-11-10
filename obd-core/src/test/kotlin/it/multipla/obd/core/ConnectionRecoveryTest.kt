package it.multipla.obd.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class ConnectionRecoveryTest {
    private val target = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")
    private class Link(val problem: Exception? = null) : AdapterLink {
        override val identity = "ELM test"
        override val disconnections = MutableSharedFlow<String>(replay = 1)
        var closed = false
        override suspend fun vehicle(): VehicleInfo {
            problem?.let { throw it }
            return VehicleInfo(identity, "KWP FAST", false, setOf(1, 12))
        }
        override fun close() {
            closed = true
        }
    }

    @Test fun `T05 one failed manual attempt cannot create an automatic lock`() = runTest {
        val store = MemoryConnectionStore(target)
        var calls = 0
        val first = ConnectionController(backgroundScope, store, { null }) {
            calls++
            throw IOException("fuori portata")
        }
        first.manualConnect()
        runCurrent()
        advanceTimeBy(100_000)
        runCurrent()
        assertEquals(1, calls)
        assertFalse(store.manualRequired())
        assertNotEquals(ConnectionPhase.MANUAL_REQUIRED, first.state.value.phase)
        first.close()
        val next = ConnectionController(backgroundScope, store, { null }) {
            calls++
            Link()
        }
        next.appOpened()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(ConnectionPhase.VEHICLE_READY, next.state.value.phase)
        assertEquals(3, next.state.value.maxAttempts)
        next.close()
    }

    @Test fun `T06 manual success rearms the next opening without a parallel connection`() = runTest {
        val store = MemoryConnectionStore(target, true)
        var calls = 0
        val first = ConnectionController(backgroundScope, store, { null }) {
            calls++
            Link()
        }
        first.appOpened()
        runCurrent()
        assertEquals(0, calls)
        first.manualConnect()
        runCurrent()
        assertEquals(1, calls)
        assertFalse(store.manualRequired())
        repeat(5) { first.appOpened() }
        runCurrent()
        assertEquals(1, calls)
        first.close()
        val next = ConnectionController(backgroundScope, store, { null }) {
            calls++
            Link()
        }
        next.appOpened()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(3, next.state.value.maxAttempts)
        assertEquals(ConnectionPhase.VEHICLE_READY, next.state.value.phase)
        next.close()
    }

    @Test fun `T07 loss after manual recovery gets three fresh attempts and can recover again`() = runTest {
        val store = MemoryConnectionStore(target, true)
        val initial = Link()
        var calls = 0
        val controller = ConnectionController(backgroundScope, store, { null }) {
            when (++calls) {
                1 -> initial
                5 -> Link()
                else -> throw IOException("fuori portata")
            }
        }
        controller.manualConnect()
        runCurrent()
        assertFalse(store.manualRequired())
        initial.disconnections.emit("EOF")
        runCurrent()
        assertTrue(initial.closed)
        assertEquals(2, calls)
        assertEquals(1, controller.state.value.attempt)
        assertEquals(3, controller.state.value.maxAttempts)
        assertFalse(store.manualRequired())
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(3, calls)
        assertFalse(store.manualRequired())
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(4, calls)
        assertTrue(store.manualRequired())
        advanceTimeBy(100_000)
        runCurrent()
        assertEquals(4, calls)
        controller.manualConnect()
        runCurrent()
        assertEquals(5, calls)
        assertFalse(store.manualRequired())
        assertEquals(ConnectionPhase.VEHICLE_READY, controller.state.value.phase)
        controller.close()
    }

    @Test fun `T08 manual adapter success rearms automation even without ECU data`() = runTest {
        val store = MemoryConnectionStore(target, true)
        val link = Link(NoDataException())
        var calls = 0
        val controller = ConnectionController(backgroundScope, store, { null }) {
            calls++
            link
        }
        controller.manualConnect()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(ConnectionPhase.ADAPTER_READY, controller.state.value.phase)
        assertTrue(controller.state.value.connected)
        assertFalse(store.manualRequired())
        assertFalse(link.closed)
        assertEquals(1, calls)
        assertTrue(controller.state.value.detail!!.contains("Centralina non raggiungibile"))
        controller.close()
    }

    @Test fun `T09 malformed ECU reply is a read failure and keeps the adapter connected`() = runTest {
        val link = Link(ElmResponseException("Formato OBD non riconosciuto: ZZ"))
        var calls = 0
        val controller = ConnectionController(backgroundScope, MemoryConnectionStore(target), { null }) {
            calls++
            link
        }
        controller.appOpened()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(ConnectionPhase.ADAPTER_READY, controller.state.value.phase)
        assertTrue(controller.state.value.detail!!.startsWith("Lettura centralina non riuscita:"))
        assertFalse(controller.state.value.detail!!.contains("non raggiungibile"))
        assertFalse(link.closed)
        assertEquals(1, calls)
        controller.close()
    }
}
