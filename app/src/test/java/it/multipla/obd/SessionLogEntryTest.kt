package it.multipla.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionLogEntryTest {
    @Test fun `known prefixes explain each event without changing payloads`() {
        val cases = listOf(
            Triple("TX  010C", SessionLogType.REQUEST, "010C"),
            Triple("RX  41 0C 0C D0\r\n>", SessionLogType.RESPONSE, "41 0C 0C D0\r\n>"),
            Triple("ERR Attesa scaduta", SessionLogType.ERROR, "Attesa scaduta"),
            Triple("STATO Centralina OBD raggiunta", SessionLogType.STATE, "Centralina OBD raggiunta"),
            Triple("NOTA Alimentazione: Metano", SessionLogType.NOTE, "Alimentazione: Metano"),
            Triple("CAMPIONE Metano · 7 parametri", SessionLogType.SAMPLE, "Metano · 7 parametri"),
        )
        cases.forEach { (message, type, content) ->
            val original = "14:32:03.120  $message"
            val entry = presentSessionLog(original)
            assertEquals(original, entry.original)
            assertEquals("14:32:03.120", entry.time)
            assertEquals(type, entry.type)
            assertEquals(content, entry.content)
        }
    }

    @Test fun `response whitespace and line endings remain intact`() {
        val payload = "  41 0C 0C D0\r\n41 05 80\r>  "
        val original = "14:32:03.120  RX  $payload"
        val entry = presentSessionLog(original)
        assertEquals(payload, entry.content)
        assertEquals("RX", entry.prefix)
        assertEquals(original, entry.original)
    }

    @Test fun `unknown prefixes preserve the entire message after a valid time`() {
        val entry = presentSessionLog("14:32:03.120  FUTURO  testo\nseconda riga")
        assertEquals("14:32:03.120", entry.time)
        assertEquals(SessionLogType.EVENT, entry.type)
        assertNull(entry.prefix)
        assertEquals("FUTURO  testo\nseconda riga", entry.content)
    }

    @Test fun `missing or invalid times leave the whole entry available`() {
        listOf("RX  41 0D 00\r>", "25:32:03.120  TX  010C", "14:62:03.120  TX  010C", "14:32:63.120  TX  010C", "14:32:03  TX  010C").forEach { original ->
            val entry = presentSessionLog(original)
            assertNull(entry.time)
            assertEquals(SessionLogType.EVENT, entry.type)
            assertEquals(original, entry.content)
        }
    }

    @Test fun `the visible register keeps the latest entries in order without mutating retention`() {
        val retained = (1..400).map { "14:32:03.120  NOTA Evento $it" }
        val visible = visibleSessionLogs(retained)
        assertEquals(120, visible.size)
        assertEquals(retained.takeLast(120), visible.map { it.original })
        assertEquals(400, retained.size)
        assertEquals(emptyList<SessionLogEntry>(), visibleSessionLogs(emptyList()))
        assertEquals(retained.take(3), visibleSessionLogs(retained.take(3)).map { it.original })
    }
}
