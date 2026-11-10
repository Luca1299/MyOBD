package it.multipla.obd.diagnostics

import it.multipla.obd.DiagnosisOutcome
import it.multipla.obd.DiagnosisPhase
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.SectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosisRobustnessTest {
    @Test fun `manual code export retains both a valid code and its reading limitation`() {
        val f = DiagnosisFixture()
        try {
            f.responses["ATDPN"] = "A6>"
            f.responses["03"] = "43010133\r7F0311>"
            f.connect()
            f.session.readErrors()
            f.tick()
            val report = f.session.exportText()
            assertTrue(report.contains("P0133"))
            assertTrue(report.contains("Lettura non conclusiva"))
        } finally {
            f.close()
        }
    }

    @Test fun `a mixed code response makes the acquisition partial and preserves the code in export`() {
        val f = DiagnosisFixture()
        try {
            f.responses["ATDPN"] = "A6>"
            f.responses["03"] = "43010171\r7F0311>"
            f.connect()
            f.session.startDiagnosis()
            f.tick()
            val diagnosis = f.session.state.value.diagnosis!!
            assertEquals(DiagnosisOutcome.PARTIAL, diagnosis.outcome)
            assertEquals(listOf("P0171"), diagnosis.codes.getValue(DtcKind.STORED).value!!.map { it.code })
            val exported = f.session.exportDiagnosis()!!
            assertTrue(exported.contains("P0171 — Miscela troppo magra"))
            assertTrue(exported.contains("Lettura non conclusiva"))
            assertTrue(exported.contains("7F0311"))
        } finally {
            f.close()
        }
    }

    @Test fun `an interruption accepted during finalization wins over a stale completion`() {
        val finalizationEntered = CountDownLatch(1)
        val resumeFinalization = CountDownLatch(1)
        val clockCalls = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        val f = DiagnosisFixture(now = {
            if (clockCalls.incrementAndGet() == 2) {
                finalizationEntered.countDown()
                check(resumeFinalization.await(5, TimeUnit.SECONDS)) { "Finalizzazione non rilasciata" }
            }
            Instant.parse("2026-11-10T12:00:00Z")
        })
        var worker: Thread? = null
        try {
            f.connect()
            f.session.startDiagnosis()
            worker = thread(name = "diagnosis-finalization-test") {
                try {
                    f.tick()
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
            assertTrue(finalizationEntered.await(5, TimeUnit.SECONDS))
            f.session.interruptDiagnosis()
            assertEquals(DiagnosisPhase.STOPPING, f.session.state.value.diagnosis!!.phase)
            resumeFinalization.countDown()
            worker.join(5000)
            assertFalse(worker.isAlive)
            failure.get()?.let { throw it }
            assertEquals(DiagnosisOutcome.INTERRUPTED, f.session.state.value.diagnosis!!.outcome)
            assertTrue(f.session.exportDiagnosis()!!.contains("Diagnosi interrotta dall’utente"))
        } finally {
            resumeFinalization.countDown()
            worker?.join(5000)
            f.close()
        }
    }

    @Test fun `registry export includes a later manual sample without changing the diagnosis`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            f.session.fuel("Benzina")
            f.session.startDiagnosis()
            f.tick()
            val ended = f.session.exportDiagnosis()
            f.responses["010C"] = "410C2000>"
            f.session.fuel("Metano")
            f.session.readSnapshot()
            f.tick()
            f.session.message("Evento successivo alla diagnosi")
            val requestsBeforeExport = f.commands.toList()
            val registry = f.session.exportText()
            assertTrue(registry.contains("2048.0 giri/min"))
            assertTrue(registry.contains("Metano"))
            assertTrue(registry.contains("Benzina"))
            assertTrue(registry.contains("DIAGNOSI CONCLUSA · CONTESTO E DATI ACQUISITI IN PRECEDENZA"))
            assertTrue(registry.contains("RX  410C2000>"))
            assertEquals(ended, f.session.exportDiagnosis())
            assertEquals(requestsBeforeExport, f.commands)
        } finally {
            f.close()
        }
    }

    @Test fun `interruption before the first request sends no diagnostic command`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            val before = f.commands.toList()
            f.session.startDiagnosis()
            f.session.interruptDiagnosis()
            f.tick()
            assertEquals(before, f.commands)
            assertEquals(DiagnosisOutcome.INTERRUPTED, f.session.state.value.diagnosis!!.outcome)
        } finally {
            f.close()
        }
    }

    @Test fun `transport loss inside current sample retains each earlier measurement`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            f.failOn = "0105"
            f.session.startDiagnosis()
            f.tick()
            val d = f.session.state.value.diagnosis!!
            assertEquals(DiagnosisOutcome.INTERRUPTED, d.outcome)
            assertEquals("820.0 giri/min", d.parameters.value!!.readings.single().display)
            assertEquals(SectionState.INTERRUPTED, d.parameters.state)
        } finally {
            f.close()
        }
    }

    @Test fun `transport loss inside stored frame retains earlier frame values`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            f.failOn = "020D00"
            f.session.startDiagnosis()
            f.tick()
            val d = f.session.state.value.diagnosis!!
            assertEquals(DiagnosisOutcome.INTERRUPTED, d.outcome)
            assertEquals("P0171", d.freezeFrame.value!!.code)
            assertEquals("1024.0 giri/min", d.freezeFrame.value!!.readings.single().display)
        } finally {
            f.close()
        }
    }

    @Test fun `interruption between stored requests preserves returned values and sends no current read`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            f.hold = "020D00"
            f.session.startDiagnosis()
            f.tick(1000)
            assertTrue(f.commands.contains("020D00"))
            f.session.interruptDiagnosis()
            f.release.complete(Unit)
            f.tick()
            val d = f.session.state.value.diagnosis!!
            assertEquals(DiagnosisOutcome.INTERRUPTED, d.outcome)
            assertEquals(2, d.freezeFrame.value!!.readings.size)
            assertEquals(SectionState.NOT_PERFORMED, d.parameters.state)
            assertFalse(f.commands.contains("010C"))
            assertEquals(0, f.closes)
        } finally {
            f.close()
        }
    }

    @Test fun `pending interruption keeps the existing command timeout and interrupts on expiry`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            f.hold = "03"
            f.session.startDiagnosis()
            f.tick(200)
            f.session.interruptDiagnosis()
            val remaining = 10000 - (f.scope.testScheduler.currentTime - f.requestTimes.getValue("03"))
            f.tick(remaining - 1)
            assertEquals(DiagnosisPhase.STOPPING, f.session.state.value.diagnosis!!.phase)
            f.tick(1)
            assertEquals(DiagnosisOutcome.INTERRUPTED, f.session.state.value.diagnosis!!.outcome)
            assertEquals(1, f.closes)
            assertFalse(f.commands.contains("07"))
            assertTrue(f.session.exportDiagnosis()!!.contains("Tempo di risposta"))
        } finally {
            f.close()
        }
    }

    @Test fun `a non cooperative old response cannot replace a later acquisition`() {
        val f = DiagnosisFixture()
        try {
            f.connect()
            f.hold = "03"
            f.ignoreCancellation = true
            f.session.startDiagnosis()
            f.tick(200)
            val oldId = f.session.state.value.diagnosis!!.id
            val oldRelease = f.release
            f.session.disconnect()
            f.tick(1)
            f.hold = null
            f.session.manualConnect()
            f.tick()
            f.hold = "0101"
            f.release = CompletableDeferred()
            f.session.startDiagnosis()
            f.tick(200)
            val newId = f.session.state.value.diagnosis!!.id
            assertNotEquals(oldId, newId)
            oldRelease.complete(Unit)
            f.tick(1)
            assertEquals(newId, f.session.state.value.diagnosis!!.id)
            assertEquals(SectionState.NOT_PERFORMED, f.session.state.value.diagnosis!!.readiness.state)
            assertTrue(f.session.state.value.busy)
            f.release.complete(Unit)
            f.tick()
            assertNotNull(f.session.exportDiagnosis())
        } finally {
            f.close()
        }
    }
}
