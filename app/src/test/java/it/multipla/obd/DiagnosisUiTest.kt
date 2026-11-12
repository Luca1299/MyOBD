package it.multipla.obd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.SectionState
import it.multipla.obd.core.TroubleCode
import it.multipla.obd.core.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosisUiTest {
    @get:Rule val compose = createComposeRule()
    private var starts = 0
    private var interruptions = 0
    private var exports = 0
    private fun show(state: DiagnosticState) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiagnosisContent(state, { starts++ }, { interruptions++ }, {}, { exports++ })
                }
            }
        }
    }

    @Test fun `missing connection explains prerequisite and disables start`() {
        show(DiagnosticState())
        compose.onNodeWithText("Diagnosi non avviata").assertExists()
        compose.onNodeWithText("Verifica la centralina dalla scheda Collegamento.").assertExists()
        compose.onNodeWithText("Avvia diagnosi").assertIsNotEnabled()
    }

    @Test fun `ready diagnosis starts only from its main action`() {
        show(DiagnosticState(info = VehicleInfo("adapter", "ISO", false, emptySet())))
        compose.onNodeWithText("Avvia diagnosi").performClick()
        assertEquals(1, starts)
    }

    @Test fun `active diagnosis exposes interruption and locks annotation`() {
        val d = Diagnosis(1, DiagnosisContext(false, "OBDII", "ELM", "ISO", "Non annotata", Instant.EPOCH))
        show(DiagnosticState(busy = true, diagnosis = d))
        compose.onNodeWithText("Benzina").assertIsNotEnabled()
        compose.onNodeWithText("Interrompi diagnosi").performClick()
        assertEquals(1, interruptions)
        compose.onNodeWithText("Esporta rapporto della diagnosi .txt").assertDoesNotExist()
    }

    @Test fun `pending interruption disables action and explains the wait`() {
        val d = Diagnosis(1, DiagnosisContext(false, "OBDII", "ELM", "ISO", "Non annotata", Instant.EPOCH), phase = DiagnosisPhase.STOPPING)
        show(DiagnosticState(busy = true, diagnosis = d))
        compose.onNodeWithText("Interruzione in corso…").assertExists()
        compose.onNodeWithText("Interrompi diagnosi").assertIsNotEnabled()
    }

    @Test fun `ended partial acquisition preserves limitations and export action`() {
        val d = Diagnosis(1, DiagnosisContext(true, "OBDII", "ELM", "ISO", "Metano", Instant.EPOCH), phase = DiagnosisPhase.ENDED, outcome = DiagnosisOutcome.PARTIAL, endedAt = Instant.EPOCH, reason = "Risposta incompleta")
        show(DiagnosticState(demo = true, diagnosis = d))
        compose.onNodeWithText("Parziale").assertExists()
        compose.onNodeWithText("Risposta incompleta").assertExists()
        compose.onNodeWithText("Esporta rapporto della diagnosi .txt").performScrollTo().performClick()
        assertEquals(1, exports)
    }

    @Test fun `empty code category has an explanation distinct from an inconclusive reading`() {
        val codes = DtcKind.entries.associateWith { kind ->
            when (kind) {
                DtcKind.PENDING -> DiagnosticSection<List<TroubleCode>>(state = SectionState.EMPTY, value = emptyList())
                DtcKind.PERMANENT -> DiagnosticSection(state = SectionState.INCONCLUSIVE, problems = listOf("Nessun dato ricevuto"))
                else -> DiagnosticSection()
            }
        }
        val diagnosis = Diagnosis(
            1,
            DiagnosisContext(true, "OBDII", "ELM", "ISO", "Metano", Instant.EPOCH),
            phase = DiagnosisPhase.ENDED,
            outcome = DiagnosisOutcome.PARTIAL,
            endedAt = Instant.EPOCH,
            codes = codes,
        )
        show(DiagnosticState(demo = true, diagnosis = diagnosis))
        compose.onNodeWithText("Nessun codice restituito").assertExists()
        compose.onNodeWithText("Lettura non conclusiva").assertExists()
        compose.onNodeWithText("Nessun dato ricevuto").assertExists()
    }
}
