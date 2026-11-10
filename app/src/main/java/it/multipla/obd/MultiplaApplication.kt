package it.multipla.obd

import android.app.Application

/** Il contenitore dei dati conclusi dura quanto il processo, senza mantenere in vita il servizio. */
class MultiplaApplication : Application() {
    val diagnosisResults = DiagnosisResults()
}
