package it.multipla.obd.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le risposte aggregate conservano codici validi e limiti di ogni lettura. */
class MixedDtcResponsesTest {
    @Test fun `positive codes survive a negative response in either order`() = runTest {
        for (raw in listOf("43010171\r7F0311>", "7F0311\r43010171>")) {
            val demo = DemoTransport()
            val transport = object : ElmTransport {
                override suspend fun open() = demo.open()
                override suspend fun exchange(command: String, timeoutMs: Long) = when (command) {
                    "ATDPN" -> "A6>"
                    "03" -> raw
                    else -> demo.exchange(command, timeoutMs)
                }
                override fun close() = demo.close()
            }
            val elm = ElmSession(transport) {}
            try {
                elm.initialize()
                val result = DiagnosticReader(elm).codes(DtcKind.STORED)
                assertEquals(SectionState.INCONCLUSIVE, result.state)
                assertEquals(listOf("P0171"), result.value!!.map { it.code })
                assertFalse(result.conclusive)
                assertTrue(result.problems.any { it.contains("11") })
            } finally {
                elm.close()
            }
        }
    }

    @Test fun `an empty positive response and a refusal remain partial`() {
        val result = ObdParser.dtcReading("4300\r7F0311>", 3, true)
        assertTrue(result.codes.isEmpty())
        assertEquals(1, result.problems.size)
    }

    @Test fun `a malformed positive payload does not erase another valid response`() {
        val result = ObdParser.dtcReading("43010171\r43020133>", 3, true)
        assertEquals(listOf("P0171"), result.codes)
        assertTrue(result.problems.single().contains("incoerente"))
    }

    @Test fun `different refusals do not prove that the whole function is unsupported`() = runTest {
        val demo = DemoTransport()
        val transport = object : ElmTransport {
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long) = if (command == "03") "7F0311\r7F0331>" else demo.exchange(command, timeoutMs)
            override fun close() = demo.close()
        }
        val elm = ElmSession(transport) {}
        try {
            elm.initialize()
            val result = DiagnosticReader(elm).codes(DtcKind.STORED)
            assertEquals(SectionState.INCONCLUSIVE, result.state)
            assertFalse(result.conclusive)
        } finally {
            elm.close()
        }
    }
}
