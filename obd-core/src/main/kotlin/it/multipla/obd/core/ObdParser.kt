package it.multipla.obd.core

/** Assumes ATH0 and the ELM's automatic CAN formatting (CAF1 default). */
object ObdParser {
    fun frames(raw: String, command: String): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        var indexed: MutableList<Int>? = null
        var declaredLength: Int? = null
        var nextIndex = 0
        var searching = false
        var responseStarted = false
        fun flush() {
            indexed?.let { bytes ->
                if (declaredLength != null && bytes.size != declaredLength) {
                    throw ElmResponseException("Risposta CAN incompleta: ${bytes.size}/$declaredLength byte")
                }
                if (bytes.isNotEmpty()) result.add(bytes.toList())
            }
            indexed = null
            declaredLength = null
            nextIndex = 0
        }
        for (original in raw.replace('>', '\n').lineSequence()) {
            var line = original.trim().uppercase()
            if (line.isBlank() || line.replace(" ", "") == command.uppercase()) continue
            if (!responseStarted && line.startsWith("SEARCHING...")) {
                searching = true
                line = line.removePrefix("SEARCHING...").trim()
            }
            // Some adapters continue the search dots on the following line or attach data to them.
            // This allowance ends at the first response record and never removes dots inside data.
            if (searching) line = line.trimStart('.').trimStart()
            if (line.isBlank() || line == "BUS INIT: OK" || line == "BUS INIT: ...OK") continue
            searching = false
            responseStarted = true
            if (line == "NO DATA") throw NoDataException()
            if (line == "?" || listOf("UNABLE TO CONNECT", "ERROR", "STOPPED", "BUFFER FULL")
                    .any { line.contains(it) }) {
                throw ElmResponseException("Risposta adattatore: $line")
            }
            if (line.matches(Regex("[0-9A-F]{3}"))) {
                flush()
                declaredLength = line.toInt(16)
                continue
            }
            val numbered = Regex("^([0-9A-F]+):\\s*(.*)$").matchEntire(line)
            if (numbered != null) {
                val index = numbered.groupValues[1].toInt(16)
                if (index == 0 && indexed != null) flush()
                if (index != nextIndex) throw ElmResponseException("Sequenza CAN incompleta")
                if (indexed == null) indexed = mutableListOf()
                indexed!!.addAll(hex(numbered.groupValues[2]))
                nextIndex++
            } else {
                if (declaredLength != null && indexed == null) {
                    throw ElmResponseException("Intestazione CAN senza blocco dati")
                }
                flush()
                result.add(hex(line))
            }
        }
        if (declaredLength != null && indexed == null) throw ElmResponseException("Risposta CAN vuota")
        flush()
        if (result.isEmpty()) throw ElmResponseException("Nessuna risposta OBD valida")
        return result
    }

    private fun hex(line: String): List<Int> {
        val compact = line.filterNot { it.isWhitespace() }
        if (compact.isEmpty() || compact.length % 2 != 0 || !compact.matches(Regex("[0-9A-F]+"))) {
            throw ElmResponseException("Formato OBD non riconosciuto: $line")
        }
        return compact.chunked(2).map { it.toInt(16) }
    }

    fun positive(raw: String, command: String, service: Int): List<List<Int>> {
        val records = frames(raw, command)
        records.firstOrNull { it.firstOrNull() == 0x7F }?.let {
            if (it.size < 3) throw ElmResponseException("Risposta negativa incompleta")
            throw ObdNegativeResponse(it[1], it[2])
        }
        if (records.any { it.firstOrNull() != service + 0x40 }) {
            throw ElmResponseException("Servizio OBD inatteso")
        }
        return records.map { it.drop(1) }
    }

    fun pid(raw: String, pid: Int, minDataBytes: Int): List<Int> {
        val command = "01%02X".format(pid)
        val records = positive(raw, command, 0x01)
        val matching = records.filter { it.firstOrNull() == pid }
        if (matching.isEmpty() || matching.any { it.size < minDataBytes + 1 }) {
            throw ElmResponseException("Risposta PID %02X assente o incompleta".format(pid))
        }
        return matching.first().drop(1)
    }

    fun supported(raw: String, base: Int): Set<Int> {
        val bytes = pid(raw, base, 4)
        return (0 until 32).filter { bit -> bytes[bit / 8] and (1 shl (7 - bit % 8)) != 0 }
            .map { base + it + 1 }.toSet()
    }

    fun dtcs(raw: String, service: Int, isCan: Boolean): List<String> {
        val records = positive(raw, "%02X".format(service), service)
        return records.flatMap { payload ->
            val bytes: List<Int>
            if (isCan) {
                if (payload.isEmpty()) throw ElmResponseException("Conteggio errori CAN assente")
                val count = payload.first()
                if (payload.size < 1 + count * 2 || payload.drop(1 + count * 2).any { it != 0 }) {
                    throw ElmResponseException("Conteggio errori CAN incoerente")
                }
                bytes = payload.drop(1).take(count * 2)
                if (bytes.chunked(2).any { it == listOf(0, 0) }) {
                    throw ElmResponseException("Codice DTC nullo nel conteggio CAN")
                }
            } else {
                if (payload.isEmpty() || payload.size % 2 != 0) {
                    throw ElmResponseException("Elenco errori incompleto")
                }
                bytes = payload
            }
            bytes.chunked(2).filter { it != listOf(0, 0) }.map { pair ->
                val a = pair[0]
                val b = pair[1]
                "${"PCBU"[a shr 6]}${(a shr 4) and 3}${(a and 15).toString(16).uppercase()}%02X".format(b)
            }
        }.distinct()
    }
}
