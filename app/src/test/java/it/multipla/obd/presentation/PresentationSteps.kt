package it.multipla.obd.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import it.multipla.obd.ConnectionActions
import it.multipla.obd.ConnectionContent
import it.multipla.obd.Diagnosis
import it.multipla.obd.DiagnosisContent
import it.multipla.obd.DiagnosisContext
import it.multipla.obd.DiagnosisOutcome
import it.multipla.obd.DiagnosisPhase
import it.multipla.obd.DiagnosticScaffold
import it.multipla.obd.DiagnosticState
import it.multipla.obd.PairedDevice
import it.multipla.obd.ParametersContent
import it.multipla.obd.SessionLogEntry
import it.multipla.obd.SessionLogType
import it.multipla.obd.SessionRegisterContent
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.ConnectionState
import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.Reading
import it.multipla.obd.core.SavedTarget
import it.multipla.obd.core.SectionState
import it.multipla.obd.core.VehicleInfo
import it.multipla.obd.visibleSessionLogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant

/** Gli step montano i componenti applicativi; i callback rilevano soltanto le azioni esplicite. */
class PresentationSteps {
    private val harness get() = PresentationWorld.harness
    private val compose get() = harness.compose
    private var logs = emptyList<String>()
    private var rows = emptyList<SessionLogEntry>()
    private var state = DiagnosticState()
    private var measurements = emptyList<Reading>()
    private val actions = mutableListOf<String>()
    private val target = SavedTarget("OBDII", "AA:BB:CC:DD:EE:01")

    private fun connectionActions() = ConnectionActions(
        onBluetoothSettings = { actions += "settings" },
        onPermissions = { actions += "permissions" },
        onNotifications = { actions += "notifications" },
        onSelect = { actions += "select" },
        onRefresh = { actions += "refresh" },
        onSave = { actions += "save" },
        onManualConnect = { actions += "connect" },
        onRecheckVehicle = { actions += "verify" },
        onDemo = { actions += "demo" },
        onExitDemo = { actions += "exit-demo" },
        onDisconnect = { actions += "disconnect" },
    )

    @Given("session entries include timestamped requests and multiline responses")
    fun timestampedExchanges() {
        logs = listOf(
            "14:32:03.240  TX  010C",
            "14:32:03.320  RX  41 0C 0C D0\n>",
            "14:32:04.000  STATO Centralina OBD raggiunta",
        )
    }

    @Given("session entries include unknown prefixes and missing timestamps")
    fun unknownEntries() {
        logs = listOf("14:32:09.000  ESTENSIONE valore originale", "riga senza orario\ncontenuto originale")
    }

    @Given("a session contains more than one hundred and twenty retained entries")
    fun retainedEntries() {
        logs = (0 until 150).map { "14:32:00.000  NOTA voce-$it" }
    }

    @When("the session register is presented")
    fun showRegister() {
        rows = visibleSessionLogs(logs)
        harness.show { SessionRegisterContent(DiagnosticState(logs = logs), { actions += "session-export" }) }
    }

    @Then("entry times and explanatory event labels are distinct from their content")
    fun registerColumns() {
        for (title in listOf("Ora", "Tipo", "Contenuto", "Richiesta inviata", "Risposta ricevuta")) {
            compose.onNodeWithText(title, useUnmergedTree = true).assertExists()
        }
        assertEquals("14:32:03.240", rows[0].time)
        assertEquals(SessionLogType.REQUEST, rows[0].type)
        assertEquals(SessionLogType.RESPONSE, rows[1].type)
    }

