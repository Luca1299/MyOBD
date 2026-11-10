package it.multipla.obd.core

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ElmSessionTest {
    @Test fun `demo exercises discovery measurements and three error categories`() = runTest {
        val requests = mutableListOf<String>()
        val session = ElmSession(DemoTransport()) { requests += it }
        val info = session.initialize()
        assertEquals(false, info.isCan)
        assertTrue(0x0C in info.supported)
        assertEquals(EngineStatus(true, 1), session.status())
        assertTrue(session.read(Parameters.all.first()).display.contains("820"))
        assertEquals(listOf("P0171"), session.readDtcs(DtcKind.STORED))
        assertEquals(emptyList<String>(), session.readDtcs(DtcKind.PENDING))
        try {
            session.readDtcs(DtcKind.PERMANENT)
            fail("NO DATA must remain inconclusive")
        } catch (_: NoDataException) { }
        assertFalse(requests.any { it == "TX  04" })
        session.close()
    }

    @Test fun `concurrent callers serialize their adapter commands`() = runTest {
        val demo = DemoTransport()
        var active = 0
        var maximum = 0
        val transport = object : ElmTransport {
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                active++
                maximum = maxOf(maximum, active)
                return try {
                    demo.exchange(command, timeoutMs)
                } finally {
                    active--
                }
            }
            override fun close() = demo.close()
        }
        val session = ElmSession(transport) { }
        session.initialize()
        val one = async { session.read(Parameters.all[0]) }
        val two = async { session.read(Parameters.all[1]) }
        one.await()
        two.await()
        assertEquals(1, maximum)
        session.close()
    }

    @Test fun `adapter identity without OBD response never establishes a vehicle session`() = runTest {
        val demo = DemoTransport()
        val transport = object : ElmTransport {
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long): String = if (command == "0100") "NO DATA\r>" else demo.exchange(command, timeoutMs)
            override fun close() = demo.close()
        }
        val session = ElmSession(transport) { }
        try {
            session.initialize()
            fail("Vehicle handshake must fail")
        } catch (_: NoDataException) { }
        try {
            session.status()
            fail("Session must stay uninitialized")
        } catch (_: IllegalStateException) { }
        session.close()
    }
}
