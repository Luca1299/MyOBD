package it.multipla.obd.core.connection

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import it.multipla.obd.core.ConnectionController
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.MemoryConnectionStore
import it.multipla.obd.core.SavedTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.IOException

/** Verifica la politica del coordinatore reale con collegamenti falliti e tempo virtuale. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticRetrySteps {
    private val scope = TestScope()
    private val target = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")
    private val starts = mutableListOf<Long>()
    private val failures = mutableListOf<Long>()
    private lateinit var store: MemoryConnectionStore
    private lateinit var controller: ConnectionController

    @Given("a saved adapter with connection prerequisites satisfied")
    fun savedAdapter() {
        store = MemoryConnectionStore(target)
    }

    @Given("every connection attempt fails")
    fun failingConnections() {
        controller = ConnectionController(scope, store, { null }) { chosen ->
            assertEquals(target, chosen)
            starts += scope.testScheduler.currentTime
            delay(ATTEMPT_DURATION_MS)
            failures += scope.testScheduler.currentTime
            throw IOException("Adattatore simulato non raggiungibile")
        }
    }

    @When("the application opens")
    fun openApplication() {
        controller.appOpened()
        scope.testScheduler.advanceUntilIdle()
    }

    @Then("exactly three automatic connection attempts are made")
    fun threeAttempts() {
        assertEquals(3, starts.size)
        assertEquals(3, failures.size)
        assertEquals(0L, starts.first())
    }

    @Then("each retry starts three seconds after the previous failure")
    fun retryDelay() {
        assertEquals(listOf(RETRY_DELAY_MS, RETRY_DELAY_MS), starts.drop(1).zip(failures) { start, failure -> start - failure })
    }

    @Then("automatic connection is suspended")
    fun automaticConnectionSuspended() {
        assertEquals(ConnectionPhase.MANUAL_REQUIRED, controller.state.value.phase)
        assertTrue(store.manualRequired())
    }

    @Then("no further automatic connection attempt is made")
    fun noFurtherAttempt() {
        scope.testScheduler.advanceTimeBy(AFTER_SUSPENSION_MS)
        scope.testScheduler.runCurrent()
        assertEquals(3, starts.size)
        assertEquals(ConnectionPhase.MANUAL_REQUIRED, controller.state.value.phase)
    }

    @After
    fun closeScenario() {
        if (::controller.isInitialized) controller.close()
        scope.cancel()
    }

    private companion object {
        const val ATTEMPT_DURATION_MS = 500L
        const val RETRY_DELAY_MS = 3_000L
        const val AFTER_SUSPENSION_MS = 100_000L
    }
}
