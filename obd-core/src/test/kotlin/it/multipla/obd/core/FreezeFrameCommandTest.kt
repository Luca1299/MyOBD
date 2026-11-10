package it.multipla.obd.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeFrameCommandTest {
    @Test fun `approved frame zero reads are allowed while writes remain forbidden`() {
        assertTrue(ElmSession.isAllowed("020200"))
        assertTrue(ElmSession.isAllowed("020000"))
        assertTrue(ElmSession.isAllowed("020C00"))
        for (command in listOf("04", "08", "2E0100", "020C01", "020C", "020C00AA")) {
            assertFalse(command, ElmSession.isAllowed(command))
        }
    }
}
