package it.multipla.obd.core.connection

import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/** Esegue soltanto il pilota confermato, senza includere le feature di diagnosi in design. */
@RunWith(RequiredScenariosRunner::class)
@CucumberOptions(
    features = ["classpath:features/connection/automatic-retry.feature"],
    glue = ["it.multipla.obd.core.connection"],
    plugin = [
        "pretty",
        "summary",
        "json:build/reports/cucumber/automatic-retry.json",
        "html:build/reports/cucumber/automatic-retry.html",
        "it.multipla.obd.core.connection.ScenarioExecutionGuard",
    ],
    publish = false,
)
class AutomaticRetryTest
