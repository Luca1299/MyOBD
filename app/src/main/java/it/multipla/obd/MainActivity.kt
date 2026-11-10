package it.multipla.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import it.multipla.obd.core.Parameters
import it.multipla.obd.core.SavedTarget
import it.multipla.obd.core.ConnectionPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class PairedDevice(val name: String, val address: String)

class MainActivity : ComponentActivity() {
    private var boundService by mutableStateOf<DiagnosticService?>(null)
    private var binding = false
    private val pendingExport get() = cacheDir.resolve("pending-obd-report.txt")
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (!binding) return
            val service = (binder as DiagnosticService.LocalBinder).service
            boundService = service
            service.requestOpenFromUi()
        }
        override fun onServiceDisconnected(name: ComponentName?) { boundService = null }
    }
    private fun hasConnectPermission() = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        binding = bindService(Intent(this, DiagnosticService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }
    override fun onStop() {
        if (binding) { unbindService(serviceConnection); binding = false }
        boundService = null
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun App() {
        // Result handlers remain registered while the UI unbinds from the service.
        var permissionEpoch by remember { mutableIntStateOf(0) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionEpoch++
            if (hasConnectPermission()) boundService?.requestOpenFromUi()
            else boundService?.session?.message("Permesso Bluetooth negato. Puoi continuare in modalità demo.")
        }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (uri != null) {
                            check(pendingExport.isFile) { "Rapporto non disponibile: ripeti l’esportazione" }
                            val snapshot = pendingExport.readText(Charsets.UTF_8)
                            val output = contentResolver.openOutputStream(uri) ?: error("Destinazione non disponibile")
                            output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                        }
                        pendingExport.delete()
                    }
                    if (uri != null) boundService?.session?.message("Rapporto esportato")
                } catch (e: Exception) { boundService?.session?.message("Esportazione non riuscita: ${e.message}") }
            }
        }
        val service = boundService
        if (service == null) {
            MaterialTheme { Text("Avvio Multipla OBD…", modifier = Modifier.padding(32.dp)) }
            return
        }
        val model = service.session
        val state by model.state.collectAsStateWithLifecycle()
        var paired by remember { mutableStateOf(emptyList<PairedDevice>()) }
        var selected by rememberSaveable { mutableStateOf("") }
        var permission by remember { mutableStateOf(hasConnectPermission()) }
        var notifications by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }

        fun refresh() {
            permission = hasConnectPermission()
            notifications = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!permission) { paired = emptyList(); return }
            try {
                val adapter = getSystemService(BluetoothManager::class.java)?.adapter
                if (adapter == null) { model.message("Bluetooth non disponibile su questo dispositivo"); return }
                if (!adapter.isEnabled) { paired = emptyList(); model.message("Attiva il Bluetooth dalle impostazioni"); return }
                paired = adapter.bondedDevices.map { PairedDevice(it.name ?: "Dispositivo senza nome", it.address) }
                    .filter { it.name.equals("OBDII", ignoreCase = true) }
                    .sortedWith(compareByDescending<PairedDevice> { it.name.equals("OBDII", ignoreCase = true) }.thenBy { it.name }.thenBy { it.address })
                if (paired.none { it.address == selected }) selected = paired.firstOrNull()?.address.orEmpty()
            } catch (e: SecurityException) {
                paired = emptyList()
                model.message("Autorizza l’accesso ai dispositivi nelle vicinanze")
            }
        }

        LaunchedEffect(state.connection.target?.address) {
            if (!state.demo) state.connection.target?.address?.let { selected = it }
        }
        LaunchedEffect(permissionEpoch) { refresh() }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
            lifecycle.addObserver(observer)
            refresh()
            onDispose { lifecycle.removeObserver(observer) }
        }

        MaterialTheme(colorScheme = darkColorScheme(
            primary = Color(0xFF73DEC1), onPrimary = Color(0xFF00382D),
            background = Color(0xFF0D171A), surface = Color(0xFF152327),
            surfaceVariant = Color(0xFF213238), onSurface = Color(0xFFE6F1F2)
        )) {
            Scaffold { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("MULTIPLA / OBD", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("2005 · benzina + metano originale", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.padding(top = 5.dp).size(8.dp).background(
                                if (state.info != null) MaterialTheme.colorScheme.primary else Color(0xFFE9BE73), CircleShape))
                            Text(state.phase, style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.demo) Text("DEMO · DATI SIMULATI", color = Color(0xFFE9BE73), fontWeight = FontWeight.Bold)
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                        listOf("Collegamento", "Parametri", "Errori", "Registro").forEachIndexed { index, label ->
                            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                        }
                    }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.error?.let { InfoCard { Text(it, color = Color(0xFFE9BE73)) } }
                        when (tab) {
                            0 -> {
                                InfoCard {
                                    Text("La prima connessione", style = MaterialTheme.typography.titleMedium)
                                    Text("Con l’auto ferma, inserisci l’adattatore e porta il quadro su ON. Seleziona OBDII e salvalo una volta: alle aperture successive il collegamento sarà automatico.")
                                    Text("La sessione continua passando a un’altra app o spegnendo lo schermo. Puoi interromperla qui o dalla notifica.", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, enabled = !state.busy) { Text("Impostazioni Bluetooth") }
                                }
                                if (!permission) Button(onClick = {
                                    val requested = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT)
                                    if (Build.VERSION.SDK_INT >= 33) requested += Manifest.permission.POST_NOTIFICATIONS
                                    permissionRequest.launch(requested.toTypedArray())
                                }, enabled = !state.busy) { Text("Autorizza Bluetooth e notifiche") }
                                else {
                                    if (!notifications && Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
                                        permissionRequest.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    }) { Text("Consenti la notifica della sessione") }
                                    Text("Dispositivi già associati", style = MaterialTheme.typography.titleMedium)
                                    if (paired.isEmpty()) Text("Nessun dispositivo disponibile. Controlla Bluetooth e associazione.")
                                    paired.forEach { device ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            RadioButton(selected = selected == device.address, onClick = { selected = device.address }, enabled = !state.busy && !state.connected && !state.demo)
                                            Column(Modifier.padding(top = 10.dp)) {
                                                Text(device.name, fontWeight = FontWeight.SemiBold)
                                                Text("ID …${device.address.takeLast(8)}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    TextButton(onClick = { refresh() }, enabled = !state.busy) { Text("Aggiorna elenco") }
                                    Button(enabled = selected.isNotBlank() && !state.busy && !state.connected && !state.demo, onClick = {
                                        try {
                                            if (!hasConnectPermission()) { permission = false; model.message("Autorizzazione Bluetooth necessaria") }
                                            else {
                                                val device = paired.firstOrNull { it.address == selected } ?: error("Seleziona un dispositivo associato")
                                                service.requestSaveFromUi(SavedTarget(device.name, device.address))
                                            }
                                        } catch (e: Exception) { model.message(e.message ?: "Collegamento non riuscito") }
                                    }) { Text("Salva dispositivo OBDII") }
                                    if (state.connection.target != null && !state.connected && !state.demo) {
                                        Button(onClick = { service.requestManualFromUi() }, enabled = !state.busy) { Text("Connetti manualmente") }
                                    }
                                }
                                if (state.connected) {
                                    InfoCard {
                                        Text("Adattatore collegato", style = MaterialTheme.typography.titleMedium)
                                        Text(state.connection.adapter.orEmpty())
                                        state.info?.let { info ->
                                            Text(info.protocol)
                                            Text("${info.supported.size} PID rilevati nelle pagine interrogate")
                                        }
                                        Text("L’identità della centralina Fiat/metano resta da verificare.", style = MaterialTheme.typography.bodySmall)
                                        if (state.connection.phase == ConnectionPhase.ADAPTER_READY) OutlinedButton(onClick = { model.recheckVehicle() }) { Text("Riprova verifica centralina") }
                                    }
                                }
                                state.connection.detail?.let { Text(it, color = Color(0xFFE9BE73)) }
                                if (state.connection.phase == ConnectionPhase.MANUAL_REQUIRED) Text("L’autoconnessione è sospesa. Premi Connetti manualmente: un collegamento riuscito la riabilita anche per le prossime aperture.")
                                if (!state.demo) OutlinedButton(onClick = { model.demo() }, enabled = !state.busy && !state.connected) { Text("Prova con dati simulati") }
                                else OutlinedButton(onClick = { service.requestExitDemoFromUi() }, enabled = !state.busy) { Text("Torna al dispositivo reale") }
                                if (state.busy || state.connected) OutlinedButton(onClick = { model.disconnect() }) { Text("Scollega / annulla") }
                                Text("Versione 0.2.1 · ADR-0001 / ADR-0002 · letture diagnostiche", style = MaterialTheme.typography.bodySmall)
                            }
                            1 -> {
                                Text("Un campione del motore", style = MaterialTheme.typography.titleLarge)
                                Text("Annota l’alimentazione attuale prima della lettura. L’app non la rileva automaticamente.")
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("Benzina", "Metano").forEach { fuel ->
                                        FilterChip(selected = state.fuel == fuel, onClick = { model.fuel(fuel) }, label = { Text(fuel) }, enabled = !state.busy)
                                    }
                                }
                                Button(onClick = { model.readSnapshot() }, enabled = state.info != null && !state.busy) { Text("Leggi parametri disponibili") }
                                if (state.info == null) Text("Verifica prima la centralina dalla scheda Collegamento oppure avvia la demo.")
                                state.sampleTime?.let { Text("Campione completato alle $it · ${state.fuel}", style = MaterialTheme.typography.bodySmall) }
                                state.engine?.let { engine ->
                                    InfoCard {
                                        Text("Spia motore: ${if (engine.mil) "richiesta accesa dalla ECU" else "non richiesta dalla ECU"}")
                                        Text("Errori confermati dichiarati: ${engine.confirmedCount}")
                                    }
                                }
                                state.readings.forEach { reading ->
                                    InfoCard {
                                        Text(reading.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(reading.display, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                state.problems.forEach { Text(it, color = Color(0xFFE9BE73)) }
                                state.info?.let { info ->
                                    val unavailable = Parameters.all.filter { it.pid !in info.supported }
                                    if (unavailable.isNotEmpty()) Text("Non dichiarati disponibili: " + unavailable.joinToString { it.label }, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Le letture sono sequenziali. Esporta il rapporto per conservare il campione prima di leggerne un altro.", style = MaterialTheme.typography.bodySmall)
                            }
                            2 -> {
                                Text("Diagnostica OBD motore", style = MaterialTheme.typography.titleLarge)
                                Text("Legge i codici restituiti attraverso l’OBD standard. I sistemi Fiat/metano dedicati richiederanno una verifica specifica.")
                                Button(onClick = { model.readErrors() }, enabled = state.info != null && !state.busy) { Text("Leggi errori") }
                                state.dtcTime?.let { Text("Scansione completata alle $it", style = MaterialTheme.typography.bodySmall) }
                                state.dtcs.forEach { result ->
                                    InfoCard {
                                        Text(result.kind.label, style = MaterialTheme.typography.titleMedium)
                                        when {
                                            result.problem != null -> Text("Lettura non conclusiva: ${result.problem}", color = Color(0xFFE9BE73))
                                            result.codes.isEmpty() -> Text("Nessun codice restituito per questa categoria")
                                            else -> result.codes.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineSmall) }
                                        }
                                    }
                                }
                                if (state.dtcs.isEmpty()) Text("Scansione non ancora eseguita.")
                                Text("La cancellazione errori verrà aggiunta dopo la verifica delle letture sull’auto.", style = MaterialTheme.typography.bodySmall)
                            }
                            else -> {
                                Text("Registro della sessione", style = MaterialTheme.typography.titleLarge)
                                Text("Ultime 400 voci. Include richieste, risposte e problemi di comunicazione.")
                                OutlinedButton(onClick = {
                                    val snapshot = model.exportText()
                                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                                    val name = "multipla-${if (state.demo) "DEMO-" else ""}$stamp.txt"
                                    lifecycleScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { pendingExport.writeText(snapshot, Charsets.UTF_8) }
                                            save.launch(name)
                                        } catch (e: Exception) { model.message("Esportazione non riuscita: ${e.message}") }
                                    }
                                }, enabled = !state.busy && state.logs.isNotEmpty()) { Text("Esporta rapporto .txt") }
                                state.logs.takeLast(120).forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                                if (state.logs.isEmpty()) Text("Avvia una sessione per registrare gli scambi.")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
