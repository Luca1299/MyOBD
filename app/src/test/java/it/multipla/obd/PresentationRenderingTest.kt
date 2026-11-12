package it.multipla.obd

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import it.multipla.obd.diagnostics.DiagnosisFixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** Evidenze del renderer Android su sessione demo reale; non certificano un telefono fisico. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PresentationRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val selectedTab = mutableIntStateOf(0)
    private val sectionNames = listOf("collegamento", "parametri", "diagnosi", "registro")

    @Test fun `four native screens render the real demo state`() {
        withDemo { state ->
            show(state)
            sectionNames.forEachIndexed { index, name ->
                select(index)
                compose.onNodeWithText("MULTIPLA / OBD").assertIsDisplayed()
                saveImage(name)
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun `narrow screens with enlarged text keep the last section content reachable`() {
        withDemo { state ->
            show(state, fontScale = 2f)
            val lastContent = listOf(
                "Torna al dispositivo reale",
                state.readings.single { it.pid == 0x07 }.display,
                "Esporta rapporto della diagnosi .txt",
                "Orari riferiti al telefono. Il rapporto contiene il contesto della sessione, i dati raccolti e gli scambi conservati.",
            )
            lastContent.forEachIndexed { index, text ->
                select(index)
                compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                if (index == 1) compose.onNodeWithText(state.readings.single { it.pid == 0x07 }.label).assertIsDisplayed()
                saveImage(sectionNames[index] + "-320-font2")
            }
        }
    }

    private fun withDemo(check: (DiagnosticState) -> Unit) {
        val fixture = DiagnosisFixture(now = { Instant.parse("2026-11-12T13:32:10Z") })
        try {
            fixture.session.demo()
            fixture.tick()
            fixture.session.fuel("Metano")
            fixture.session.readSnapshot()
            fixture.tick()
            fixture.session.startDiagnosis()
            fixture.tick()
            val state = fixture.session.state.value
            assertEquals(DiagnosisOutcome.PARTIAL, state.diagnosis?.outcome)
            check(state)
        } finally {
            fixture.close()
        }
    }

    private fun show(state: DiagnosticState, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MultiplaTheme {
                    DiagnosticScaffold(state, selectedTab.intValue, { selectedTab.intValue = it }) {
                        when (selectedTab.intValue) {
                            0 -> ConnectionContent(state, emptyList(), "", true, true, ConnectionActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
                            1 -> ParametersContent(state, {}, {})
                            2 -> DiagnosisContent(state, {}, {}, {}, {})
                            else -> SessionRegisterContent(state, {})
                        }
                    }
                }
            }
        }
    }

    private fun select(index: Int) {
        compose.runOnIdle { selectedTab.intValue = index }
        compose.waitForIdle()
    }

    private fun saveImage(name: String) {
        val destination = File("build/reports/interface-r1/$name.png")
        val directory = checkNotNull(destination.parentFile)
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            check(view.width > 0 && view.height > 0)
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        destination.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
