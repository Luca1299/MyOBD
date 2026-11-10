package it.multipla.obd.diagnostics

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import it.multipla.obd.Diagnosis
import it.multipla.obd.DiagnosisOutcome
import it.multipla.obd.DiagnosisPhase
import it.multipla.obd.DiagnosisResults
import it.multipla.obd.DiagnosticsSession
import it.multipla.obd.ElmAdapterLink
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.DemoTransport
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.ElmSession
import it.multipla.obd.core.ElmTransport
import it.multipla.obd.core.MemoryConnectionStore
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.SavedTarget
import it.multipla.obd.core.SectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Usa la sessione di produzione e il coordinatore esistente con tempo virtuale. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosisSteps {
    private val fixture = DiagnosisFixture()
    private val scope get() = fixture.scope
    private val store get() = fixture.store
    private val responses get() = fixture.responses
    private val commands get() = fixture.commands
    private val losses get() = fixture.losses
    private val session get() = fixture.session
    private val opens get() = fixture.opens
    private val closes get() = fixture.closes
    private var hold: String?
        get() = fixture.hold
        set(value) {
            fixture.hold = value
        }
    private var release: CompletableDeferred<Unit>
        get() = fixture.release
        set(value) {
            fixture.release = value
        }
    private var previous: Diagnosis? = null
    private var recreated: DiagnosticsSession? = null
    private var recreatedDemo = false
    private var exported: String? = null
    private var commandCount = 0
    private var expectedOutcome = DiagnosisOutcome.COMPLETED
    private var expectNoData = false
    private val diagnosis get() = requireNotNull(session.state.value.diagnosis)
    private fun tick(ms: Long = 5000) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }
    private fun connect() {
        session.appOpened()
        tick()
        assertNotNull(session.state.value.info)
    }
    private fun startHeld() {
        connect()
        hold = "03"
        session.startDiagnosis()
        tick(200)
        assertTrue(session.state.value.busy)
    }
    private fun ended() {
        connect()
        session.startDiagnosis()
        tick()
        assertEquals(DiagnosisPhase.ENDED, diagnosis.phase)
    }

    @After fun close() {
        recreated?.close()
        session.close()
        scope.runCurrent()
    }

    @Given("no manual diagnosis has been requested")
    fun noRequest() {
        assertNull(session.state.value.diagnosis)
    }

    @When("the vehicle connection becomes available after {string}")
    fun available(event: String) {
        connect()
        if (event == "successful reconnect") {
            losses.tryEmit("perdita sintetica")
            tick()
        }
    }

    @Then("no diagnosis is started automatically")
    fun noAutomatic() {
        assertNull(session.state.value.diagnosis)
        assertFalse(commands.contains("03"))
    }

    @Then("a manual diagnosis can be requested when its prerequisites are satisfied")
    fun ready() {
        session.startDiagnosis()
        tick()
        assertNotNull(session.state.value.diagnosis)
    }

    @Given("the vehicle control unit has not been reached")
    fun notReached() { }

    @When("a manual diagnosis is requested")
    fun manual() {
        session.startDiagnosis()
        scope.runCurrent()
    }

    @Then("no diagnosis acquisition is started")
    fun noStart() {
        assertNull(session.state.value.diagnosis)
        assertTrue(commands.isEmpty())
    }

    @Then("the unmet connection prerequisite remains available for presentation")
    fun prerequisite() {
        assertNull(session.state.value.info)
        assertFalse(session.state.value.busy)
    }

    @Given("a manual diagnosis is in progress")
    fun inProgress() = startHeld()

    @When("another diagnosis and other manual diagnostic readings are requested")
    fun concurrent() {
        commandCount = commands.size
        session.startDiagnosis()
        session.readSnapshot()
        session.readErrors()
        session.recheckVehicle()
        scope.runCurrent()
    }

    @Then("no additional diagnostic operation is started or queued")
    fun noQueue() {
        assertEquals(commandCount, commands.size)
        release.complete(Unit)
        tick()
        assertEquals(1, commands.count { it == "03" })
        assertEquals(1, commands.count { it == "0101" })
    }

    @Then("only the original operation continues to use the diagnostic connection")
    fun oneOperation() {
        assertEquals(1, opens)
        assertEquals(0, closes)
    }

    @Given("a manual diagnosis ends with {string}")
    fun outcomes(value: String) {
        connect()
        when (value) {
            "valid data and confirmed unsupported functions" -> responses["0A"] = "7F0A11>"

            "valid data and valid absence of a stored frame" -> responses["020200"] = "4202000000>"

            "valid data and an inconclusive reading" -> {
                responses["07"] = "NO DATA>"
                expectedOutcome = DiagnosisOutcome.PARTIAL
            }

            "no acquired data because all reads returned content errors" -> {
                expectNoData = true
                (listOf("0101", "03", "07", "0A", "020200") + Parameters.all.map { "01%02X".format(it.pid) }).forEach { responses[it] = "ZZ>" }
                expectedOutcome = DiagnosisOutcome.PARTIAL
            }
        }
        session.startDiagnosis()
        tick()
    }

    @When("the acquisition outcome is prepared")
    fun prepareOutcome() {
        assertEquals(DiagnosisPhase.ENDED, diagnosis.phase)
    }

    @Then("the diagnosis outcome is {string}")
    fun outcome(value: String) {
        assertEquals(
            when (value) {
                "completed" -> DiagnosisOutcome.COMPLETED
                "partial" -> DiagnosisOutcome.PARTIAL
                else -> DiagnosisOutcome.INTERRUPTED
            },
            diagnosis.outcome,
        )
    }

    @Then("individual section outcomes remain available")
    fun individual() {
        assertEquals(expectedOutcome, diagnosis.outcome)
        assertEquals(6, diagnosis.sectionStates.size)
    }

    @Then("the result makes no vehicle health or inspection pass claim")
    fun noHealth() {
        val report = session.exportDiagnosis()!!
        assertFalse(report.contains("veicolo senza guasti"))
        assertTrue(report.contains("non certifica"))
        if (expectNoData) assertTrue(report.contains("Nessun dato acquisito"))
    }

    @Given("a diagnosis has acquired data from an earlier section")
    fun earlier() = startHeld()

    @Given("a later section returns a content error while the transport remains usable")
    fun localError() {
        responses["03"] = "43ZZ>"
    }

    @When("the diagnosis proceeds through its remaining sections")
    fun proceed() {
        release.complete(Unit)
        tick()
    }

    @Then("the earlier acquired data is retained")
    fun earlierRetained() {
        assertNotNull(diagnosis.readiness.value)
    }

    @Then("the failed section remains inconclusive")
    fun failed() {
        assertEquals(SectionState.INCONCLUSIVE, diagnosis.codes.getValue(DtcKind.STORED).state)
    }

    @Then("other readable sections are still acquired")
    fun laterAcquired() {
        assertNotNull(diagnosis.parameters.value)
        assertEquals(0, closes)
    }

    @Given("a diagnosis has a vehicle request in progress")
    fun activeRequest() = startHeld()

    @When("diagnosis interruption is requested")
    fun interrupt() {
        commandCount = commands.size
        session.interruptDiagnosis()
        scope.runCurrent()
    }

    @Then("diagnosis interruption remains pending until that request ends within its existing limit")
    fun pending() {
        assertEquals(DiagnosisPhase.STOPPING, diagnosis.phase)
        assertFalse(release.isCompleted)
    }

    @Then("no following diagnostic request is started")
    fun noFollowing() {
        assertEquals(commandCount, commands.size)
    }

    @When("the active request returns a valid response")
    fun returnResponse() {
        release.complete(Unit)
        tick()
    }

    @Then("its acquired data is retained")
    fun activeRetained() {
        assertEquals(SectionState.ACQUIRED, diagnosis.codes.getValue(DtcKind.STORED).state)
    }

    @Then("the diagnosis outcome is interrupted")
    fun interrupted() = outcome("interrupted")

    @Then("later sections remain not performed")
    fun notPerformed() {
        assertEquals(SectionState.NOT_PERFORMED, diagnosis.codes.getValue(DtcKind.PENDING).state)
        assertEquals(SectionState.NOT_PERFORMED, diagnosis.parameters.state)
    }

    @Then("diagnosis interruption alone does not close the connection")
    fun remainsConnected() {
        assertTrue(session.state.value.connected)
        assertEquals(0, closes)
    }

    @Given("a diagnosis has already acquired some results")
    fun someResults() = startHeld()

    @When("the diagnostic session is explicitly disconnected")
    fun disconnect() {
        session.disconnect()
        tick()
    }

    @Then("the active connection is closed through the explicit stop operation")
    fun closed() {
        assertEquals(1, closes)
        assertEquals(ConnectionPhase.STOPPED, session.state.value.connection.phase)
    }

    @Then("its acquired results remain available in session memory")
    fun memoryResults() {
        assertNotNull(diagnosis.readiness.value)
    }

    @Then("the explicit stop does not schedule a reconnection")
    fun noReconnect() {
        tick(100000)
        assertEquals(1, opens)
    }

    @Given("a diagnosis was interrupted by transport loss after acquiring some results")
    fun lost() {
        startHeld()
        losses.tryEmit("perdita sintetica")
        scope.runCurrent()
        previous = diagnosis
        assertEquals(DiagnosisOutcome.INTERRUPTED, diagnosis.outcome)
    }

    @When("the connection is recovered by the existing connection policy")
    fun recover() {
        hold = null
        tick()
        assertNotNull(session.state.value.info)
    }

    @Then("the interrupted diagnosis results remain available")
    fun preservedAfterRecovery() {
        assertEquals(previous, diagnosis)
    }

    @Then("the diagnosis is not resumed or restarted automatically")
    fun notResumed() {
        assertEquals(1, commands.count { it == "03" })
        assertEquals(DiagnosisPhase.ENDED, diagnosis.phase)
    }

    @Given("one diagnosis is owned by the service session")
    fun owned() {
        startHeld()
        previous = diagnosis
    }

    @When("{string} occurs")
    fun lifecycle(event: String) {
        session.appOpened()
        scope.runCurrent()
        assertTrue(event.isNotEmpty())
    }

    @Then("the same diagnosis remains owned by that session")
    fun sameOwner() {
        assertEquals(previous!!.id, diagnosis.id)
    }

    @Then("no duplicate diagnosis or connection is started")
    fun noDuplicate() {
        assertEquals(1, opens)
        assertEquals(1, commands.count { it == "03" })
    }

    @Given("a previous diagnosis has acquired section results")
    fun previousResult() {
        ended()
        previous = diagnosis
    }

    @When("a new manual diagnosis is started")
    fun newDiagnosis() {
        hold = "0101"
        release = CompletableDeferred()
        session.startDiagnosis()
        tick(200)
    }

    @Then("its section results belong only to the new acquisition")
    fun newId() {
        assertNotEquals(previous!!.id, diagnosis.id)
    }

    @Then("prior values are not used to fill sections not yet acquired")
    fun noOldValues() {
        assertNull(diagnosis.readiness.value)
        assertNull(diagnosis.parameters.value)
        assertTrue(diagnosis.codes.values.all { it.value == null })
    }

    @Then("late results from the previous acquisition do not update the new diagnosis")
    fun noLateResults() {
        val id = diagnosis.id
        session.disconnect()
        scope.runCurrent()
        release.complete(Unit)
        tick()
        assertEquals(id, diagnosis.id)
        assertNull(diagnosis.readiness.value)
    }

    @Given("a diagnosis captures a manual fuel annotation at its start")
    fun annotated() {
        session.fuel("Benzina")
        ended()
    }

    @When("a different annotation is prepared after that diagnosis ends")
    fun changeFuel() {
        session.fuel("Metano")
    }

    @Then("the ended diagnosis retains its captured annotation")
    fun capturedFuel() {
        assertEquals("Benzina", diagnosis.context.fuel)
        assertEquals("Metano", session.state.value.fuel)
    }

    @Then("fuel is not inferred from generic diagnostic measurements")
    fun manualFuel() {
        assertEquals("Benzina", diagnosis.context.fuel)
    }

    @Given("a diagnosis has ended with results available in memory")
    fun inMemory() = ended()

    @When("those results are made available within the application session")
    fun consult() {
        previous = diagnosis
        session.disconnect()
        scope.runCurrent()
    }

    @Then("no persistent diagnosis history entry is created")
    fun noHistory() {
        assertEquals(previous, diagnosis)
        assertEquals(SavedTarget("OBDII", "AA:BB:CC:DD:EE:01"), store.target())
    }

    @Then("retaining that diagnosis beyond the session requires explicit report export")
    fun exportOnly() {
        assertNotNull(session.exportDiagnosis())
        session.close()
        // Un contenitore nuovo rappresenta un processo distinto, privo dei dati in memoria.
        val fresh = DiagnosticsSession(scope.backgroundScope, store, { null }, results = DiagnosisResults()) { _, _ -> error("nessuna connessione") }
        assertNull(fresh.state.value.diagnosis)
        fresh.close()
    }

    @Given("a real saved adapter profile and automatic connection block state exist")
    fun realProfile() {
        ended()
        previous = diagnosis
        session.disconnect()
        scope.runCurrent()
        store.setManualRequired(true)
    }

    @Given("the application is using a demo session")
    fun demoSession() {
        session.demo()
        tick()
    }

    @When("a manual demo diagnosis is acquired")
    fun demoDiagnosis() {
        session.startDiagnosis()
        tick()
    }

    @Then("its results are identified as simulated")
    fun simulated() {
        assertTrue(diagnosis.context.demo)
    }

    @Then("the real saved profile and connection block are unchanged")
    fun unchangedProfile() {
        assertTrue(store.manualRequired())
        assertEquals("AA:BB:CC:DD:EE:01", store.target()!!.address)
    }

    @Then("synthetic results do not overwrite real diagnosis data")
    fun isolated() {
        session.exitDemo()
        scope.runCurrent()
        assertEquals(previous, session.state.value.diagnosis)
    }

    @Given("an ended diagnosis has captured context, times and section results")
    fun reportFixture() {
        session.fuel("Benzina")
        ended()
    }

    @Given("a diagnosis has ended as {string}")
    fun incompleteReport(value: String) {
        startHeld()
        responses["03"] = "NO DATA>"
        if (value == "interrupted") {
            session.interruptDiagnosis()
        } else {
            responses["020200"] = "NO DATA>"
        }
        release.complete(Unit)
        tick()
    }

    @Given("its section results distinguish acquired data, inconclusive readings and unperformed sections")
    fun distinctSections() {
        assertNotNull(diagnosis.readiness.value)
        assertEquals(SectionState.INCONCLUSIVE, diagnosis.codes.getValue(DtcKind.STORED).state)
    }

    @When("its text report is exported")
    @When("the diagnosis text report is exported")
    fun export() {
        commandCount = commands.size
        exported = session.exportDiagnosis()
    }

    @Then("the report preserves that acquisition context and results")
    fun reportContext() {
        assertTrue(exported!!.contains(diagnosis.context.startedAt.toString()))
        assertTrue(exported!!.contains(diagnosis.endedAt.toString()))
    }

    @Then("it includes adapter, protocol, acquisition times and the captured fuel annotation")
    fun contextFields() {
        assertTrue(exported!!.contains("OBDII"))
        assertTrue(exported!!.contains(diagnosis.context.protocol))
        assertTrue(exported!!.contains("Benzina"))
    }

    @Then("trouble code categories and measurement units are preserved")
    fun categoriesUnits() {
        for (text in listOf("Memorizzati", "In attesa", "Permanenti", "km/h", "giri/min", "°C", "kPa", "%"))assertTrue(text, exported!!.contains(text))
    }

    @Then("current measurements and freeze frame values remain separate")
    fun separateReport() {
        assertTrue(exported!!.contains("Freeze frame"))
        assertTrue(exported!!.contains("Parametri attuali"))
        assertTrue(exported!!.contains("1024.0 giri/min"))
        assertTrue(exported!!.contains("820.0 giri/min"))
    }

    @Then("no new vehicle reading is performed for export")
    fun noExportReading() {
        assertEquals(commandCount, commands.size)
    }

    @Then("the report preserves acquired data and individual section outcomes")
    fun reportSections() {
        assertTrue(exported!!.contains("Dati acquisiti"))
        assertTrue(exported!!.contains("Lettura non conclusiva"))
    }

    @Then("it explains the available reason for the incomplete acquisition")
    fun reportReason() {
        assertTrue(exported!!.contains(diagnosis.reason!!))
    }

    @Then("failed or unperformed sections are not represented as valid empty results")
    fun noFalseEmpty() {
        assertFalse(exported!!.contains("Memorizzati: Risposta valida senza codici"))
    }

    @Given("a reported code has no verified description")
    fun unknownCode() {
        responses["03"] = "4319990000>"
        ended()
    }

    @Given("the responding control unit has not been identified")
    fun unknownSource() { }

    @Then("the original code remains present with its description unavailable")
    fun unknownInReport() {
        assertTrue(exported!!.contains("P1999 — Descrizione non disponibile"))
    }

    @Then("the unknown response provenance remains explicit")
    fun unknownProvenance() {
        assertTrue(exported!!.contains("Centralina di origine non identificata"))
    }

    @Then("no gasoline or methane control unit attribution is invented")
    fun noAttribution() {
        assertFalse(exported!!.contains("centralina metano:"))
        assertFalse(exported!!.contains("centralina benzina:"))
    }

    @Given("the current diagnosis is {string}")
    fun running(value: String) {
        startHeld()
        if (value == "interruption pending") session.interruptDiagnosis()
    }

    @When("export of the current acquisition is requested")
    fun exportRunning() {
        exported = session.exportDiagnosis()
    }

    @Then("no ended diagnosis report is produced for that running acquisition")
    fun noRunningReport() {
        assertNull(exported)
    }

    @Then("export remains available after the acquisition has ended")
    fun laterExport() {
        release.complete(Unit)
        tick()
        assertNotNull(session.exportDiagnosis())
    }

    @Given("an ended diagnosis was acquired in a demo session")
    fun endedDemo() {
        session.demo()
        tick()
        session.startDiagnosis()
        tick()
    }

    @Then("the report explicitly identifies the diagnosis data as simulated")
    @Then("it does not represent synthetic measurements as readings from the real vehicle")
    fun demoReport() {
        assertTrue(exported!!.contains("SIMULATA — DATI INVENTATI"))
        assertFalse(exported!!.contains("Tipo sessione: REALE"))
    }

    @Given("an ended diagnosis exists in {string} mode")
    fun endedForRecreation(mode: String) {
        recreatedDemo = mode == "demo"
        if (recreatedDemo) {
            session.demo()
            tick()
        }
        ended()
        previous = diagnosis
    }

    @When("its service is stopped and recreated in the same application process")
    fun recreateSessionOwner() {
        session.disconnect()
        scope.runCurrent()
        session.close()
        recreated = DiagnosticsSession(scope.backgroundScope, store, { null }, results = fixture.results) { _, _ -> error("Il ripristino dei dati non deve aprire collegamenti") }
        if (recreatedDemo) recreated!!.demo()
    }

    @Then("the ended result remains available in that mode")
    fun restoredResult() {
        assertEquals(previous, recreated!!.state.value.diagnosis)
        assertNotNull(recreated!!.exportDiagnosis())
    }

    @Then("restoring the result does not start a diagnostic acquisition")
    fun noAcquisitionAfterRestore() {
        assertEquals(DiagnosisPhase.ENDED, recreated!!.state.value.diagnosis!!.phase)
        assertEquals(previous!!.id, recreated!!.state.value.diagnosis!!.id)
    }
}
