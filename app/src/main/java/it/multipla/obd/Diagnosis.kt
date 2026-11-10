package it.multipla.obd

import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.FreezeFrame
import it.multipla.obd.core.MonitorId
import it.multipla.obd.core.MonitorState
import it.multipla.obd.core.ParameterSample
import it.multipla.obd.core.Readiness
import it.multipla.obd.core.SectionState
import it.multipla.obd.core.TroubleCode
import java.time.Duration
import java.time.Instant

/** Il contesto viene fissato all'avvio e non segue le successive modifiche della schermata. */
data class DiagnosisContext(val demo: Boolean, val adapterName: String, val adapter: String, val protocol: String, val fuel: String, val startedAt: Instant)

/** Ciclo di vita dell’acquisizione indipendente dalla schermata. */
enum class DiagnosisPhase { ACQUIRING, STOPPING, ENDED }

/** Qualità dell’acquisizione conclusa, senza giudizi sulla vettura. */
enum class DiagnosisOutcome { COMPLETED, PARTIAL, INTERRUPTED }

/** Sezione che possiede la richiesta diagnostica corrente. */
enum class DiagnosisStage { READINESS, STORED_CODES, PENDING_CODES, PERMANENT_CODES, FREEZE_FRAME, CURRENT_PARAMETERS }

/** Un solo tentativo in memoria; le sezioni non raggiunte non ereditano i dati precedenti. */
data class Diagnosis(
    val id: Long,
    val context: DiagnosisContext,
    val phase: DiagnosisPhase = DiagnosisPhase.ACQUIRING,
    val stage: DiagnosisStage = DiagnosisStage.READINESS,
    val outcome: DiagnosisOutcome? = null,
    val endedAt: Instant? = null,
    val reason: String? = null,
    val readiness: DiagnosticSection<Readiness> = DiagnosticSection(),
    val codes: Map<DtcKind, DiagnosticSection<List<TroubleCode>>> = DtcKind.entries.associateWith { DiagnosticSection() },
    val freezeFrame: DiagnosticSection<FreezeFrame> = DiagnosticSection(),
    val parameters: DiagnosticSection<ParameterSample> = DiagnosticSection(),
    val logs: List<String> = emptyList(),
) {
    val sectionStates get() = listOf(readiness.state) + DtcKind.entries.map { codes.getValue(it).state } + listOf(freezeFrame.state, parameters.state)
    val hasAcquiredData get() = readiness.value != null || codes.values.any { it.value != null } || freezeFrame.value != null || parameters.value?.readings?.isNotEmpty() == true
    val duration get() = endedAt?.let { Duration.between(context.startedAt, it) }
}

