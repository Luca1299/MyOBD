package it.multipla.obd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import it.multipla.obd.core.EngineStatus
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.Reading
import it.multipla.obd.core.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParametersContentTest {
    @get:Rule val compose = createComposeRule()
    private var reads = 0
    private val fuelChanges = mutableListOf<String>()
    private val readings = Parameters.all.zip(listOf("820.0 giri/min", "88 °C", "0 km/h", "1,6 %", "6,3 %", "34 kPa", "3,1 %")) { parameter, display -> Reading(parameter.pid, parameter.label, display) }
    private val info = VehicleInfo("ELM", "ISO", false, readings.map { it.pid }.toSet())

    private fun show(state: DiagnosticState, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        ParametersContent(state, { reads++ }, { fuelChanges += it })
                    }
                }
            }
        }
    }

    @Test fun `all sample labels and original displays remain available including valid zero`() {
        show(DiagnosticState(info = info, readings = readings, sampleTime = "14:32:04.120", fuel = "Metano"))
        readings.forEach { reading ->
            compose.onNodeWithText(reading.label).assertExists()
            compose.onNodeWithText(reading.display).assertExists()
        }
        compose.onNodeWithText("0 km/h").performScrollTo().assertIsDisplayed()
        assertEquals(0, reads)
        assertEquals(emptyList<String>(), fuelChanges)
    }

    @Test fun `missing prerequisite explains why the single read is unavailable`() {
        show(DiagnosticState())
        compose.onNodeWithText("Leggi parametri disponibili").assertIsNotEnabled()
        compose.onNodeWithText("Verifica prima la centralina dalla scheda Collegamento oppure avvia la demo.").assertExists()
        compose.onNodeWithText("Nessun campione acquisito.").assertExists()
        compose.onNodeWithText("0 km/h").assertDoesNotExist()
    }

    @Test fun `reading and annotation change only through their actions`() {
        show(DiagnosticState(info = info))
        compose.onNodeWithText("Leggi parametri disponibili").performClick()
        compose.onNodeWithText("Metano").performClick()
        assertEquals(1, reads)
        assertEquals(listOf("Metano"), fuelChanges)
    }

    @Test fun `busy state prevents another sample and fuel change`() {
        show(DiagnosticState(info = info, busy = true))
        compose.onNodeWithText("Leggi parametri disponibili").assertIsNotEnabled()
        compose.onNodeWithText("Benzina").assertIsNotEnabled()
        compose.onNodeWithText("Metano").assertIsNotEnabled()
    }

    @Test fun `sample context engine status and reading limitations remain explicit`() {
        val problem = "Temperatura refrigerante: Nessun dato ricevuto"
        show(DiagnosticState(info = info.copy(supported = setOf(readings.first().pid)), readings = readings.take(1), engine = EngineStatus(true, 1), sampleTime = "14:32:04.120", fuel = "Metano", problems = listOf(problem)))
        compose.onNodeWithText("Campione completato alle 14:32:04.120 · Metano").assertExists()
        compose.onNodeWithText("Richiesta accesa dalla centralina").assertExists()
        compose.onNodeWithText("Codici confermati dichiarati: 1").assertExists()
        compose.onNodeWithText(problem).assertExists()
        compose.onNodeWithText("Non dichiarati disponibili in questo contesto:", substring = true).assertExists()
    }

    @Test fun `large text keeps correction labels and values reachable at narrow width`() {
        show(DiagnosticState(info = info, readings = readings), fontScale = 2f)
        compose.onNodeWithText(readings.last { it.pid == 0x07 }.label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6,3 %").performScrollTo().assertIsDisplayed()
    }

    @Test fun `presentation groups preserve acquired readings while corrections occupy their own rows`() {
        val groups = groupParameterReadings(readings)
        assertEquals(readings.filter { it.pid !in setOf(0x06, 0x07) }, groups.primary)
        assertEquals(readings.filter { it.pid in setOf(0x06, 0x07) }, groups.corrections)
        assertEquals(readings.toSet(), (groups.primary + groups.corrections).toSet())
    }
}
