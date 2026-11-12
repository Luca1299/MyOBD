package it.multipla.obd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionRegisterContentTest {
    @get:Rule val compose = createComposeRule()
    private var exports = 0

    private fun show(state: DiagnosticState) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(400.dp).verticalScroll(rememberScrollState())) {
                    SessionRegisterContent(state, { exports++ })
                }
            }
        }
    }

    @Test fun `register explains columns and keeps multiline exchanges and distinct export`() {
        val response = "41 0C 0C D0\r\n>"
        show(DiagnosticState(logs = listOf("14:32:03.120  TX  010C", "14:32:03.200  RX  $response")))
        listOf("Ora", "Tipo", "Contenuto", "Richiesta inviata", "Risposta ricevuta", "010C", response).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Fino a 120 voci a schermo · fino a 400 nel rapporto").assertExists()
        assertEquals(0, exports)
        compose.onNodeWithText("Esporta rapporto della sessione .txt").performClick()
        assertEquals(1, exports)
    }

    @Test fun `empty register gives a next step without an exportable report`() {
        show(DiagnosticState())
        compose.onNodeWithText("Avvia una sessione per visualizzare gli scambi.").assertExists()
        compose.onNodeWithText("Esporta rapporto della sessione .txt").assertIsNotEnabled()
    }

    @Test fun `busy session keeps export unavailable`() {
        show(DiagnosticState(busy = true, logs = listOf("14:32:03.120  NOTA Evento")))
        compose.onNodeWithText("Esporta rapporto della sessione .txt").assertIsNotEnabled()
    }
}
