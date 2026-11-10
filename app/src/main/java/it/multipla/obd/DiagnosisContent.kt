package it.multipla.obd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.MonitorId
import it.multipla.obd.core.MonitorState
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.SectionState

/** Presentazione della sessione esistente: non avvia effetti o connessioni. */
@Composable
internal fun DiagnosisContent(state: DiagnosticState, onStart: () -> Unit, onInterrupt: () -> Unit, onFuel: (String) -> Unit, onExport: () -> Unit) {
    val diagnosis = state.diagnosis
    val running = diagnosis != null && diagnosis.phase != DiagnosisPhase.ENDED
    Text(stringResource(R.string.diagnosis_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(if (state.demo) R.string.diagnosis_demo else R.string.diagnosis_real))
    Text(stringResource(R.string.diagnosis_scope))
    Text(stringResource(R.string.fuel_annotation))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.fuel_unannotated, R.string.fuel_gasoline, R.string.fuel_methane).forEach { resource ->
            val fuel = stringResource(resource)
            FilterChip(selected = state.fuel == fuel, onClick = { onFuel(fuel) }, label = { Text(fuel) }, enabled = !state.busy)
        }
    }
    if (running) {
        Text(stringResource(if (diagnosis.phase == DiagnosisPhase.STOPPING) R.string.diagnosis_stopping else R.string.diagnosis_acquiring))
        Text(stringResource(diagnosis.stage.resource))
        OutlinedButton(onClick = onInterrupt, enabled = diagnosis.phase == DiagnosisPhase.ACQUIRING) { Text(stringResource(R.string.diagnosis_interrupt)) }
        Text(stringResource(R.string.diagnosis_stop_help))
    } else {
        Button(onClick = onStart, enabled = state.info != null && !state.busy) { Text(stringResource(R.string.diagnosis_start)) }
        if (state.info == null) Text(stringResource(R.string.diagnosis_prerequisite))
    }
    if (diagnosis == null) {
        Text(stringResource(R.string.diagnosis_not_started))
        return
    }
    InfoCard {
        Text(stringResource(R.string.diagnosis_summary), style = MaterialTheme.typography.titleMedium)
        diagnosis.outcome?.let { Text(stringResource(it.resource)) }
        if (!running && !diagnosis.hasAcquiredData) Text(stringResource(R.string.diagnosis_no_data))
        Text(stringResource(R.string.diagnosis_context, diagnosis.id, diagnosis.context.fuel))
        Text(stringResource(if (diagnosis.context.demo) R.string.diagnosis_demo else R.string.diagnosis_real))
        Text(stringResource(R.string.diagnosis_adapter, diagnosis.context.adapterName, diagnosis.context.adapter, diagnosis.context.protocol))
        Text(stringResource(R.string.diagnosis_started, diagnosis.context.startedAt.toString()))
        diagnosis.endedAt?.let { Text(stringResource(R.string.diagnosis_ended, it.toString(), diagnosis.duration?.toMillis() ?: 0)) }
        diagnosis.reason?.let { Text(it) }
        Text(stringResource(R.string.diagnosis_sequential), style = MaterialTheme.typography.bodySmall)
    }
    DtcKind.entries.forEach { kind ->
        val codes = diagnosis.codes.getValue(kind)
        DiagnosticSectionCard(stringResource(kind.resource), codes) {
            codes.value.orEmpty().forEach { code ->
                Text(code.code, style = MaterialTheme.typography.titleMedium)
                Text(code.description ?: stringResource(R.string.description_unavailable))
                code.source?.let { Text(stringResource(R.string.description_source, it, code.version.orEmpty()), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Text(stringResource(R.string.diagnosis_provenance), style = MaterialTheme.typography.bodySmall)
    DiagnosticSectionCard(stringResource(R.string.readiness_title), diagnosis.readiness) {
        Text(stringResource(R.string.readiness_scope))
        diagnosis.readiness.value?.let { readiness ->
            Text(stringResource(if (readiness.engine.mil) R.string.lamp_on else R.string.lamp_off))
            Text(stringResource(R.string.confirmed_count, readiness.engine.confirmedCount))
            readiness.monitors.forEach { monitor -> Text(stringResource(R.string.monitor_status, stringResource(monitor.id.resource), stringResource(monitor.state.resource))) }
        }
    }
    DiagnosticSectionCard(stringResource(R.string.freeze_title), diagnosis.freezeFrame) {
        diagnosis.freezeFrame.value?.let { frame ->
            frame.code?.let { Text(stringResource(R.string.freeze_code, it)) }
            frame.readings.forEach { Text(stringResource(R.string.measurement, it.label, it.display)) }
            Text(stringResource(R.string.freeze_provenance))
            Text(stringResource(R.string.freeze_time))
            val unavailable = Parameters.all.filter { it.pid !in frame.supported }
            if (frame.supported.isNotEmpty() && unavailable.isNotEmpty()) Text(stringResource(R.string.parameters_unavailable, unavailable.joinToString { it.label }))
        }
    }
    DiagnosticSectionCard(stringResource(R.string.current_title), diagnosis.parameters) {
        diagnosis.parameters.value?.let { sample ->
            sample.readings.forEach { Text(stringResource(R.string.measurement, it.label, it.display)) }
            val unavailable = Parameters.all.filter { it.pid in sample.unsupportedPids }
            if (unavailable.isNotEmpty()) Text(stringResource(R.string.parameters_unavailable, unavailable.joinToString { it.label }))
        }
    }
    Text(stringResource(R.string.diagnosis_interpretation), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.diagnosis_retention), style = MaterialTheme.typography.bodySmall)
    if (!running) OutlinedButton(onClick = onExport, enabled = !state.busy) { Text(stringResource(R.string.report_export)) }
}

/** Titolo, esito e limiti sono presentati nello stesso modo per tutte le sezioni. */
@Composable
private fun DiagnosticSectionCard(title: String, section: DiagnosticSection<*>, content: @Composable () -> Unit) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(section.state.resource))
            section.readAt?.let { Text(stringResource(R.string.section_read_time, it.toString()), style = MaterialTheme.typography.bodySmall) }
            section.problems.forEach { Text(it) }
            content()
        }
    }
}

