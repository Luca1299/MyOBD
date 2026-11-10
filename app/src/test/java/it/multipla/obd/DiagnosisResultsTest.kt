package it.multipla.obd

import it.multipla.obd.diagnostics.DiagnosisFixture
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

/** Verifica i limiti del contenitore e l’integrazione fra sessioni successive. */
class DiagnosisResultsTest {
    @Test fun `new acquisitions reset visible values and obsolete ended results cannot replace the latest`() {
        val results = DiagnosisResults()
        val first = DiagnosisFixture(results = results)
        first.connect()
        first.session.startDiagnosis()
        first.tick()
        val original = first.session.state.value.diagnosis!!
        first.close()
        val next = DiagnosisFixture(results = results)
        try {
            assertEquals(original, next.session.state.value.diagnosis)
            next.connect()
            next.hold = "0101"
            next.release = CompletableDeferred()
            next.session.startDiagnosis()
            next.tick(200)
            val running = next.session.state.value.diagnosis!!
            assertNotEquals(original.id, running.id)
            assertNull(running.readiness.value)
            assertNull(running.parameters.value)
            assertEquals(original, results.last(false))
            next.session.disconnect()
            next.tick(1)
            val interrupted = next.session.state.value.diagnosis!!
            assertEquals(DiagnosisOutcome.INTERRUPTED, interrupted.outcome)
            results.retain(original)
            assertEquals(interrupted, results.last(false))
            next.session.demo()
            next.tick()
            next.session.startDiagnosis()
            next.tick()
            val demo = next.session.state.value.diagnosis!!
            assertEquals(demo, results.last(true))
            assertEquals(interrupted, results.last(false))
            next.session.exitDemo()
            assertEquals(interrupted, next.session.state.value.diagnosis)
            assertNull(DiagnosisResults().last(false))
            assertNull(DiagnosisResults().last(true))
        } finally {
            next.close()
        }
    }

    @Test fun `running acquisitions cannot be retained as completed results`() {
        val result = Diagnosis(1, DiagnosisContext(false, "OBDII", "ELM", "ISO", "Non annotata", Instant.EPOCH))
        assertThrows(IllegalArgumentException::class.java) { DiagnosisResults().retain(result) }
    }
}
