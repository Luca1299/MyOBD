package it.multipla.obd.diagnostics

import it.multipla.obd.DiagnosisOutcome
import it.multipla.obd.DiagnosisPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** La preparazione del contesto non accoda una diagnosi al collegamento successivo. */
class DiagnosisStartTest {
    @Test fun `transport loss before admission leaves no running diagnosis`() {
        rejectedStart(recoverBeforeRelease = false, previousResult = false)
    }

    @Test fun `a recovered replacement link cannot accept a request prepared for the lost link`() {
        rejectedStart(recoverBeforeRelease = true, previousResult = false)
    }

    @Test fun `rejected admission preserves the previous ended diagnosis and its export`() {
        rejectedStart(recoverBeforeRelease = false, previousResult = true)
    }

    @Test fun `transport loss while admission is published ends the diagnosis before any request`() {
        val f = DiagnosisFixture()
        val loseOnce = AtomicBoolean(true)
        try {
            f.connect()
            val requests = f.commands.count { it == "0101" }
            val observer = f.scope.backgroundScope.launch(Dispatchers.Unconfined) {
                f.session.state.collect { state ->
                    if (state.busy && state.diagnosis?.phase == DiagnosisPhase.ACQUIRING && loseOnce.compareAndSet(true, false)) {
                        check(f.losses.tryEmit("Perdita durante la pubblicazione dell’avvio"))
                        f.tick(1)
                    }
                }
            }
            f.session.startDiagnosis()
            f.tick()
            assertFalse(loseOnce.get())
            assertEquals(DiagnosisPhase.ENDED, f.session.state.value.diagnosis!!.phase)
            assertEquals(DiagnosisOutcome.INTERRUPTED, f.session.state.value.diagnosis!!.outcome)
            assertEquals(requests, f.commands.count { it == "0101" })
            assertFalse(f.session.state.value.busy)
            assertNotNull(f.session.exportDiagnosis())
            observer.cancel()
        } finally {
            f.close()
        }
    }

    private fun rejectedStart(recoverBeforeRelease: Boolean, previousResult: Boolean) {
        val preparationEntered = CountDownLatch(1)
        val resumePreparation = CountDownLatch(1)
        val pauseNextStart = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        val f = DiagnosisFixture(now = {
            if (pauseNextStart.compareAndSet(true, false)) {
                preparationEntered.countDown()
                check(resumePreparation.await(5, TimeUnit.SECONDS)) { "Preparazione non rilasciata" }
            }
            Instant.EPOCH
        })
        var worker: Thread? = null
        try {
            f.connect()
            if (previousResult) {
                f.session.startDiagnosis()
                f.tick()
            }
            val previous = f.session.state.value.diagnosis
            val exported = f.session.exportDiagnosis()
            val requests = f.commands.count { it == "0101" }
            pauseNextStart.set(true)
            worker = thread(name = "diagnosis-admission-test") {
                try {
                    f.session.startDiagnosis()
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
            assertTrue(preparationEntered.await(5, TimeUnit.SECONDS))
            assertTrue(f.losses.tryEmit("Perdita durante la preparazione"))
            f.tick(if (recoverBeforeRelease) 5000 else 1)
            resumePreparation.countDown()
            worker.join(5000)
            assertFalse(worker.isAlive)
            failure.get()?.let { throw it }
            f.tick()
            assertNotNull(f.session.state.value.info)
            assertFalse(f.session.state.value.busy)
            assertEquals(previous, f.session.state.value.diagnosis)
            assertEquals(previous, f.results.last(false))
            assertEquals(exported, f.session.exportDiagnosis())
            assertEquals(requests, f.commands.count { it == "0101" })
            f.session.interruptDiagnosis()
            f.tick()
            assertEquals(previous, f.session.state.value.diagnosis)
            f.session.startDiagnosis()
            f.tick()
            val next = f.session.state.value.diagnosis!!
            assertEquals(DiagnosisPhase.ENDED, next.phase)
            assertNotEquals(previous?.id, next.id)
            assertNotNull(f.session.exportDiagnosis())
        } finally {
            resumePreparation.countDown()
            worker?.join(5000)
            f.close()
        }
    }
}
