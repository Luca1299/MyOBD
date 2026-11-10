package it.multipla.obd.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException

class ObdParserTest {
    @Test fun `echo searching and compact PID response are accepted`() {
        val data = ObdParser.pid("010C\rSEARCHING...\r410C1AF8\r>", 0x0C, 2)
        assertEquals(1726.0, (data[0] * 256 + data[1]) / 4.0, 0.001)
    }

    @Test fun `K line DTCs decode all four categories and ignore padding`() {
        assertEquals(listOf("P0133", "C0234", "B0567", "U1016"),
            ObdParser.dtcs("43 01 33 42 34 85 67 D0 16 00 00\r>", 3, false))
    }

    @Test fun `valid K line zero response means no returned codes`() {
        assertEquals(emptyList<String>(), ObdParser.dtcs("43 00 00 00 00 00 00\r>", 3, false))
    }

    @Test fun `CAN count byte is not decoded as a DTC`() {
        assertEquals(listOf("P0133", "P0210"), ObdParser.dtcs("43 02 01 33 02 10\r>", 3, true))
        assertEquals(emptyList<String>(), ObdParser.dtcs("43 00\r>", 3, true))
    }

    @Test fun `indexed ELM CAN response is reassembled with its declared length`() {
        val response = "00E\r0: 43 06 01 33 02 10\r1: 03 01 04 20 05 00\r2: D0 16\r>"
        assertEquals(listOf("P0133", "P0210", "P0301", "P0420", "P0500", "U1016"), ObdParser.dtcs(response, 3, true))
    }

    @Test fun `multiple positive ECU responses retain all codes`() {
        assertEquals(listOf("P0133", "U0123"), ObdParser.dtcs("43 01 01 33\r43 01 C1 23\r>", 3, true))
    }

    @Test fun `pending and permanent use the proper positive services`() {
        assertEquals(listOf("P0171"), ObdParser.dtcs("47 01 71\r>", 7, false))
        assertEquals(listOf("P0171"), ObdParser.dtcs("4A 01 01 71\r>", 10, true))
    }

    @Test(expected = NoDataException::class)
    fun `NO DATA is inconclusive rather than an empty error list`() {
        ObdParser.dtcs("NO DATA\r>", 3, false)
    }

    @Test(expected = ElmResponseException::class)
    fun `empty prompt is not a successful scan`() { ObdParser.dtcs(">", 3, false) }

    @Test(expected = ElmResponseException::class)
    fun `truncated CAN DTC count is rejected`() { ObdParser.dtcs("43 02 01 33\r>", 3, true) }

    @Test(expected = ElmResponseException::class)
    fun `unaccounted CAN data is rejected`() { ObdParser.dtcs("43 00 01 33\r>", 3, true) }

    @Test(expected = ElmResponseException::class)
    fun `null DTC cannot satisfy a positive CAN count`() { ObdParser.dtcs("43 01 00 00\r>", 3, true) }

    @Test(expected = ElmResponseException::class)
    fun `missing K line DTC byte is rejected`() { ObdParser.dtcs("43 01\r>", 3, false) }

    @Test(expected = ElmResponseException::class)
    fun `indexed CAN length mismatch is rejected`() {
        ObdParser.dtcs("008\r0: 43 02 01 33 02 10\r>", 3, true)
    }

    @Test(expected = ElmResponseException::class)
    fun `missing CAN fragment is rejected`() {
        ObdParser.dtcs("008\r0: 43 03 01 33\r2: 02 10 03 01\r>", 3, true)
    }

    @Test(expected = ObdNegativeResponse::class)
    fun `ECU refusal is distinct from no errors`() { ObdParser.dtcs("7F 03 11\r>", 3, false) }

    @Test(expected = ElmResponseException::class)
    fun `another PID does not count as a valid measurement`() { ObdParser.pid("41 05 80\r>", 0x0C, 2) }

    @Test(expected = ElmResponseException::class)
    fun `partial parameter response is rejected`() { ObdParser.pid("41 0C 10\r>", 0x0C, 2) }

    @Test(expected = ElmResponseException::class)
    fun `adapter bus error is surfaced`() { ObdParser.pid("BUS INIT: ERROR\r>", 0, 4) }

    @Test fun `supported PID bits use network order and base offset`() {
        assertEquals(setOf(1, 32), ObdParser.supported("41 00 80 00 00 01\r>", 0))
        assertEquals(setOf(33, 64), ObdParser.supported("41 20 80 00 00 01\r>", 32))
    }

    @Test fun `reader stops exactly at prompt preserving the next response`() {
        val stream = ByteArrayInputStream("41 05 80\r>43 00 00\r>".toByteArray())
        assertEquals("41 05 80\r>", PromptReader.read(stream))
        assertEquals("43 00 00\r>", PromptReader.read(stream))
    }

    @Test(expected = EOFException::class)
    fun `Android 17 end of stream terminates rather than spinning`() {
        PromptReader.read(ByteArrayInputStream("41 05".toByteArray()))
    }

    @Test(expected = IOException::class)
    fun `response without prompt has a bounded buffer`() {
        PromptReader.read(ByteArrayInputStream("123456789".toByteArray()), 5)
    }
}
