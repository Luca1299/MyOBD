package it.multipla.obd.core.diagnostics

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import it.multipla.obd.core.DemoTransport
import it.multipla.obd.core.DiagnosticReader
import it.multipla.obd.core.DiagnosticSection
import it.multipla.obd.core.DtcCatalog
import it.multipla.obd.core.DtcKind
import it.multipla.obd.core.ElmSession
import it.multipla.obd.core.ElmTransport
import it.multipla.obd.core.FreezeFrame
import it.multipla.obd.core.MonitorId
import it.multipla.obd.core.MonitorState
import it.multipla.obd.core.ParameterSample
import it.multipla.obd.core.Readiness
import it.multipla.obd.core.ReadinessScope
import it.multipla.obd.core.SectionState
import it.multipla.obd.core.TroubleCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant

/** Risposte sintetiche attraversano i lettori, il parser e il catalogo di produzione. */
class DiagnosticSteps {
    private val responses = mutableMapOf<String, String>()
    private val commands = mutableListOf<String>()
    private val demo = DemoTransport()
    private val elm = ElmSession(
        object : ElmTransport {
            override suspend fun open() = demo.open()
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                commands += command
                return responses[command] ?: demo.exchange(command, timeoutMs)
            }
            override fun close() = demo.close()
        },
        {},
    )
    private val now = Instant.parse("2026-11-10T10:00:00Z")
    private val reader = DiagnosticReader(elm) { now }
    private var codes = emptyMap<DtcKind, DiagnosticSection<List<TroubleCode>>>()
    private var readiness: DiagnosticSection<Readiness>? = null
    private var frame: DiagnosticSection<FreezeFrame>? = null
    private var sample: DiagnosticSection<ParameterSample>? = null
    private var classified: DiagnosticSection<List<TroubleCode>>? = null
    private var otherFrame: DiagnosticSection<FreezeFrame>? = null
    private var description: TroubleCode? = null
    private var code = "P0171"

    private fun ready(block: suspend () -> Unit) = runTest {
        elm.initialize()
        block()
        elm.close()
    }
    private fun collect() = ready {
        readiness = reader.readiness()
        codes = DtcKind.entries.associateWith { reader.codes(it) }
        frame = reader.freezeFrame()
        sample = reader.currentParameters()
    }
    private fun storedFrame() {
        responses["020200"] = "42 02 00 01 71\r>"
        responses["020000"] = "42 00 00 00 18 00 00\r>"
        responses["020C00"] = "42 0C 00 10 00\r>"
        responses["020D00"] = "42 0D 00 14\r>"
    }

    @Given("the same trouble code is reported as stored, pending and permanent")
    fun repeatedCode() {
        responses.putAll(mapOf("03" to "4301710000>", "07" to "4701710171>", "0A" to "4A01710000>"))
    }

    @Given("a valid stored trouble code response contains no codes")
    fun emptyStored() {
        responses["03"] = "4300000000>"
    }

    @Given("the pending trouble code response cannot be read")
    fun failedPending() {
        responses["07"] = "NO DATA>"
    }

    @When("the trouble code categories are read")
    fun readCodes() = ready { codes = DtcKind.entries.associateWith { reader.codes(it) } }

    @Then("the trouble code remains in each reported category")
    fun categories() {
        assertEquals(3, codes.size)
        codes.values.forEach { assertEquals(listOf("P0171"), it.value!!.map { c -> c.code }) }
    }

    @Then("the stored category reports a valid response without codes")
    fun emptyCategory() {
        assertEquals(SectionState.EMPTY, codes.getValue(DtcKind.STORED).state)
    }

    @Then("the pending category reports an inconclusive reading")
    fun pendingInconclusive() {
        assertEquals(SectionState.INCONCLUSIVE, codes.getValue(DtcKind.PENDING).state)
    }

    @Then("the result does not claim that the vehicle has no faults")
    fun noHealthClaim() {
        assertTrue(codes.values.any { it.state == SectionState.INCONCLUSIVE })
    }

    @Given("a diagnostic reading returns {string}")
    fun response(value: String) {
        responses["03"] = when (value) {
            "NO DATA" -> "NO DATA>"
            "no response" -> ">"
            else -> "43ZZ>"
        }
    }

    @Given("valid diagnostic evidence confirms that a requested function is unsupported")
    fun unsupported() {
        responses["03"] = "7F0311>"
    }

    @When("the reading outcome is classified")
    fun classify() = ready { classified = reader.codes(DtcKind.STORED) }

    @Then("the reading remains inconclusive")
    @Then("the function is not classified as unsupported solely from that outcome")
    fun inconclusive() {
        assertEquals(SectionState.INCONCLUSIVE, classified!!.state)
    }

    @Then("the result is not classified as a valid response without codes")
    @Then("the outcome is distinct from a valid response without codes")
    fun notEmpty() {
        assertNotEquals(SectionState.EMPTY, classified!!.state)
    }

    @Then("the function is classified as unsupported with the available reason")
    fun unsupportedReason() {
        assertEquals(SectionState.UNSUPPORTED, classified!!.state)
        assertTrue(classified!!.problems.isNotEmpty())
    }

    @Then("the outcome is distinct from a missing response")
    fun notMissing() {
        assertNotEquals(SectionState.INCONCLUSIVE, classified!!.state)
    }

    @Given("a valid engine status declares a confirmed trouble code count")
    fun count() {
        responses["0101"] = "410185070000>"
    }

    @Given("the collected trouble codes belong to more than one category")
    fun multipleCategories() = repeatedCode()

    @When("the diagnostic result is assembled")
    fun assemble() = collect()

    @Then("the declared confirmed trouble code count is preserved")
    @Then("it is not replaced by the sum of the category sizes")
    fun originalCount() {
        assertEquals(5, readiness!!.value!!.engine.confirmedCount)
        assertNotEquals(5, codes.values.sumOf { it.value.orEmpty().size })
    }

    @Given("a reported trouble code has an applicable verified local catalogue entry")
    fun known() {
        code = "P0171"
    }

    @Given("the catalogue entry has an identifiable source and version")
    fun source() {
        assertNotNull(DtcCatalog.describe(code).source)
        assertNotNull(DtcCatalog.describe(code).version)
    }

    @Given("a reported trouble code has no verified local description")
    fun unknown() {
        code = "P1999"
    }

    @Given("a manufacturer-specific trouble code has no verified applicable definition")
    fun manufacturer() {
        code = "P1171"
    }

    @Given("an unrelated generic catalogue entry resembles that code")
    fun similar() {
        assertNotNull(DtcCatalog.describe("P0171").description)
    }

    @When("the trouble code description is prepared")
    fun describe() {
        description = DtcCatalog.describe(code)
    }

    @Then("the original trouble code and verified description are preserved")
    fun described() {
        assertEquals(code, description!!.code)
        assertEquals("Miscela troppo magra, banco 1", description!!.description)
    }

    @Then("the description provenance remains available")
    fun provenance() {
        assertTrue(description!!.source!!.contains("OBDex"))
        assertTrue(description!!.version!!.isNotBlank())
    }

    @Then("no inferred repair instruction is added")
    fun noRepair() {
        assertFalse(description!!.description!!.contains("sostitu"))
    }

    @Then("the original trouble code remains available")
    fun originalCode() {
        assertEquals(code, description!!.code)
    }

    @Then("its description is explicitly unavailable")
    @Then("no description is invented")
    @Then("the unrelated generic entry is not used")
    @Then("the original code is retained with an unavailable description")
    fun unavailableDescription() {
        assertEquals(code, description!!.code)
        assertNull(description!!.description)
        assertNull(description!!.source)
    }

    @Given("a controlled vehicle supports the planned read-only diagnostic sections")
    fun completeVehicle() {
        storedFrame()
        responses["0A"] = "4A00000000>"
    }

    @When("the approved diagnostic readers acquire their corresponding data")
    fun readAll() = collect()

    @Then("the vehicle requests remain within the approved read-only services")
    fun readOnly() {
        assertTrue(commands.filterNot { it.startsWith("AT") }.all { it.startsWith("01") || it.matches(Regex("02[0-9A-F]{2}00")) || it in listOf("03", "07", "0A") })
        assertTrue(commands.contains("020C00"))
    }

    @Then("no diagnostic clearing command is sent")
    fun noClear() {
        assertFalse(commands.contains("04"))
    }

    @Then("no actuator control or vehicle coding command is sent")
    fun noWrites() {
        assertFalse(commands.any { it.startsWith("08") || it.startsWith("2E") })
    }

    @Given("a supported current parameter returns a valid zero measurement")
    fun zero() {
        responses["010D"] = "410D00>"
    }

    @When("the current parameter sample is decoded")
    fun current() = ready { sample = reader.currentParameters() }

    @Then("the zero value and its unit are retained")
    @Then("that parameter is not classified as unavailable")
    fun validZero() {
        assertEquals("0 km/h", sample!!.value!!.readings.single { it.pid == 0x0D }.display)
        assertFalse(0x0D in sample!!.value!!.unsupportedPids)
    }

    @Given("valid monitor status since trouble codes were cleared is available")
    fun monitorAvailable() { }

    @Given("a monitor is {string} with completion {string}")
    fun monitorBits(s: String, c: String) {
        responses["0101"] = "41010007${if (s == "supported") "01" else "00"}${if (c == "incomplete") "01" else "00"}>"
    }

    @When("the monitor readiness is decoded")
    @When("the readiness result is prepared")
    fun decodeReadiness() = ready { readiness = reader.readiness() }

    @Then("that monitor is reported as {string}")
    fun monitorOutcome(outcome: String) {
        assertEquals(
            when (outcome) {
                "completed" -> MonitorState.COMPLETED
                "incomplete" -> MonitorState.INCOMPLETE
                else -> MonitorState.UNSUPPORTED
            },
            readiness!!.value!!.monitors.single { it.id == MonitorId.CATALYST }.state,
        )
    }

    @Given("the monitor status response is incomplete or malformed")
    fun invalidStatus() {
        responses["0101"] = "41010007>"
    }

    @Given("no interpretable monitor status response is received")
    fun missingStatus() {
        responses["0101"] = "NO DATA>"
    }

    @Then("the readiness reading is reported as failed")
    @Then("the readiness reading remains inconclusive")
    fun readinessFailed() {
        assertEquals(SectionState.INCONCLUSIVE, readiness!!.state)
    }

    @Then("unsupported monitor states are not invented")
    @Then("completed monitor states are not invented")
    @Then("no list of unsupported monitors is fabricated from the missing response")
    fun noMonitorDefaults() {
        assertNull(readiness!!.value)
    }

    @Given("valid engine status does not request the engine warning lamp")
    fun lampOff() {
        responses["0101"] = "410100070101>"
    }

    @Given("a supported emission monitor is incomplete since trouble codes were cleared")
    fun incompleteMonitor() {
        responses["0101"] = "410100070101>"
    }

    @Then("that monitor remains incomplete")
    fun remainsIncomplete() {
        assertFalse(readiness!!.value!!.engine.mil)
        monitorOutcome("incomplete")
    }

    @Then("the result retains its scope since trouble codes were cleared")
    fun readinessScope() {
        assertEquals(ReadinessScope.SINCE_CODES_CLEARED, readiness!!.value!!.scope)
    }

    @Given("a valid stored freeze frame identifies an associated trouble code")
    fun validFrame() = storedFrame()

    @Given("a stored parameter differs from its current measurement")
    fun differs() {
        responses["010C"] = "410C2000>"
    }

    @When("the diagnostic data is collected")
    fun collectData() = collect()

    @Then("the freeze frame retains its stored value and associated code")
    fun storedValues() {
        assertEquals("P0171", frame!!.value!!.code)
        assertEquals("1024.0 giri/min", frame!!.value!!.readings.single { it.pid == 12 }.display)
    }

    @Then("the current measurement remains a separate value")
    fun separateCurrent() {
        assertEquals("2048.0 giri/min", sample!!.value!!.readings.single { it.pid == 12 }.display)
    }

    @Given("one controlled reading validly declares that no freeze frame is stored")
    fun absentFrame() {
        responses["020200"] = "4202000000>"
    }

    @Given("another controlled reading receives no freeze frame response")
    fun missingFrame() { }

    @When("their freeze frame outcomes are classified")
    fun twoFrames() = ready {
        frame = reader.freezeFrame()
        responses["020200"] = "NO DATA>"
        otherFrame = reader.freezeFrame()
    }

    @Then("the first reading reports valid absence of a stored frame")
    fun emptyFrame() {
        assertEquals(SectionState.EMPTY, frame!!.state)
    }

    @Then("the second reading remains inconclusive")
    fun otherFailed() {
        assertEquals(SectionState.INCONCLUSIVE, otherFrame!!.state)
    }

    @Given("a readable freeze frame is missing one expected parameter")
    fun partialFrame() {
        storedFrame()
        responses["020D00"] = "NO DATA>"
    }

    @Given("a current measurement for that parameter is available")
    fun currentAvailable() {
        responses["010D"] = "410D00>"
    }

    @When("the freeze frame result is prepared")
    @When("the freeze frame acquisition is prepared")
    fun prepareFrame() = ready { frame = reader.freezeFrame() }

    @Then("its readable stored parameters are retained")
    fun retained() {
        assertTrue(frame!!.value!!.readings.any { it.pid == 12 })
    }

    @Then("the missing stored parameter is reported with its limitation")
    fun limitation() {
        assertEquals(SectionState.INCONCLUSIVE, frame!!.state)
        assertTrue(frame!!.problems.any { it.contains("Velocità") })
    }

    @Then("its value is not filled from the current measurement")
    fun notFilled() {
        assertFalse(frame!!.value!!.readings.any { it.pid == 13 })
        assertFalse(commands.contains("010D"))
    }

    @Given("current parameter support differs from valid freeze frame parameter support")
    fun supportDiffers() = storedFrame()

    @Then("supported stored parameters are determined in the freeze frame context")
    @Then("current parameter support is not used as a substitute")
    fun frameSupport() {
        assertEquals(setOf(12, 13), frame!!.value!!.supported)
        assertTrue(commands.contains("020000"))
        assertFalse(commands.contains("020500"))
    }

    @Given("the received data does not establish a certain association between frame, code and control unit")
    fun ambiguousFrame() {
        storedFrame()
        responses["020C00"] = "420C001000\r420C002000>"
    }

    @Then("uncertain associations remain explicitly unverified")
    @Then("no methane control unit attribution is inferred")
    fun unverified() {
        assertFalse(frame!!.value!!.associationVerified)
        assertNull(frame!!.value!!.controlUnit)
    }

    @Then("no single coherent frame is fabricated from ambiguously related data")
    fun notFabricated() {
        assertFalse(frame!!.value!!.readings.any { it.pid == 12 })
        assertEquals(SectionState.INCONCLUSIVE, frame!!.state)
    }

    @Given("a valid freeze frame does not provide a fault occurrence time")
    fun unknownEventTime() = storedFrame()

    @Given("the application knows when it reads the frame")
    fun knownReadTime() { }

    @Then("the read time remains acquisition metadata")
    fun readTime() {
        assertEquals(now, frame!!.readAt)
    }

    @Then("the fault occurrence time remains unknown")
    fun faultTime() {
        assertNull(frame!!.value!!.faultTime)
    }
}
