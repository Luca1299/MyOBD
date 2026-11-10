package it.multipla.obd.core

/** La provenienza è disponibile solo per una corrispondenza esatta verificata. */
data class TroubleCode(val code: String, val description: String?, val source: String? = null, val version: String? = null)

/** Sottoinsieme locale CC0: nessuna approssimazione per codici del costruttore. */
object DtcCatalog {
    const val VERSION = "9ba1ffa095f3140d9e21b985b322ddea3134bb60 · it-1"
    const val SOURCE = "OBDex · CC0-1.0 · https://github.com/foerbsnavi/OBDex"
    private val descriptions = mapOf(
        "P0133" to "Risposta lenta del circuito sensore ossigeno, banco 1 sensore 1",
        "P0171" to "Miscela troppo magra, banco 1",
        "P0300" to "Mancate accensioni casuali o multiple rilevate",
    )
    fun describe(code: String): TroubleCode {
        val description = descriptions[code]
        return TroubleCode(code, description, if (description != null) SOURCE else null, if (description != null) VERSION else null)
    }
}
