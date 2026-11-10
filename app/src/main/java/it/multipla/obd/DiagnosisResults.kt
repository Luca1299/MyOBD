package it.multipla.obd

import java.util.concurrent.atomic.AtomicLong

/**
 * Ultimi risultati conclusi del processo, separati fra reale e demo.
 * Non possiede sessioni, trasporti o operazioni e non scrive su disco.
 */
class DiagnosisResults {
    private val sequence = AtomicLong()
    private var real: Diagnosis? = null
    private var demo: Diagnosis? = null

    fun nextId(): Long = sequence.incrementAndGet()

    @Synchronized
    fun last(simulated: Boolean): Diagnosis? = if (simulated) demo else real

    /** Accetta solo fotografie concluse; una pubblicazione tardiva non sostituisce una successiva. */
    @Synchronized
    fun retain(result: Diagnosis) {
        require(result.phase == DiagnosisPhase.ENDED) { "Si conservano soltanto diagnosi concluse" }
        val current = last(result.context.demo)
        if (current != null && current.id >= result.id) return
        if (result.context.demo) demo = result else real = result
    }
}
