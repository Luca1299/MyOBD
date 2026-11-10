package it.multipla.obd.core.connection

import io.cucumber.plugin.EventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.TestCaseFinished
import io.cucumber.plugin.event.TestCaseStarted
import io.cucumber.plugin.event.TestRunFinished

/** Impedisce un successo apparente quando il runner non esegue alcuno scenario. */
class ScenarioExecutionGuard : EventListener {
    private var started = 0
    private var finished = 0

    override fun setEventPublisher(publisher: EventPublisher) {
        publisher.registerHandlerFor(TestCaseStarted::class.java) { started++ }
        publisher.registerHandlerFor(TestCaseFinished::class.java) { finished++ }
        publisher.registerHandlerFor(TestRunFinished::class.java) {
            check(started > 0) { "Nessuno scenario Cucumber eseguito: verificare feature e filtri" }
            check(finished == started) { "Scenari Cucumber incompleti: $finished conclusi su $started avviati" }
            println("Cucumber: $started scenari avviati, $finished conclusi")
        }
    }
}
