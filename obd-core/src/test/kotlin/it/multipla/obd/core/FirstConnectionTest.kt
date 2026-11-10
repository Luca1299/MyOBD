package it.multipla.obd.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstConnectionTest {
    private val recordedReply = "SEARCHING...\r..4100BE3EB81100\r>"
    private val recordedBytes = listOf(0xBE, 0x3E, 0xB8, 0x11, 0x00)

    @Test fun `T01 recorded first response preserves every data byte`() {
        assertEquals(recordedBytes, ObdParser.pid(recordedReply, 0, 4))
    }

    @Test fun `T02 search progress can precede data inline or across lines`() {
        val replies = listOf(
            "SEARCHING.....4100BE3EB81100\r>",
            "0100\rSEARCHING...\r.\r..4100BE3EB81100\r>",
            "SEARCHING...\r...\r4100BE3EB81100\r>",
            "searching...\n..41 00 BE 3E B8 11 00\n>",
            "SEARCHING...4100BE3EB81100\r>",
            "4100BE3EB81100\r>"
        )
        replies.forEach { assertEquals(it, recordedBytes, ObdParser.pid(it, 0, 4)) }
    }

    @Test fun `T02 progress handling never repairs corrupt or truncated data`() {
        val replies = listOf(
            "..4100BE3EB81100\r>",
            "SEARCHING...\r..41ZZBE3EB81100\r>",
            "SEARCHING...\r..4100BE3EB8110\r>",
            "SEARCHING...\r..4100BE3E\r>",
            "SEARCHING...\r..4100BE..3EB81100\r>",
            "SEARCHING...\rX4100BE3EB81100\r>",
            "SEARCHING...\r4100BE3EB81100\r..4100BE3EB81100\r>"
        )
        replies.forEach { raw ->
            assertThrows(raw, ElmResponseException::class.java) { ObdParser.pid(raw, 0, 4) }
        }
    }

    @Test fun `T02 search context ends with the command response`() {
        ObdParser.pid("SEARCHING...\r4100BE3EB81100\r>", 0, 4)
        assertThrows(ElmResponseException::class.java) { ObdParser.pid("..4100BE3EB81100\r>", 0, 4) }
        assertThrows(ElmResponseException::class.java) { ObdParser.pid("SEARCHING...\r...\r>", 0, 4) }
    }

    @Test(expected = NoDataException::class)
    fun `T02 search progress followed by NO DATA remains inconclusive`() {
        ObdParser.pid("SEARCHING...\r...\rNO DATA\r>", 0, 4)
    }

    @Test fun `T03 one manual connection reaches ECU using the recorded first reply`() = runTest {
        val demo = DemoTransport()
        var opens = 0; var resets = 0; var initialQueries = 0; var connections = 0
        val transport = object : ElmTransport {
            override suspend fun open() { opens++; demo.open() }
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                if (command == "ATZ") resets++
                return when (command) {
                    "0100" -> { initialQueries++; recordedReply }
                    "0120" -> "41208000000020\r>"
                    "ATDP" -> "AUTO, ISO 14230-4 (KWP FAST)\r>"
                    "ATDPN" -> "A5\r>"
                    else -> demo.exchange(command, timeoutMs)
                }
            }
            override fun close() = demo.close()
        }
        val session = ElmSession(transport) { }
        val store = MemoryConnectionStore(SavedTarget("OBDII", "AA:BB:CC:DD:EE:01"), true)
        val controller = ConnectionController(backgroundScope, store, { null }) {
            connections++
            val adapterIdentity = session.connectAdapter()
            object : AdapterLink {
                override val identity = adapterIdentity
                override val disconnections = transport.disconnects
                override suspend fun vehicle() = session.discoverVehicle()
                override fun close() = session.close()
            }
        }
        try {
            controller.manualConnect(); runCurrent(); advanceTimeBy(30_000); runCurrent()
            assertEquals(ConnectionPhase.VEHICLE_READY, controller.state.value.phase)
            assertEquals("AUTO, ISO 14230-4 (KWP FAST)", controller.state.value.info?.protocol)
            assertEquals(false, controller.state.value.info?.isCan)
            assertTrue(12 in controller.state.value.info!!.supported)
            assertTrue(33 in controller.state.value.info!!.supported)
            assertFalse(store.manualRequired())
            assertEquals(1, connections); assertEquals(1, opens)
            assertEquals(1, resets); assertEquals(1, initialQueries)
        } finally { controller.close() }
    }
}
