package it.multipla.obd.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

interface ElmTransport {
    val disconnects: Flow<String> get() = emptyFlow()
    suspend fun open()
    suspend fun exchange(command: String, timeoutMs: Long): String
    fun close()
}

/** A Bluetooth read is a fragment, not a complete ELM response. */
object PromptReader {
    fun read(input: InputStream, maxBytes: Int = 32_768): String {
        val out = StringBuilder()
        while (out.length < maxBytes) {
            val byte = input.read()
            if (byte == -1) throw EOFException("Collegamento chiuso prima del prompt ELM")
            out.append(byte.toChar())
            if (byte == '>'.code) return out.toString()
        }
        throw IOException("Risposta ELM troppo lunga o priva del prompt finale")
    }
}

class NoDataException(message: String = "Nessun dato ricevuto per questa richiesta") : Exception(message)
class ElmResponseException(message: String) : Exception(message)
class ObdNegativeResponse(val service: Int, val reason: Int) :
    Exception(
        "Richiesta %02X rifiutata dalla centralina (codice %02X)".format(service, reason),
    )
