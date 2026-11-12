package it.multipla.obd

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val warningColor = Color(0xFFEDC381)
internal val actionShape = RoundedCornerShape(12.dp)

/** Tema della revisione visiva; non dipende dalla modalità o dallo stato della sessione. */
@Composable
internal fun MultiplaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF73DEC1),
            onPrimary = Color(0xFF00382D),
            secondary = Color(0xFFA5C9EE),
            secondaryContainer = Color(0xFF23483E),
            onSecondaryContainer = Color(0xFFA7F2DC),
            background = Color(0xFF0D171A),
            onBackground = Color(0xFFE6F1F2),
            surface = Color(0xFF152327),
            surfaceVariant = Color(0xFF1B2A2F),
            onSurface = Color(0xFFE6F1F2),
            onSurfaceVariant = Color(0xFFB0C3C8),
            outline = Color(0xFF304348),
            error = warningColor,
        ),
        content = content,
    )
}

/** Intestazione e navigazione osservano lo stato; la selezione non richiede letture. */
@Composable
internal fun DiagnosticScaffold(state: DiagnosticState, selectedTab: Int, onTabSelected: (Int) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val tabs = listOf(R.string.ui_tab_connection, R.string.ui_tab_parameters, R.string.diagnosis_tab, R.string.ui_tab_register)
    val scrollStates = tabs.map { rememberScrollState() }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ui_app_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_vehicle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 6.dp).size(7.dp).background(if (state.info != null) MaterialTheme.colorScheme.primary else warningColor, CircleShape))
                    Text(state.phase, style = MaterialTheme.typography.bodySmall)
                }
                if (state.demo) StatusBadge(stringResource(R.string.diagnosis_demo), warning = true)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("diagnostic-tabs")) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { onTabSelected(index) }, selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text(stringResource(label)) })
                }
            }
            Column(
                Modifier.fillMaxSize().testTag("diagnostic-content").verticalScroll(scrollStates[selectedTab]).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { InfoCard { Text(it, color = warningColor) } }
                content()
            }
        }
    }
}

/** Superficie comune a istruzioni, risultati e dettagli, senza effetti propri. */
@Composable
internal fun InfoCard(warning: Boolean = false, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (warning) warningColor.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (warning) warningColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** L'esito rimane testuale: il colore lo accompagna senza sostituirne il significato. */
@Composable
internal fun StatusBadge(text: String, warning: Boolean = false, neutral: Boolean = false) {
    val color = if (warning) {
        warningColor
    } else if (neutral) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Ricompone la coppia con caratteri ingranditi, mantenendo visibili entrambi i testi. */
@Composable
internal fun LabelValueRow(label: String, value: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 250.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** L'orario resta quello acquisito; solo la presentazione usa il fuso del telefono. */
internal fun acquisitionTime(instant: Instant): String = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss.SSS", Locale.ITALY).withZone(ZoneId.systemDefault()).format(instant)
