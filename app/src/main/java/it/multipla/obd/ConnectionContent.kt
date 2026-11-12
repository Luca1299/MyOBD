package it.multipla.obd

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.multipla.obd.core.ConnectionPhase

private const val ADDRESS_SUFFIX_LENGTH = 8

/** Dispositivo già associato al telefono, selezionabile senza modificare la sessione. */
internal data class PairedDevice(val name: String, val address: String)

/** Azioni della schermata adattate dall'Activity ai proprietari esistenti. */
internal data class ConnectionActions(
    val onBluetoothSettings: () -> Unit,
    val onPermissions: () -> Unit,
    val onNotifications: () -> Unit,
    val onSelect: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onSave: () -> Unit,
    val onManualConnect: () -> Unit,
    val onRecheckVehicle: () -> Unit,
    val onDemo: () -> Unit,
    val onExitDemo: () -> Unit,
    val onDisconnect: () -> Unit,
)

/** Presenta il collegamento corrente e inoltra soltanto le azioni esplicite. */
@Composable
internal fun ConnectionContent(state: DiagnosticState, paired: List<PairedDevice>, selected: String, permission: Boolean, notifications: Boolean, actions: ConnectionActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.connection_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ConnectionStatusCard(state, actions.onRecheckVehicle)
        ConnectionPreparationCard(state.busy, permission, notifications, actions)
        if (permission) ConnectionDevicesCard(state, paired, selected, actions)
        if (state.demo) {
            OutlinedButton(shape = actionShape, onClick = actions.onExitDemo, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_exit_demo)) }
        } else {
            OutlinedButton(shape = actionShape, onClick = actions.onDemo, enabled = !state.busy && !state.connected, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_enter_demo)) }
        }
        if (state.busy || state.connected) {
            OutlinedButton(shape = actionShape, onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_disconnect)) }
        }
        Text(stringResource(R.string.connection_continuity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.connection_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionStatusCard(state: DiagnosticState, onRecheckVehicle: () -> Unit) {
    InfoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.connection_session_status), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            StatusBadge(stringResource(state.connection.phase.badgeResource), warning = state.connection.phase != ConnectionPhase.VEHICLE_READY)
        }
        LabelValueRow(stringResource(R.string.connection_adapter_label), stringResource(state.connection.phase.adapterResource))
        LabelValueRow(stringResource(R.string.connection_vehicle_label), stringResource(state.connection.phase.vehicleResource))
        if (state.connected) {
            LabelValueRow(stringResource(R.string.connection_identification), state.connection.adapter.orEmpty())
            state.info?.let { info ->
                LabelValueRow(stringResource(R.string.connection_protocol), info.protocol)
                Text(pluralStringResource(R.plurals.connection_supported_parameters, info.supported.size, info.supported.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.connection_vehicle_identity_limit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.connection.phase == ConnectionPhase.ADAPTER_READY) {
                OutlinedButton(shape = actionShape, onClick = onRecheckVehicle) { Text(stringResource(R.string.connection_recheck)) }
            }
        }
        state.connection.detail?.let { Text(it, color = warningColor, style = MaterialTheme.typography.bodyMedium) }
        if (state.connection.phase == ConnectionPhase.MANUAL_REQUIRED) {
            Text(stringResource(R.string.connection_manual_help), style = MaterialTheme.typography.bodyMedium, color = warningColor)
        }
    }
}

@Composable
private fun ConnectionPreparationCard(busy: Boolean, permission: Boolean, notifications: Boolean, actions: ConnectionActions) {
    InfoCard {
        Text(stringResource(R.string.connection_first_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.connection_first_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.connection_automatic_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(shape = actionShape, onClick = actions.onBluetoothSettings, enabled = !busy) { Text(stringResource(R.string.connection_bluetooth_settings)) }
        if (!permission) {
            Button(shape = actionShape, onClick = actions.onPermissions, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_request_permissions)) }
        } else if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(shape = actionShape, onClick = actions.onNotifications) { Text(stringResource(R.string.connection_request_notifications)) }
        }
    }
}

@Composable
private fun ConnectionDevicesCard(state: DiagnosticState, paired: List<PairedDevice>, selected: String, actions: ConnectionActions) {
    InfoCard {
        Text(stringResource(R.string.connection_paired_devices), style = MaterialTheme.typography.titleMedium)
        if (paired.isEmpty()) Text(stringResource(R.string.connection_empty_devices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val selectable = !state.busy && !state.connected && !state.demo
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            paired.forEach { device ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = selected == device.address, enabled = selectable, role = Role.RadioButton, onClick = { actions.onSelect(device.address) }).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == device.address, onClick = null, enabled = selectable)
                    Column {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.connection_device_id, device.address.takeLast(ADDRESS_SUFFIX_LENGTH)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = actions.onRefresh, enabled = !state.busy) { Text(stringResource(R.string.connection_refresh_devices)) }
        Button(shape = actionShape, onClick = actions.onSave, enabled = selected.isNotBlank() && !state.busy && !state.connected && !state.demo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_save_device)) }
        if (state.connection.target != null && !state.connected && !state.demo) {
            Button(shape = actionShape, onClick = actions.onManualConnect, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_manual_connect)) }
        }
        if (state.demo) Text(stringResource(R.string.connection_demo_isolation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ConnectionPhase.badgeResource get() = when (this) {
    ConnectionPhase.VEHICLE_READY -> R.string.connection_ready
    ConnectionPhase.ADAPTER_READY -> R.string.connection_verification_needed
    ConnectionPhase.CONNECTING, ConnectionPhase.PROBING_VEHICLE -> R.string.connection_in_progress
    ConnectionPhase.RETRY_WAIT -> R.string.connection_waiting
    ConnectionPhase.IDLE, ConnectionPhase.NEEDS_SETUP, ConnectionPhase.MANUAL_REQUIRED, ConnectionPhase.CONNECTION_FAILED, ConnectionPhase.STOPPED -> R.string.connection_needed
}

private val ConnectionPhase.adapterResource get() = when (this) {
    ConnectionPhase.PROBING_VEHICLE, ConnectionPhase.ADAPTER_READY, ConnectionPhase.VEHICLE_READY -> R.string.connection_adapter_connected
    ConnectionPhase.CONNECTING -> R.string.connection_adapter_connecting
    ConnectionPhase.IDLE, ConnectionPhase.NEEDS_SETUP, ConnectionPhase.RETRY_WAIT, ConnectionPhase.MANUAL_REQUIRED, ConnectionPhase.CONNECTION_FAILED, ConnectionPhase.STOPPED -> R.string.connection_adapter_disconnected
}

private val ConnectionPhase.vehicleResource get() = when (this) {
    ConnectionPhase.VEHICLE_READY -> R.string.connection_vehicle_reached
    ConnectionPhase.PROBING_VEHICLE -> R.string.connection_vehicle_verifying
    ConnectionPhase.ADAPTER_READY -> R.string.connection_vehicle_unverified
    ConnectionPhase.IDLE, ConnectionPhase.NEEDS_SETUP, ConnectionPhase.CONNECTING, ConnectionPhase.RETRY_WAIT, ConnectionPhase.MANUAL_REQUIRED, ConnectionPhase.CONNECTION_FAILED, ConnectionPhase.STOPPED -> R.string.connection_vehicle_pending
}
