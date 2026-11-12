package it.multipla.obd.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import io.cucumber.core.options.CommandlineOptionsParser
import io.cucumber.core.runtime.Runtime
import io.cucumber.plugin.EventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.TestCaseFinished
import io.cucumber.plugin.event.TestCaseStarted
import io.cucumber.plugin.event.TestRunFinished
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Esegue gli step Cucumber nello stesso ambiente Android dei componenti Compose reali. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresentationScenariosTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `confirmed presentation scenarios exercise the Android components`() {
        val harness = PresentationHarness(compose)
        PresentationWorld.attach(harness)
        try {
            compose.setContent { MaterialTheme { harness.Content() } }
            val guard = PresentationExecutionGuard()
            val options = CommandlineOptionsParser(System.out).parse(
                "--glue", "it.multipla.obd.presentation",
                "--plugin", "summary",
                "--plugin", "json:build/reports/cucumber/presentation.json",
                "--plugin", "html:build/reports/cucumber/presentation.html",
                "classpath:features/presentation/diagnostic-interface.feature",
            ).setThreads(1).setPublish(false).setPublishQuiet(true).build()
            val runtime = Runtime.builder()
                .withRuntimeOptions(options)
                .withClassLoader { javaClass.classLoader }
                .withAdditionalPlugins(guard)
                .build()
            runtime.run()
            guard.verifyComplete()
            assertEquals("Scenari di presentazione Cucumber falliti", 0, runtime.exitStatus().toInt())
        } finally {
            PresentationWorld.detach()
        }
    }
}

/** Rifiuta scoperta vuota, filtri involontari e scenari non conclusi della revisione R1. */
private class PresentationExecutionGuard : EventListener {
    private val expected = setOf("@UI-R01", "@UI-R02", "@UI-R03", "@UI-P01", "@UI-C01", "@UI-D01", "@UI-N01")
    private val started = mutableListOf<String>()
    private var finished = 0
    private var runFinished = false

    override fun setEventPublisher(publisher: EventPublisher) {
        publisher.registerHandlerFor(TestCaseStarted::class.java) { event ->
            val identifier = event.testCase.tags.single { it in expected }
            started += identifier
        }
        publisher.registerHandlerFor(TestCaseFinished::class.java) { finished++ }
        publisher.registerHandlerFor(TestRunFinished::class.java) { runFinished = true }
    }

    fun verifyComplete() {
        check(runFinished) { "Esecuzione Cucumber di presentazione non conclusa" }
        check(started.toSet() == expected && started.size == expected.size) {
            "Scoperta Cucumber incompleta: attesi $expected, avviati $started"
        }
        check(finished == started.size) { "Scenari conclusi: $finished su ${started.size}" }
    }
}
