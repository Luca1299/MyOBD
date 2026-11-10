package it.multipla.obd

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import it.multipla.obd.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class SessionServiceTest {
    class Store : ConnectionStore {
        var blocked = false
        var failConnection = false
        var attempts = 0
        override fun target() = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")
        override fun saveTarget(target: SavedTarget) { }
        override fun manualRequired() = blocked
        override fun setManualRequired(required: Boolean) { blocked = required }
    }
    class FakeService : DiagnosticService() {
        override fun createScope(): CoroutineScope = fixtureScope
        override fun hasConnectPermission() = true
        override fun createSession(scope: CoroutineScope): DiagnosticsSession = DiagnosticsSession(scope, fixtureStore, { null }) { _, log ->
            fixtureStore.attempts++
            if (fixtureStore.failConnection) throw java.io.IOException("fuori portata")
            val demo = DemoTransport()
            val transport = object : ElmTransport {
                override suspend fun open() { opens++; demo.open() }
                override suspend fun exchange(command: String, timeoutMs: Long) = demo.exchange(command, timeoutMs)
                override fun close() { closes++; demo.close() }
            }
            val elm = ElmSession(transport, log)
            ElmAdapterLink(elm, transport, elm.connectAdapter())
        }
        companion object {
            lateinit var fixtureScope: CoroutineScope
            lateinit var fixtureStore: Store
            var opens = 0
            var closes = 0
        }
    }

    @Test fun `AC06 AC07 activity unbind and rebind preserve service session and notification`() = runTest {
        FakeService.fixtureScope = backgroundScope; FakeService.fixtureStore = Store(); FakeService.opens = 0; FakeService.closes = 0
        val controller = Robolectric.buildService(FakeService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent().setAction(DiagnosticService.ACTION_OPEN), 0, 1)
            runCurrent(); advanceTimeBy(3000); runCurrent()
            assertTrue(service.session.state.value.connected)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            service.onUnbind(Intent()); service.onBind(Intent())
            shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(false)
            advanceTimeBy(61_000); runCurrent()
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
            service.onStartCommand(Intent().setAction(DiagnosticService.ACTION_OPEN), 0, 2)
            runCurrent()
            assertEquals(1, FakeService.opens); assertEquals(0, FakeService.closes)
            assertTrue(service.session.state.value.connected)
        } finally { controller.destroy() }
        assertEquals(1, FakeService.closes)
    }

    @Test fun `AC08 notification stop closes session and releases wake lock`() = runTest {
        FakeService.fixtureScope = backgroundScope; FakeService.fixtureStore = Store(); FakeService.opens = 0; FakeService.closes = 0
        val controller = Robolectric.buildService(FakeService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent().setAction(DiagnosticService.ACTION_OPEN), 0, 1)
            runCurrent(); advanceTimeBy(3000); runCurrent()
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
            service.onStartCommand(Intent().setAction(DiagnosticService.ACTION_STOP), 0, 2)
            runCurrent()
            assertFalse(service.session.state.value.connected)
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
            assertEquals(1, FakeService.closes)
        } finally { controller.destroy() }
    }

    @Test fun `AC11 target and manual lock persist across Android store instances`() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(AndroidConnectionStore.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        val first = AndroidConnectionStore(app)
        val target = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")
        first.saveTarget(target); first.setManualRequired(true)
        val next = AndroidConnectionStore(app)
        assertEquals(target, next.target()); assertTrue(next.manualRequired())
        next.setManualRequired(false); assertFalse(AndroidConnectionStore(app).manualRequired())
    }

    @Test fun `T06 manual success persists rearming across Android store and session recreation`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(AndroidConnectionStore.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        val store = AndroidConnectionStore(app)
        store.saveTarget(SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")); store.setManualRequired(true)
        var calls = 0
        fun session(connectionStore: ConnectionStore) = DiagnosticsSession(backgroundScope, connectionStore, { null }) { _, log ->
            calls++
            val transport = DemoTransport(); val elm = ElmSession(transport, log)
            ElmAdapterLink(elm, transport, elm.connectAdapter())
        }
        val first = session(store)
        try {
            first.appOpened(); runCurrent(); assertEquals(0, calls)
            first.manualConnect(); runCurrent(); advanceTimeBy(3000); runCurrent()
            assertEquals(1, calls); assertFalse(AndroidConnectionStore(app).manualRequired())
        } finally { first.close() }
        val next = session(AndroidConnectionStore(app))
        try {
            next.appOpened(); runCurrent(); advanceTimeBy(3000); runCurrent()
            assertEquals(2, calls); assertEquals(ConnectionPhase.VEHICLE_READY, next.state.value.connection.phase)
            assertEquals(3, next.state.value.connection.maxAttempts)
        } finally { next.close() }
    }

    @Test fun `T05 failed unblocked manual connection stops foreground work without disabling automation`() = runTest {
        FakeService.fixtureScope = backgroundScope
        FakeService.fixtureStore = Store().apply { failConnection = true }
        FakeService.opens = 0; FakeService.closes = 0
        val controller = Robolectric.buildService(FakeService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent().setAction(DiagnosticService.ACTION_MANUAL), 0, 1)
            runCurrent(); advanceTimeBy(30_000); runCurrent()
            assertEquals(1, FakeService.fixtureStore.attempts)
            assertFalse(FakeService.fixtureStore.blocked)
            assertFalse(service.session.state.value.connected)
            assertFalse(service.session.state.value.busy)
            assertTrue(shadowOf(service).isForegroundStopped)
            assertTrue(shadowOf(service).isStoppedBySelf)
            ShadowPowerManager.getLatestWakeLock()?.let { assertFalse(it.isHeld) }
        } finally { controller.destroy() }
    }

    @Test fun `demo session cannot clear real manual connection gate`() = runTest {
        FakeService.fixtureScope = backgroundScope; FakeService.fixtureStore = Store().apply { blocked = true }; FakeService.opens = 0; FakeService.closes = 0
        val controller = Robolectric.buildService(FakeService::class.java).create()
        val service = controller.get()
        try {
            service.session.demo(); runCurrent(); advanceTimeBy(3000); runCurrent()
            assertTrue(service.session.state.value.demo); assertTrue(service.session.state.value.connected)
            assertTrue(FakeService.fixtureStore.blocked)
            service.session.exitDemo(); service.session.appOpened(); runCurrent()
            assertFalse(service.session.state.value.demo)
            assertEquals(ConnectionPhase.MANUAL_REQUIRED, service.session.state.value.connection.phase)
            assertTrue(FakeService.fixtureStore.blocked); assertEquals(0, FakeService.opens)
        } finally { controller.destroy() }
    }
}
