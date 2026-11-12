package it.multipla.obd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.Reading

private const val SHORT_FUEL_TRIM_PID = 0x06
private const val LONG_FUEL_TRIM_PID = 0x07
private const val LARGE_PARAMETER_FONT_SCALE = 1.3f
private val minimumTwoColumnWidth = 320.dp

/** Raggruppamento visivo: ogni valore conserva identificatore, etichetta, precisione e unità. */
internal data class ParameterReadingGroups(val primary: List<Reading>, val corrections: List<Reading>)

internal fun groupParameterReadings(readings: List<Reading>): ParameterReadingGroups {
    val (corrections, primary) = readings.partition { it.pid == SHORT_FUEL_TRIM_PID || it.pid == LONG_FUEL_TRIM_PID }
    return ParameterReadingGroups(primary, corrections)
}

/** Campione manuale esistente, senza effetti alla composizione o acquisizioni periodiche. */
@Composable
internal fun ParametersContent(state: DiagnosticState, onRead: () -> Unit, onFuel: (String) -> Unit) {
    Text(stringResource(R.string.parameters_sample_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.parameters_sample_help))
    InfoCard {
        Text(stringResource(R.string.parameters_fuel_annotation), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(R.string.fuel_gasoline, R.string.fuel_methane).forEach { resource ->
                val fuel = stringResource(resource)
                FilterChip(selected = state.fuel == fuel, onClick = { onFuel(fuel) }, label = { Text(fuel) }, enabled = !state.busy)
            }
        }
        Button(shape = actionShape, onClick = onRead, enabled = state.info != null && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.parameters_read)) }
        if (state.info == null) Text(stringResource(R.string.parameters_prerequisite), style = MaterialTheme.typography.bodySmall)
    }
    state.sampleTime?.let { Text(stringResource(R.string.parameters_sample_time, it, state.fuel), style = MaterialTheme.typography.bodySmall) }
    state.engine?.let { engine ->
        InfoCard(warning = engine.mil) {
            LabelValueRow(stringResource(R.string.parameters_lamp_title), stringResource(if (engine.mil) R.string.parameters_lamp_on else R.string.parameters_lamp_off))
            Text(stringResource(R.string.confirmed_count, engine.confirmedCount))
        }
    }
    if (state.readings.isEmpty() && !state.busy && state.sampleTime == null) Text(stringResource(R.string.parameters_empty))
    ParameterReadings(state.readings)
    if (state.problems.isNotEmpty()) {
        InfoCard {
            Text(stringResource(R.string.parameters_problems_title), style = MaterialTheme.typography.titleSmall)
            state.problems.forEach { Text(it) }
        }
    }
    state.info?.let { info ->
        val unavailable = Parameters.all.filter { it.pid !in info.supported }
        if (unavailable.isNotEmpty()) Text(stringResource(R.string.parameters_unavailable, unavailable.joinToString { it.label }), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.parameters_sample_retention), style = MaterialTheme.typography.bodySmall)
}

/** Griglia condivisa fra campione manuale, dati attuali e freeze frame della diagnosi. */
@Composable
internal fun ParameterReadings(readings: List<Reading>) {
    if (readings.isEmpty()) return
    val groups = groupParameterReadings(readings)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth < minimumTwoColumnWidth || fontScale >= LARGE_PARAMETER_FONT_SCALE) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.primary.chunked(columnCount).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { reading ->
                        Box(Modifier.weight(1f)) { ParameterValueCard(reading, reserveLabelLines = row.size > 1) }
                    }
                }
            }
            if (groups.corrections.isNotEmpty()) {
                Text(stringResource(R.string.parameters_corrections_title), style = MaterialTheme.typography.titleSmall)
                groups.corrections.forEach { reading ->
                    ParameterCorrectionRow(reading)
                }
            }
        }
    }
}

@Composable
private fun ParameterValueCard(reading: Reading, reserveLabelLines: Boolean) {
    InfoCard {
        Text(reading.label, style = MaterialTheme.typography.bodyMedium, minLines = if (reserveLabelLines) 2 else 1)
        ParameterDisplay(reading.display)
    }
}

@Composable
private fun ParameterCorrectionRow(reading: Reading) {
    val largeText = LocalDensity.current.fontScale >= LARGE_PARAMETER_FONT_SCALE
    InfoCard {
        if (largeText) {
            Text(reading.label, style = MaterialTheme.typography.bodyMedium)
            ParameterDisplay(reading.display)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(reading.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                ParameterDisplay(reading.display)
            }
        }
    }
}

@Composable
private fun ParameterDisplay(display: String) {
    val unitStart = display.lastIndexOf(' ')
    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        if (unitStart < 0) {
            append(display)
        } else {
            append(display.substring(0, unitStart))
            withStyle(SpanStyle(fontSize = 12.sp, color = unitColor)) { append(display.substring(unitStart)) }
        }
    }
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
}
