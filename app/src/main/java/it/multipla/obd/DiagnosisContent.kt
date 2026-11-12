package it.multipla.obd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.MonitorId
import it.multipla.obd.core.MonitorState
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.SectionState
import it.multipla.obd.core.TroubleCode

/** Presenta risultati e azioni della sessione esistente, senza avviare effetti propri. */
@Composable
internal fun DiagnosisContent(state: DiagnosticState, onStart: () -> Unit, onInterrupt: () -> Unit, onFuel: (String) -> Unit, onExport: () -> Unit) {
    val diagnosis = state.diagnosis
    val running = diagnosis != null && diagnosis.phase != DiagnosisPhase.ENDED
    Text(stringResource(R.string.diagnosis_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(if (state.demo) R.string.diagnosis_demo else R.string.diagnosis_real), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.diagnosis_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.fuel_annotation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.fuel_unannotated, R.string.fuel_gasoline, R.string.fuel_methane).forEach { resource ->
            val fuel = stringResource(resource)
            FilterChip(selected = state.fuel == fuel, onClick = { onFuel(fuel) }, label = { Text(fuel) }, enabled = !state.busy)
        }
    }
    if (running) {
        InfoCard {
            Text(stringResource(if (diagnosis.phase == DiagnosisPhase.STOPPING) R.string.diagnosis_stopping else R.string.diagnosis_acquiring), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(diagnosis.stage.resource))
            OutlinedButton(shape = actionShape, onClick = onInterrupt, enabled = diagnosis.phase == DiagnosisPhase.ACQUIRING) { Text(stringResource(R.string.diagnosis_interrupt)) }
            Text(stringResource(R.string.diagnosis_stop_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Button(shape = actionShape, onClick = onStart, enabled = state.info != null && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.diagnosis_start)) }
        if (state.info == null) Text(stringResource(R.string.diagnosis_prerequisite), style = MaterialTheme.typography.bodySmall)
    }
    if (diagnosis == null) {
        Text(stringResource(R.string.diagnosis_not_started), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    InfoCard {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.diagnosis_summary), style = MaterialTheme.typography.titleMedium)
            diagnosis.outcome?.let { StatusBadge(stringResource(it.resource), warning = it != DiagnosisOutcome.COMPLETED) }
        }
        if (!running && !diagnosis.hasAcquiredData) Text(stringResource(R.string.diagnosis_no_data))
        diagnosis.reason?.let { Text(it, color = warningColor) }
        LabelValueRow(stringResource(R.string.ui_context_attempt), stringResource(R.string.ui_context_attempt_value, diagnosis.id))
        LabelValueRow(stringResource(R.string.ui_context_mode), stringResource(if (diagnosis.context.demo) R.string.diagnosis_demo else R.string.diagnosis_real))
        LabelValueRow(stringResource(R.string.ui_context_fuel), diagnosis.context.fuel)
        LabelValueRow(stringResource(R.string.ui_context_adapter), diagnosis.context.adapterName + " · " + diagnosis.context.adapter)
        LabelValueRow(stringResource(R.string.ui_context_protocol), diagnosis.context.protocol)
        LabelValueRow(stringResource(R.string.ui_context_started), acquisitionTime(diagnosis.context.startedAt))
        diagnosis.endedAt?.let { LabelValueRow(stringResource(R.string.ui_context_ended), stringResource(R.string.ui_context_ended_value, acquisitionTime(it), diagnosis.duration?.toMillis() ?: 0)) }
        Text(stringResource(R.string.diagnosis_sequential), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DtcKind.entries.forEach { kind ->
        val codes = diagnosis.codes.getValue(kind)
        DiagnosticSectionCard(stringResource(kind.resource), codes, R.string.ui_codes_empty) {
            codes.value.orEmpty().forEach { code ->
                Text(code.code, style = MaterialTheme.typography.headlineSmall, color = warningColor)
                Text(code.description ?: stringResource(R.string.description_unavailable))
                code.source?.let { DescriptionDetails(code) }
            }
        }
    }
    Text(stringResource(R.string.diagnosis_provenance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DiagnosticSectionCard(stringResource(R.string.readiness_title), diagnosis.readiness) {
        Text(stringResource(R.string.readiness_scope), style = MaterialTheme.typography.bodySmall)
        diagnosis.readiness.value?.let { readiness ->
            Text(stringResource(if (readiness.engine.mil) R.string.lamp_on else R.string.lamp_off), color = if (readiness.engine.mil) warningColor else MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.confirmed_count, readiness.engine.confirmedCount))
            readiness.monitors.forEach { monitor -> LabelValueRow(stringResource(monitor.id.resource), stringResource(monitor.state.resource)) }
        }
    }
    DiagnosticSectionCard(stringResource(R.string.freeze_title), diagnosis.freezeFrame, R.string.ui_frame_empty) {
        diagnosis.freezeFrame.value?.let { frame ->
            frame.code?.let { Text(stringResource(R.string.freeze_code, it)) }
            ParameterReadings(frame.readings)
            Text(stringResource(R.string.freeze_provenance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.freeze_time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val unavailable = Parameters.all.filter { it.pid !in frame.supported }
            if (frame.supported.isNotEmpty() && unavailable.isNotEmpty()) Text(stringResource(R.string.parameters_unavailable, unavailable.joinToString { it.label }), style = MaterialTheme.typography.bodySmall)
        }
    }
    DiagnosticSectionCard(stringResource(R.string.current_title), diagnosis.parameters) {
        diagnosis.parameters.value?.let { sample ->
            ParameterReadings(sample.readings)
            val unavailable = Parameters.all.filter { it.pid in sample.unsupportedPids }
            if (unavailable.isNotEmpty()) Text(stringResource(R.string.parameters_unavailable, unavailable.joinToString { it.label }), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(stringResource(R.string.diagnosis_interpretation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.diagnosis_retention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!running) OutlinedButton(shape = actionShape, onClick = onExport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_export)) }
}

/** Titolo, esito, tempo e limiti mantengono lo stesso ordine in tutte le sezioni. */
@Composable
private fun DiagnosticSectionCard(title: String, section: DiagnosticSection<*>, emptyResource: Int = R.string.section_empty, content: @Composable () -> Unit) {
    InfoCard {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            StatusBadge(
                stringResource(if (section.state == SectionState.EMPTY) emptyResource else section.state.resource),
                warning = section.state == SectionState.INCONCLUSIVE || section.state == SectionState.INTERRUPTED,
                neutral = section.state == SectionState.NOT_PERFORMED || section.state == SectionState.UNSUPPORTED,
            )
        }
        section.readAt?.let { Text(stringResource(R.string.section_read_time, acquisitionTime(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        section.problems.forEach { Text(it, color = warningColor) }
        content()
    }
}

/** Mantiene consultabile la provenienza completa senza appesantire l'elenco dei codici. */
@Composable
private fun DescriptionDetails(code: TroubleCode) {
    var expanded by rememberSaveable(code.code, code.source, code.version) { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.ui_details_expanded else R.string.ui_details_collapsed)
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = state }) { Text(stringResource(R.string.ui_description_details)) }
    if (expanded) Text(stringResource(R.string.description_source, code.source.orEmpty(), code.version.orEmpty()), style = MaterialTheme.typography.bodySmall)
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
