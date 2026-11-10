package it.multipla.obd.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AdapterInitializationTest {
    @Test fun `adapter remains usable when ECU initially reports NO DATA`() = runTest {
        val demo = DemoTransport(); var opens = 0; var missing = true; var resets = 0
        val transport = object : ElmTransport {
            override suspend fun open() { opens++; demo.open() }
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                if (command == "ATZ") resets++
                return if (command == "0100" && missing) "NO DATA\r>" else demo.exchange(command, timeoutMs)
            }
            override fun close() = demo.close()
        }
        val session = ElmSession(transport) { }
        assertTrue(session.connectAdapter().contains("ELM"))
        try { session.discoverVehicle(); fail("Expected inconclusive ECU response") } catch (_: NoDataException) { }
        missing = false
        assertTrue(12 in session.discoverVehicle().supported)
        assertEquals(1, opens); assertEquals(1, resets); session.close()
    }
}