    @Then("the original exchange content is preserved")
    fun originalExchanges() {
        assertEquals(logs, rows.map { it.original })
        assertEquals("010C", rows[0].content)
        assertEquals("41 0C 0C D0\n>", rows[1].content)
        compose.onNodeWithText(rows[0].content, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(rows[1].content, useUnmergedTree = true).assertExists()
    }

    @Then("unrecognised content remains available without an invented timestamp")
    fun preserveUnknownEntries() {
        assertEquals(SessionLogType.EVENT, rows[0].type)
        assertEquals("ESTENSIONE valore originale", rows[0].content)
        assertNull(rows[1].time)
        assertEquals(logs[1], rows[1].content)
        rows.forEach { compose.onNodeWithText(it.content, useUnmergedTree = true).assertExists() }
    }

    @Then("only the latest one hundred and twenty entries are selected in original order")
    fun visibleLimit() {
        assertEquals(logs.takeLast(120), rows.map { it.original })
        compose.onNodeWithText("voce-0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("voce-30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("voce-149", useUnmergedTree = true).assertExists()
    }

    @Then("the interface explains the screen and report limits separately")
    fun explainLimits() {
        compose.onNodeWithText("Fino a 120 voci a schermo · fino a 400 nel rapporto").assertExists()
        compose.onNodeWithText("Esporta rapporto della sessione .txt").assertExists()
    }

    @Given("one engine sample contains the seven supported measurements including zero speed")
    fun sample() {
        val bytes = mapOf(
            0x0C to listOf(0x0C, 0xD0),
            0x05 to listOf(0x80),
            0x0D to listOf(0),
            0x06 to listOf(0x82),
            0x07 to listOf(0x88),
            0x0B to listOf(0x22),
            0x11 to listOf(0x08),
        )
        measurements = Parameters.all.map { Reading(it.pid, it.label, it.decode(bytes.getValue(it.pid))) }
        val info = VehicleInfo("ELM327", "ISO", false, Parameters.all.map { it.pid }.toSet())
        state = DiagnosticState(readings = measurements, info = info, sampleTime = "14:32:04.120", fuel = "Metano")
        assertEquals(7, measurements.size)
    }

    @When("the engine sample is presented")
    fun showSample() {
        harness.show { ParametersContent(state, { actions += "read" }, { actions += "fuel" }) }
    }

    @Then("every acquired label value and unit remains available")
    fun measurementContents() {
        measurements.forEach {
            compose.onNodeWithText(it.label, useUnmergedTree = true).assertExists()
            compose.onNodeWithText(it.display, useUnmergedTree = true).assertExists()
        }
    }

    @Then("zero speed remains a valid measurement")
    fun validZero() {
        compose.onNodeWithText("0 km/h", useUnmergedTree = true).assertExists()
        assertTrue(actions.isEmpty())
    }

    @Given("the adapter is connected but vehicle verification is incomplete")
    fun adapterOnly() {
        state = DiagnosticState(connection = ConnectionState(phase = ConnectionPhase.ADAPTER_READY, target = target, adapter = "ELM327"))
    }

    @When("the connection is presented")
    fun showConnection() {
        harness.show { ConnectionContent(state, listOf(PairedDevice(target.name, target.address)), target.address, true, true, connectionActions()) }
    }

    @Then("adapter and vehicle verification states remain distinct")
    fun distinguishConnectionStates() {
        compose.onNodeWithText("Collegato", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Non verificata", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Raggiunta", useUnmergedTree = true).assertDoesNotExist()
    }

    @Then("vehicle verification remains an explicit action")
    fun explicitVerification() {
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("Riprova verifica centralina").performScrollTo().performClick()
        assertEquals(listOf("verify"), actions)
    }

    @Given("an ended diagnosis has empty pending codes and inconclusive permanent codes")
    fun incompleteDiagnosis() {
        val diagnosis = Diagnosis(
            1,
            DiagnosisContext(true, "OBDII", "ELM327", "ISO", "Metano", Instant.EPOCH),
            phase = DiagnosisPhase.ENDED,
            outcome = DiagnosisOutcome.PARTIAL,
            endedAt = Instant.EPOCH,
            codes = DtcKind.entries.associateWith { kind ->
                when (kind) {
                    DtcKind.PENDING -> DiagnosticSection(state = SectionState.EMPTY, value = emptyList(), readAt = Instant.EPOCH)
                    DtcKind.PERMANENT -> DiagnosticSection(state = SectionState.INCONCLUSIVE, problems = listOf("Nessun dato ricevuto per questa richiesta"))
                    else -> DiagnosticSection()
                }
            },
        )
        state = DiagnosticState(demo = true, diagnosis = diagnosis, logs = listOf("14:32:00.000  NOTA risultato conservato"))
    }

    @When("the diagnosis is presented")
    fun showDiagnosis() {
        harness.show { DiagnosisContent(state, { actions += "diagnosis" }, { actions += "interrupt" }, { actions += "fuel" }, { actions += "diagnosis-export" }) }
    }

    @Then("empty and inconclusive code results have distinct explanations")
    fun distinguishCodeResults() {
        compose.onNodeWithText("Nessun codice restituito", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Lettura non conclusiva", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Nessun dato ricevuto per questa richiesta", useUnmergedTree = true).assertExists()
    }

    @Then("the ended diagnosis export is distinct from the session export")
    fun distinguishExports() {
        compose.onNodeWithText("Esporta rapporto della diagnosi .txt").performScrollTo().performClick()
        assertEquals(listOf("diagnosis-export"), actions)
        harness.show { SessionRegisterContent(state, { actions += "session-export" }) }
        compose.onNodeWithText("Esporta rapporto della diagnosi .txt").assertDoesNotExist()
        compose.onNodeWithText("Esporta rapporto della sessione .txt").performClick()
        assertEquals(listOf("diagnosis-export", "session-export"), actions)
    }

    @Given("a ready presentation has received no manual acquisition request")
    fun readyPresentation() {
        val info = VehicleInfo("ELM327", "ISO", false, emptySet())
        state = DiagnosticState(
            phase = "Centralina OBD raggiunta",
            info = info,
            connection = ConnectionState(phase = ConnectionPhase.VEHICLE_READY, target = target, adapter = "ELM327", info = info),
        )
    }

    @When("all four sections are visited")
    fun navigateSections() {
        harness.show(scrolling = false) {
            var selected by remember { mutableIntStateOf(0) }
            DiagnosticScaffold(state, selected, { selected = it }) {
                when (selected) {
                    0 -> ConnectionContent(state, listOf(PairedDevice(target.name, target.address)), target.address, true, true, connectionActions())
                    1 -> ParametersContent(state, { actions += "read" }, { actions += "fuel" })
                    2 -> DiagnosisContent(state, { actions += "diagnosis" }, { actions += "interrupt" }, { actions += "fuel" }, { actions += "diagnosis-export" })
                    else -> SessionRegisterContent(state, { actions += "session-export" })
                }
            }
        }
        listOf("Collegamento", "Parametri", "Diagnosi", "Registro").forEach { label ->
            val tab = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(tab).performClick().assertIsSelected()
        }
    }

    @Then("navigation alone requests no reading diagnosis or export")
    fun noImplicitActions() {
        assertTrue("La navigazione ha invocato azioni: $actions", actions.isEmpty())
    }
}
