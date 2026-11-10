package it.multipla.obd.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DiagnosticProtocolTest {
    @Test fun `continuous readiness support and completion use independent bit fields`() {
        val result = ReadinessDecoder.decode(listOf(0x82, 0x57, 0, 0))
        assertEquals(EngineStatus(true, 2), result.engine)
        assertEquals(MonitorState.INCOMPLETE, result.monitors.single { it.id == MonitorId.MISFIRE }.state)
        assertEquals(MonitorState.COMPLETED, result.monitors.single { it.id == MonitorId.FUEL_SYSTEM }.state)
        assertEquals(MonitorState.INCOMPLETE, result.monitors.single { it.id == MonitorId.COMPONENTS }.state)
    }

    @Test fun `compression ignition maps monitor bits without inventing spark monitors`() {
        val result = ReadinessDecoder.decode(listOf(0, 0x0F, 0xEB, 0x40))
        assertEquals(MonitorState.INCOMPLETE, result.monitors.single { it.id == MonitorId.PARTICULATE_FILTER }.state)
        assertEquals(MonitorState.COMPLETED, result.monitors.single { it.id == MonitorId.NOX_SCR }.state)
        assertFalse(result.monitors.any { it.id == MonitorId.OXYGEN_HEATER || it.id == MonitorId.EVAPORATIVE_SYSTEM })
    }

    @Test fun `freeze decoder rejects another frame wrong PID and malformed payloads`() {
        for (raw in listOf("420C011000>", "420D001000>", "420C0010>", "420C00ZZ00>", "420C001000\r420C001000>")) {
            assertThrows(raw, ElmResponseException::class.java) { ObdParser.freezePid(raw, 12, 2) }
        }
        assertEquals(listOf(0, 0), ObdParser.freezePid("420C000000>", 12, 2))
    }
}
