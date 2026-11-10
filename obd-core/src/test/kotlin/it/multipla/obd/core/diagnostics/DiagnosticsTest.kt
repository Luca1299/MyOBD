package it.multipla.obd.core.diagnostics

import io.cucumber.junit.CucumberOptions
import it.multipla.obd.core.connection.RequiredScenariosRunner
import org.junit.runner.RunWith

/** Scenari confermati del protocollo diagnostico, con scoperta obbligatoria. */
@RunWith(RequiredScenariosRunner::class)
@CucumberOptions(
    features = ["classpath:features/diagnostics"],
    glue = ["it.multipla.obd.core.diagnostics"],
    plugin = ["summary", "json:build/reports/cucumber/diagnostics.json", "html:build/reports/cucumber/diagnostics.html", "it.multipla.obd.core.connection.ScenarioExecutionGuard"],
    publish = false,
)
class DiagnosticsTest
