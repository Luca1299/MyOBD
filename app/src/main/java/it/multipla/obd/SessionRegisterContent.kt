package it.multipla.obd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val LARGE_REGISTER_FONT_SCALE = 1.3f
private val minimumRegisterColumnsWidth = 340.dp
private val registerTimeWidth = 96.dp
private val registerTypeWidth = 112.dp

/** Consulta gli scambi già acquisiti ed esporta il rapporto esistente della sessione. */
@Composable
internal fun SessionRegisterContent(state: DiagnosticState, onExport: () -> Unit) {
    Text(stringResource(R.string.register_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.register_help))
    OutlinedButton(shape = actionShape, onClick = onExport, enabled = !state.busy && state.logs.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.register_export)) }
    Text(stringResource(R.string.register_capacity), style = MaterialTheme.typography.bodySmall)
    val entries = visibleSessionLogs(state.logs)
    if (entries.isEmpty()) {
        Text(stringResource(R.string.register_empty))
    } else {
        SessionRegisterTable(entries)
    }
    Text(stringResource(R.string.register_report_help), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SessionRegisterTable(entries: List<SessionLogEntry>) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < minimumRegisterColumnsWidth || fontScale >= LARGE_REGISTER_FONT_SCALE
        SelectionContainer {
            Column {
                RegisterHeaders(compact)
                HorizontalDivider()
                entries.forEach { entry ->
                    if (compact) CompactRegisterEntry(entry) else RegisterColumns(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RegisterHeaders(compact: Boolean) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.register_time_column), Modifier.width(registerTimeWidth), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.register_type_column), if (compact) Modifier.weight(1f) else Modifier.width(registerTypeWidth), style = MaterialTheme.typography.labelMedium)
            if (!compact) Text(stringResource(R.string.register_content_column), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        }
        if (compact) Text(stringResource(R.string.register_content_column), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RegisterColumns(entry: SessionLogEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RegisterTime(entry, Modifier.width(registerTimeWidth))
        Column(Modifier.width(registerTypeWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) { RegisterType(entry) }
        Text(entry.content, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CompactRegisterEntry(entry: SessionLogEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalDensity.current.fontScale >= LARGE_REGISTER_FONT_SCALE) {
            RegisterTime(entry)
            RegisterType(entry)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RegisterTime(entry, Modifier.width(registerTimeWidth))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { RegisterType(entry) }
            }
        }
        Text(entry.content, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RegisterTime(entry: SessionLogEntry, modifier: Modifier = Modifier) {
    Text(entry.time ?: stringResource(R.string.register_unknown_time), modifier, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.sp), fontFamily = FontFamily.Monospace)
}

@Composable
private fun RegisterType(entry: SessionLogEntry) {
    Text(stringResource(entry.type.labelResource), style = MaterialTheme.typography.labelMedium, color = if (entry.type == SessionLogType.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    entry.prefix?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
}