/** Rapporto ottenuto soltanto dalla fotografia conclusa; non accede al trasporto. */
object DiagnosisReport {
    /** Restituisce null finché il tentativo non è concluso; non interroga la sessione. */
    fun render(diagnosis: Diagnosis): String? {
        if (diagnosis.phase != DiagnosisPhase.ENDED) return null
        return buildString {
            val context = diagnosis.context
            appendLine("MULTIPLA OBD · Diagnosi #${diagnosis.id}")
            appendLine("Tipo sessione: ${if (context.demo) "SIMULATA — DATI INVENTATI" else "REALE"}")
            appendLine("Adattatore: ${context.adapterName} · ${context.adapter}")
            appendLine("Protocollo: ${context.protocol}")
            appendLine("Alimentazione annotata manualmente: ${context.fuel}")
            appendLine("Inizio: ${context.startedAt}; fine: ${diagnosis.endedAt}; durata: ${diagnosis.duration?.toMillis()} ms")
            appendLine("Esito: ${diagnosis.outcome?.label}")
            if (!diagnosis.hasAcquiredData) appendLine("Nessun dato acquisito")
            diagnosis.reason?.let { appendLine("Motivo: $it") }
            appendLine("Orari di acquisizione del telefono. Letture sequenziali, non simultanee.")
            appendLine("Stato spia e readiness dall'ultima cancellazione")
            section(diagnosis.readiness)
            diagnosis.readiness.value?.let { status ->
                appendLine("Spia richiesta dalla centralina: ${if (status.engine.mil) "accesa" else "spenta"}; codici confermati dichiarati: ${status.engine.confirmedCount}")
                status.monitors.forEach { appendLine("${it.id.label}: ${it.state.label}") }
            }
            DtcKind.entries.forEach { kind ->
                val codes = diagnosis.codes.getValue(kind)
                appendLine("${kind.label}: ${codes.state.label}")
                section(codes)
                codes.value.orEmpty().forEach { code ->
                    appendLine("${code.code} — ${code.description ?: "Descrizione non disponibile"}")
                    code.source?.let { appendLine("Fonte: $it; versione: ${code.version}") }
                }
            }
            appendLine("Freeze frame · frame 00")
            section(diagnosis.freezeFrame)
            diagnosis.freezeFrame.value?.let { frame ->
                frame.code?.let { appendLine("Codice restituito per il frame: $it") }
                frame.readings.forEach { appendLine("${it.label}: ${it.display}") }
                appendLine("Associazione fra risposte, codice e centralina non verificata con ATH0; valori restituiti separatamente, nessuna fotografia coerente garantita.")
                appendLine("Data e ora del guasto non disponibili; l'ora di lettura non è l'ora del guasto.")
            }
            appendLine("Parametri attuali")
            section(diagnosis.parameters)
            diagnosis.parameters.value?.let { sample ->
                sample.readings.forEach { appendLine("${it.label}: ${it.display}") }
                if (sample.unsupportedPids.isNotEmpty()) appendLine("PID non dichiarati disponibili: ${sample.unsupportedPids.joinToString { "%02X".format(it) }}")
            }
            appendLine("Centralina di origine non identificata. Diagnosi OBD generica: non dimostra l'accesso a tutte le centraline o alla gestione metano.")
            appendLine("L'esito descrive l'acquisizione e non certifica la salute della vettura né l'esito della revisione. Le descrizioni indicano condizioni rilevate, senza individuare cause esatte o riparazioni.")
            appendLine("Registro originale disponibile: ultime 400 voci alla conclusione.")
            diagnosis.logs.forEach { appendLine(it) }
        }
    }

    private fun StringBuilder.section(section: DiagnosticSection<*>) {
        appendLine("${section.state.label}; lettura: ${section.readAt ?: "non eseguita"}")
        section.problems.forEach { appendLine("Limite: $it") }
    }
}

internal val SectionState.label get() = when (this) {
    SectionState.ACQUIRED -> "Dati acquisiti"
    SectionState.EMPTY -> "Risposta valida senza codici o senza frame"
    SectionState.UNSUPPORTED -> "Funzione non supportata"
    SectionState.INCONCLUSIVE -> "Lettura non conclusiva"
    SectionState.NOT_PERFORMED -> "Non eseguita"
    SectionState.INTERRUPTED -> "Interrotta"
}
internal val DiagnosisOutcome.label get() = when (this) {
    DiagnosisOutcome.COMPLETED -> "Completata"
    DiagnosisOutcome.PARTIAL -> "Parziale"
    DiagnosisOutcome.INTERRUPTED -> "Interrotta"
}

internal val MonitorState.label get() = when (this) {
    MonitorState.COMPLETED -> "Completato"
    MonitorState.INCOMPLETE -> "Incompleto"
    MonitorState.UNSUPPORTED -> "Non supportato"
}
internal val MonitorId.label get() = when (this) {
    MonitorId.MISFIRE -> "Mancate accensioni"
    MonitorId.FUEL_SYSTEM -> "Sistema carburante"
    MonitorId.COMPONENTS -> "Componenti"
    MonitorId.CATALYST -> "Catalizzatore"
    MonitorId.HEATED_CATALYST -> "Catalizzatore riscaldato"
    MonitorId.EVAPORATIVE_SYSTEM -> "Sistema evaporativo"
    MonitorId.SECONDARY_AIR -> "Aria secondaria"
    MonitorId.OXYGEN_SENSOR -> "Sensore ossigeno"
    MonitorId.OXYGEN_HEATER -> "Riscaldatore sensore ossigeno"
    MonitorId.EGR -> "Ricircolo gas di scarico"
    MonitorId.NMHC_CATALYST -> "Catalizzatore idrocarburi non metanici"
    MonitorId.NOX_SCR -> "Trattamento ossidi di azoto"
    MonitorId.BOOST -> "Sovralimentazione"
    MonitorId.EXHAUST_GAS_SENSOR -> "Sensore gas di scarico"
    MonitorId.PARTICULATE_FILTER -> "Filtro antiparticolato"
}
