package it.multipla.obd.diagnostics

import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

/** Attraversa la sessione reale con trasporto e tempo controllati. */
@RunWith(RequiredScenariosRunner::class)
@CucumberOptions(
    features = ["classpath:features/diagnostics"],
    glue = ["it.multipla.obd.diagnostics"],
    plugin = ["summary", "json:build/reports/cucumber/diagnosis.json", "html:build/reports/cucumber/diagnosis.html", "it.multipla.obd.diagnostics.ScenarioExecutionGuard"],
    publish = false,
)
class DiagnosisTest
