package it.multipla.obd.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionControllerTest {
    private val target = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")
    private val vehicle = VehicleInfo("ELM", "ISO 9141-2", false, setOf(1, 5, 12))
    private class Store(var saved: SavedTarget?, var blocked: Boolean = false) : ConnectionStore {
        override fun target() = saved
        override fun saveTarget(target: SavedTarget) { saved = target }
        override fun manualRequired() = blocked
        override fun setManualRequired(required: Boolean) { blocked = required }
    }
    private inner class Link : AdapterLink {
        override val identity = "ELM test"
        val lost = MutableSharedFlow<String>(replay = 1)
        override val disconnections: Flow<String> = lost
        var closed = false
        var probes = 0
        var problem: Exception? = null
        override suspend fun vehicle(): VehicleInfo { probes++; problem?.let { throw it }; return vehicle }
        override fun close() { closed = true }
    }

    @Test fun `AC01 opens saved target automatically and reaches vehicle`() = runTest {
        val store = Store(target); var calls = 0; val link = Link()
        val c = ConnectionController(backgroundScope, store, { null }) { chosen ->
            assertEquals(target, chosen); calls++; link
        }
        c.appOpened(); runCurrent()
        assertEquals(1, calls); assertEquals(ConnectionPhase.VEHICLE_READY, c.state.value.phase)
        assertSame(link, c.activeLink); assertFalse(store.blocked)
        c.close()
    }

    @Test fun `AC02 AC03 exactly three attempts with delays from each failure`() = runTest {
        val store = Store(target); val starts = mutableListOf<Long>()
        val c = ConnectionController(backgroundScope, store, { null }) {
            starts += testScheduler.currentTime; delay(500); throw IOException("assente")
        }
        c.appOpened(); runCurrent(); advanceTimeBy(500); runCurrent()
        assertEquals(ConnectionPhase.RETRY_WAIT, c.state.value.phase)
        advanceTimeBy(2999); runCurrent(); assertEquals(listOf(0L), starts)
        advanceTimeBy(1); runCurrent(); assertEquals(listOf(0L, 3500L), starts)
        advanceTimeBy(3500); runCurrent(); assertEquals(listOf(0L, 3500L, 7000L), starts)
        advanceTimeBy(500); runCurrent()
        assertEquals(ConnectionPhase.MANUAL_REQUIRED, c.state.value.phase); assertTrue(store.blocked)
        advanceTimeBy(100_000); runCurrent(); assertEquals(3, starts.size)
        c.close()
    }

    @Test fun `AC04 second attempt success cancels remaining retries`() = runTest {
        var calls = 0; val link = Link()
        val c = ConnectionController(backgroundScope, Store(target), { null }) {
            if (++calls == 1) throw IOException("assente"); link
        }
        c.appOpened(); runCurrent(); advanceTimeBy(3000); runCurrent()
        assertEquals(ConnectionPhase.VEHICLE_READY, c.state.value.phase)
        advanceTimeBy(100_000); runCurrent(); assertEquals(2, calls)
        c.close()
    }

    @Test fun `AC04 third attempt success does not enter manual lock`() = runTest {
        val store = Store(target); var calls = 0
        val c = ConnectionController(backgroundScope, store, { null }) {
            if (++calls < 3) throw IOException("assente"); Link()
        }
        c.appOpened(); runCurrent(); advanceTimeBy(6000); runCurrent()
        assertEquals(3, calls); assertFalse(store.blocked)
        assertEquals(ConnectionPhase.VEHICLE_READY, c.state.value.phase); c.close()
    }

    @Test fun `AC11 manual lock survives reopening and only explicit success clears it`() = runTest {
        val store = Store(target); var calls = 0
        val first = ConnectionController(backgroundScope, store, { null }) { calls++; throw IOException("assente") }
        first.appOpened(); runCurrent(); advanceTimeBy(6000); runCurrent(); first.close()
        val next = ConnectionController(backgroundScope, store, { null }) { calls++; Link() }
        next.appOpened(); runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(3, calls); assertEquals(ConnectionPhase.MANUAL_REQUIRED, next.state.value.phase)
        next.manualConnect(); runCurrent()
        assertEquals(4, calls); assertFalse(store.blocked)
        assertEquals(ConnectionPhase.VEHICLE_READY, next.state.value.phase); next.close()
    }

    @Test fun `AC11 failed manual action has one attempt and preserves lock`() = runTest {
        val store = Store(target, true); var calls = 0
        val c = ConnectionController(backgroundScope, store, { null }) { calls++; throw IOException("assente") }
        c.manualConnect(); runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, calls); assertTrue(store.blocked)
        c.appOpened(); runCurrent(); assertEquals(1, calls); c.close()
    }

    @Test fun `AC12 unavailable ECU keeps adapter and consumes no Bluetooth retries`() = runTest {
        val link = Link().apply { problem = NoDataException() }; var calls = 0
        val c = ConnectionController(backgroundScope, Store(target), { null }) { calls++; link }
        c.appOpened(); runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(ConnectionPhase.ADAPTER_READY, c.state.value.phase)
        assertFalse(link.closed); assertEquals(1, calls); assertNotNull(c.state.value.detail)
        link.problem = null; c.recheckVehicle(); runCurrent()
        assertEquals(ConnectionPhase.VEHICLE_READY, c.state.value.phase)
        assertEquals(1, calls); assertEquals(2, link.probes); c.close()
    }

    @Test fun `AC05 physical loss starts one new cycle with three total attempts`() = runTest {
        val initial = Link(); var calls = 0; val store = Store(target)
        val c = ConnectionController(backgroundScope, store, { null }) {
            if (++calls == 1) initial else throw IOException("fuori portata")
        }
        c.appOpened(); runCurrent(); initial.lost.emit("EOF"); runCurrent()
        assertTrue(initial.closed); assertEquals(2, calls)
        advanceTimeBy(6000); runCurrent()
        assertEquals(4, calls); assertTrue(store.blocked)
        assertEquals(ConnectionPhase.MANUAL_REQUIRED, c.state.value.phase); c.close()
    }

    @Test fun `AC06 repeated UI openings never overlap an in flight attempt`() = runTest {
        var calls = 0; var active = 0; var maximum = 0
        val c = ConnectionController(backgroundScope, Store(target), { null }) {
            calls++; active++; maximum = maxOf(maximum, active)
            try { delay(1000); Link() } finally { active-- }
        }
        repeat(10) { c.appOpened() }; runCurrent()
        assertEquals(1, calls); advanceTimeBy(1000); runCurrent()
        repeat(10) { c.appOpened() }; runCurrent()
        assertEquals(1, calls); assertEquals(1, maximum); c.close()
    }

    @Test fun `AC06 opening during retry wait does not reset the budget`() = runTest {
        var calls = 0
        val c = ConnectionController(backgroundScope, Store(target), { null }) { calls++; throw IOException("assente") }
        c.appOpened(); runCurrent()
        repeat(5) { c.appOpened() }; runCurrent()
        advanceTimeBy(6000); runCurrent(); assertEquals(3, calls); c.close()
    }

    @Test fun `AC08 stop cancels scheduled retries and reopening current UI does not resume them`() = runTest {
        var calls = 0
        val c = ConnectionController(backgroundScope, Store(target), { null }) { calls++; throw IOException("assente") }
        c.appOpened(); runCurrent(); c.stop(); runCurrent()
        c.appOpened(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, calls); assertEquals(ConnectionPhase.STOPPED, c.state.value.phase); c.close()
    }

    @Test fun `AC08 stop cancels blocking connect and prevents a late reconnect`() = runTest {
        var cancelled = false; var calls = 0
        val c = ConnectionController(backgroundScope, Store(target), { null }) {
            calls++; try { awaitCancellation() } finally { cancelled = true }
        }
        c.appOpened(); runCurrent(); c.stop(); runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertTrue(cancelled); assertEquals(1, calls); c.close()
    }

    @Test fun `AC08 stop closes live link and ignores its subsequent loss signal`() = runTest {
        val link = Link(); var calls = 0
        val c = ConnectionController(backgroundScope, Store(target), { null }) { calls++; link }
        c.appOpened(); runCurrent(); c.stop(); runCurrent(); link.lost.emit("closed"); runCurrent()
        assertTrue(link.closed); assertEquals(1, calls); c.close()
    }

    @Test fun `AC10 missing profile does not consume attempts`() = runTest {
        var calls = 0
        val c = ConnectionController(backgroundScope, Store(null), { null }) { calls++; Link() }
        c.appOpened(); runCurrent()
        assertEquals(ConnectionPhase.NEEDS_SETUP, c.state.value.phase); assertEquals(0, calls); c.close()
    }

    @Test fun `AC10 missing permission or disabled Bluetooth waits without attempts`() = runTest {
        var problem: String? = "Bluetooth spento"; var calls = 0
        val c = ConnectionController(backgroundScope, Store(target), { problem }) { calls++; Link() }
        c.appOpened(); runCurrent(); advanceTimeBy(20_000); runCurrent(); assertEquals(0, calls)
        problem = null; c.appOpened(); runCurrent(); assertEquals(1, calls); c.close()
    }

    @Test fun `AC09 selected identifier is preserved instead of an identically named device`() = runTest {
        var chosen: SavedTarget? = null
        val c = ConnectionController(backgroundScope, Store(target), { null }) { chosen = it; Link() }
        c.appOpened(); runCurrent(); assertEquals("AA:BB:CC:DD:EE:01", chosen?.address); c.close()
    }

    @Test fun `profile saved while idle becomes target of automatic connection`() = runTest {
        val store = Store(null); var chosen: SavedTarget? = null
        val c = ConnectionController(backgroundScope, store, { null }) { chosen = it; Link() }
        c.saveTarget(target); c.appOpened(); runCurrent()
        assertEquals(target, chosen); assertEquals(target, store.saved); c.close()
    }

    @Test fun `changing target cannot replace an active connection`() = runTest {
        val store = Store(target)
        val c = ConnectionController(backgroundScope, store, { null }) { Link() }
        c.appOpened(); runCurrent(); c.saveTarget(target.copy(address = "OTHER")); runCurrent()
        assertEquals(target, store.saved); c.close()
    }

    @Test fun `command acknowledgement observes policy state even when repeated state is equal`() = runTest {
        val c = ConnectionController(backgroundScope, Store(target, true), { null }) { Link() }
        val first = c.appOpened()
        assertFalse(first.isCompleted); runCurrent()
        assertTrue(first.isCompleted); assertEquals(ConnectionPhase.MANUAL_REQUIRED, c.state.value.phase)
        val repeated = c.appOpened(); runCurrent()
        assertTrue(repeated.isCompleted); assertEquals(ConnectionPhase.MANUAL_REQUIRED, c.state.value.phase)
        c.close()
    }
}
