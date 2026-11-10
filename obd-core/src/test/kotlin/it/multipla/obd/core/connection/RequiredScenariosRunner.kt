package it.multipla.obd.core.connection

import io.cucumber.junit.Cucumber
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.notification.RunNotifier

/**
 * Rifiuta una suite vuota prima che Gradle possa scartarla dalla scoperta.
 * Delega esecuzione e filtri a Cucumber, conservando il percorso JUnit 4.
 */
class RequiredScenariosRunner(testClass: Class<*>) :
    Runner(),
    Filterable {
    private val delegate = Cucumber(testClass)

    init {
        check(delegate.description.children.isNotEmpty()) {
            "Nessuno scenario Cucumber scoperto: verificare feature e filtri"
        }
    }

    override fun getDescription(): Description = delegate.description

    override fun run(notifier: RunNotifier) = delegate.run(notifier)

    override fun filter(filter: Filter) = delegate.filter(filter)
}
