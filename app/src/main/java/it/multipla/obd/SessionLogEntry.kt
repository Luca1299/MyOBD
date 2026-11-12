package it.multipla.obd

/** Categorie di presentazione del registro, senza interpretare le risposte diagnostiche. */
internal enum class SessionLogType(val labelResource: Int, val prefix: String? = null, val separator: String = " ") {
    REQUEST(R.string.register_type_request, "TX", "  "),
    RESPONSE(R.string.register_type_response, "RX", "  "),
    ERROR(R.string.register_type_error, "ERR"),
    STATE(R.string.register_type_state, "STATO"),
    NOTE(R.string.register_type_note, "NOTA"),
    SAMPLE(R.string.register_type_sample, "CAMPIONE"),
    EVENT(R.string.register_type_event),
}

/** La voce originale resta disponibile anche quando ora o prefisso non sono riconoscibili. */
internal data class SessionLogEntry(val original: String, val time: String?, val type: SessionLogType, val prefix: String?, val content: String)

private const val VISIBLE_SESSION_LOG_LIMIT = 120
private val sessionLogPattern = Regex("^((?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d\\.\\d{3}) {2}([\\s\\S]*)$")

/** Separa soltanto il formato prodotto dalla sessione, conservando integralmente il contenuto. */
internal fun presentSessionLog(raw: String): SessionLogEntry {
    val match = sessionLogPattern.matchEntire(raw) ?: return SessionLogEntry(raw, null, SessionLogType.EVENT, null, raw)
    val time = match.groupValues[1]
    val message = match.groupValues[2]
    val type = SessionLogType.entries.firstOrNull { it.prefix != null && message.startsWith("${it.prefix}${it.separator}") }
        ?: return SessionLogEntry(raw, time, SessionLogType.EVENT, null, message)
    return SessionLogEntry(raw, time, type, type.prefix, message.removePrefix("${type.prefix}${type.separator}"))
}

/** Limita solo la vista: ordine e conservazione della sessione restano invariati. */
internal fun visibleSessionLogs(logs: List<String>): List<SessionLogEntry> = logs.takeLast(VISIBLE_SESSION_LOG_LIMIT).map(::presentSessionLog)
