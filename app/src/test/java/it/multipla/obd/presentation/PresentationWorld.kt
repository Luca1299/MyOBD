package it.multipla.obd.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Ponte locale tra il ciclo Cucumber e la regola Compose, senza imitare la presentazione. */
internal object PresentationWorld {
    private var active: PresentationHarness? = null
    val harness get() = checkNotNull(active) { "Gli scenari richiedono l'ambiente Compose attivo" }

    fun attach(harness: PresentationHarness) {
        check(active == null) { "Ambiente di presentazione già attivo" }
        active = harness
    }

    fun detach() {
        active = null
    }
}

/** Cambia il componente montato mantenendo un'unica Activity gestita dalla regola Android. */
internal class PresentationHarness(val compose: ComposeContentTestRule) {
    private data class Screen(val id: Long, val scrolling: Boolean, val body: @Composable () -> Unit)
    private val screen = mutableStateOf<Screen?>(null)
    private var sequence = 0L

    fun show(scrolling: Boolean = true, content: @Composable () -> Unit) {
        compose.runOnIdle { screen.value = Screen(++sequence, scrolling, content) }
        compose.waitForIdle()
    }

    @Composable
    fun Content() {
        screen.value?.let { current ->
            key(current.id) {
                if (current.scrolling) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { current.body() }
                } else {
                    current.body()
                }
            }
        }
    }
}