private val DtcKind.resource get() = when (this) {
    DtcKind.STORED -> R.string.codes_stored
    DtcKind.PENDING -> R.string.codes_pending
    DtcKind.PERMANENT -> R.string.codes_permanent
}
private val DiagnosisOutcome.resource get() = when (this) {
    DiagnosisOutcome.COMPLETED -> R.string.outcome_completed
    DiagnosisOutcome.PARTIAL -> R.string.outcome_partial
    DiagnosisOutcome.INTERRUPTED -> R.string.outcome_interrupted
}
private val SectionState.resource get() = when (this) {
    SectionState.ACQUIRED -> R.string.section_acquired
    SectionState.EMPTY -> R.string.section_empty
    SectionState.UNSUPPORTED -> R.string.section_unsupported
    SectionState.INCONCLUSIVE -> R.string.section_inconclusive
    SectionState.NOT_PERFORMED -> R.string.section_not_performed
    SectionState.INTERRUPTED -> R.string.outcome_interrupted
}
private val DiagnosisStage.resource get() = when (this) {
    DiagnosisStage.READINESS -> R.string.readiness_title
    DiagnosisStage.STORED_CODES -> R.string.codes_stored
    DiagnosisStage.PENDING_CODES -> R.string.codes_pending
    DiagnosisStage.PERMANENT_CODES -> R.string.codes_permanent
    DiagnosisStage.FREEZE_FRAME -> R.string.freeze_title
    DiagnosisStage.CURRENT_PARAMETERS -> R.string.current_title
}
private val MonitorState.resource get() = when (this) {
    MonitorState.COMPLETED -> R.string.monitor_completed
    MonitorState.INCOMPLETE -> R.string.monitor_incomplete
    MonitorState.UNSUPPORTED -> R.string.monitor_unsupported
}
private val MonitorId.resource get() = when (this) {
    MonitorId.MISFIRE -> R.string.monitor_misfire
    MonitorId.FUEL_SYSTEM -> R.string.monitor_fuel
    MonitorId.COMPONENTS -> R.string.monitor_components
    MonitorId.CATALYST -> R.string.monitor_catalyst
    MonitorId.HEATED_CATALYST -> R.string.monitor_heated_catalyst
    MonitorId.EVAPORATIVE_SYSTEM -> R.string.monitor_evaporative
    MonitorId.SECONDARY_AIR -> R.string.monitor_secondary_air
    MonitorId.OXYGEN_SENSOR -> R.string.monitor_oxygen
    MonitorId.OXYGEN_HEATER -> R.string.monitor_oxygen_heater
    MonitorId.EGR -> R.string.monitor_egr
    MonitorId.NMHC_CATALYST -> R.string.monitor_nmhc
    MonitorId.NOX_SCR -> R.string.monitor_nox
    MonitorId.BOOST -> R.string.monitor_boost
    MonitorId.EXHAUST_GAS_SENSOR -> R.string.monitor_exhaust_sensor
    MonitorId.PARTICULATE_FILTER -> R.string.monitor_particulate
}
