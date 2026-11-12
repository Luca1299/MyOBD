package it.multipla.obd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.ConnectionState
import it.multipla.obd.core.SavedTarget
import it.multipla.obd.core.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifica azioni e prerequisiti della schermata reale, senza operazioni Bluetooth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionContentTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<String>()
    private val device = PairedDevice("OBDII", "00:11:22:33:44:55")
    private val actions = ConnectionActions(
        onBluetoothSettings = { events += "settings" },
        onPermissions = { events += "permissions" },
        onNotifications = { events += "notifications" },
        onSelect = { events += "select:$it" },
        onRefresh = { events += "refresh" },
        onSave = { events += "save" },
        onManualConnect = { events += "manual" },
        onRecheckVehicle = { events += "verify" },
        onDemo = { events += "demo" },
        onExitDemo = { events += "exit-demo" },
        onDisconnect = { events += "disconnect" },
    )

    private fun state(phase: ConnectionPhase, busy: Boolean = false, demo: Boolean = false, info: VehicleInfo? = null): DiagnosticState {
        val connection = ConnectionState(phase = phase, target = SavedTarget(device.name, device.address), adapter = "ELM327", info = info)
        return DiagnosticState(connection = connection, busy = busy || connection.busy, demo = demo, info = info)
    }

    private fun show(state: DiagnosticState, permission: Boolean = true, notifications: Boolean = true, paired: List<PairedDevice> = listOf(device), selected: String = device.address) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ConnectionContent(state, paired, selected, permission, notifications, actions)
                }
            }
        }
        compose.waitForIdle()
        assertTrue(events.isEmpty())
    }

    @Test fun `adapter connection is distinct from an unverified vehicle`() {
        show(state(ConnectionPhase.ADAPTER_READY))
        compose.onNodeWithText("Adattatore").assertExists()
        compose.onNodeWithText("Collegato").assertExists()
        compose.onNodeWithText("Centralina OBD").assertExists()
        compose.onNodeWithText("Non verificata").assertExists()
        compose.onNodeWithText("Riprova verifica centralina").performScrollTo().performClick()
        assertEquals(listOf("verify"), events)
        compose.onNodeWithText("Salva dispositivo OBDII").assertIsNotEnabled()
    }

    @Test fun `verified vehicle exposes received protocol without another verification action`() {
        val info = VehicleInfo("ELM327", "ISO 9141-2", false, setOf(1, 5, 12))
        show(state(ConnectionPhase.VEHICLE_READY, info = info))
        compose.onNodeWithText("Raggiunta").assertExists()
        compose.onNodeWithText("ISO 9141-2").assertExists()
        compose.onNodeWithText("3 identificatori di parametro (PID) rilevati nelle pagine interrogate.").assertExists()
        compose.onNodeWithText("Riprova verifica centralina").assertDoesNotExist()
    }

    @Test fun `missing permission keeps configuration actions explicit`() {
        show(state(ConnectionPhase.NEEDS_SETUP), permission = false)
        compose.onNodeWithText("Autorizza Bluetooth e notifiche").performScrollTo().performClick()
        compose.onNodeWithText("Impostazioni Bluetooth").performScrollTo().performClick()
        compose.onNodeWithText("Dispositivi già associati").assertDoesNotExist()
        compose.onNodeWithText("Salva dispositivo OBDII").assertDoesNotExist()
        compose.onNodeWithText("Connetti manualmente").assertDoesNotExist()
        assertEquals(listOf("permissions", "settings"), events)
    }

    @Test fun `idle saved profile invokes only the requested connection action`() {
        show(state(ConnectionPhase.IDLE))
        compose.onNode(isSelectable()).performScrollTo().performClick()
        compose.onNodeWithText("Aggiorna elenco").performScrollTo().performClick()
        compose.onNodeWithText("Salva dispositivo OBDII").performScrollTo().performClick()
        compose.onNodeWithText("Connetti manualmente").performScrollTo().performClick()
        compose.onNodeWithText("Prova con dati simulati").performScrollTo().performClick()
        assertEquals(listOf("select:${device.address}", "refresh", "save", "manual", "demo"), events)
    }

    @Test fun `busy connection locks setup but keeps explicit stop and notification consent`() {
        show(state(ConnectionPhase.CONNECTING), notifications = false)
        compose.onNode(isSelectable()).assertIsNotEnabled()
        listOf("Impostazioni Bluetooth", "Aggiorna elenco", "Salva dispositivo OBDII", "Connetti manualmente", "Prova con dati simulati").forEach {
            compose.onNodeWithText(it).assertIsNotEnabled()
        }
        compose.onNodeWithText("Consenti la notifica della sessione").assertIsEnabled().performScrollTo().performClick()
        compose.onNodeWithText("Scollega / annulla").performScrollTo().performClick()
        assertEquals(listOf("notifications", "disconnect"), events)
    }

    @Test fun `demo preserves real profile controls and exposes its exit action`() {
        show(state(ConnectionPhase.VEHICLE_READY, demo = true))
        compose.onNode(isSelectable()).assertIsNotEnabled()
        compose.onNodeWithText("Salva dispositivo OBDII").assertIsNotEnabled()
        compose.onNodeWithText("Connetti manualmente").assertDoesNotExist()
        compose.onNodeWithText("Prova con dati simulati").assertDoesNotExist()
        compose.onNodeWithText("Torna al dispositivo reale").performScrollTo().performClick()
        assertEquals(listOf("exit-demo"), events)
    }

    @Test fun `empty device list explains setup and preserves manual recovery`() {
        show(state(ConnectionPhase.MANUAL_REQUIRED), paired = emptyList(), selected = "")
        compose.onNodeWithText("Nessun dispositivo disponibile. Controlla Bluetooth e associazione.").assertExists()
        compose.onNodeWithText("Salva dispositivo OBDII").assertIsNotEnabled()
        compose.onNodeWithText("L’autoconnessione è sospesa. Premi Connetti manualmente: un collegamento riuscito la riabilita anche per le prossime aperture.").assertExists()
        compose.onNodeWithText("Connetti manualmente").performScrollTo().performClick()
        assertEquals(listOf("manual"), events)
    }

    @Test
    @Config(sdk = [31])
    fun `notification permission action is absent before Android thirteen`() {
        show(state(ConnectionPhase.IDLE), notifications = false)
        compose.onNodeWithText("Consenti la notifica della sessione").assertDoesNotExist()
    }
}
