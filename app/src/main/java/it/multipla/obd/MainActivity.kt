package it.multipla.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import it.multipla.obd.core.SavedTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }
    private fun hasConnectPermission() = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        binding = bindService(Intent(this, DiagnosticService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }
    override fun onStop() {
        if (binding) {
            unbindService(serviceConnection)
            binding = false
        }
        boundService = null
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun App() {
        // I launcher restano registrati anche quando la schermata si disassocia dal servizio.
        var permissionEpoch by remember { mutableIntStateOf(0) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionEpoch++
            if (hasConnectPermission()) {
                boundService?.requestOpenFromUi()
            } else {
                boundService?.session?.message("Permesso Bluetooth negato. Puoi continuare in modalità demo.")
            }
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
                } catch (e: Exception) {
                    boundService?.session?.message("Esportazione non riuscita: ${e.message}")
                }
            }
        }
        val service = boundService
        if (service == null) {
            MultiplaTheme { Text(stringResource(R.string.ui_starting), modifier = Modifier.padding(32.dp)) }
            return
        }
        val model = service.session
        val state by model.state.collectAsStateWithLifecycle()
        var paired by remember { mutableStateOf(emptyList<PairedDevice>()) }
        var selected by rememberSaveable { mutableStateOf("") }
        var permission by remember { mutableStateOf(hasConnectPermission()) }
        var notifications by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }

        fun exportReport(snapshot: String) {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val name = "multipla-${if (state.demo) "DEMO-" else ""}$stamp.txt"
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { pendingExport.writeText(snapshot, Charsets.UTF_8) }
                    save.launch(name)
                } catch (e: Exception) {
                    model.message("Esportazione non riuscita: ${e.message}")
                }
            }
        }

        fun refresh() {
            permission = hasConnectPermission()
            notifications = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!permission) {
                paired = emptyList()
                return
            }
            try {
                val adapter = getSystemService(BluetoothManager::class.java)?.adapter
                if (adapter == null) {
                    model.message("Bluetooth non disponibile su questo dispositivo")
                    return
                }
                if (!adapter.isEnabled) {
                    paired = emptyList()
                    model.message("Attiva il Bluetooth dalle impostazioni")
                    return
                }
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

        MultiplaTheme {
            DiagnosticScaffold(state, tab, { tab = it }) {
                when (tab) {
                    0 -> ConnectionContent(
                        state = state,
                        paired = paired,
                        selected = selected,
                        permission = permission,
                        notifications = notifications,
                        actions = ConnectionActions(
                            onBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                            onPermissions = {
                                val requested = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT)
                                if (Build.VERSION.SDK_INT >= 33) requested += Manifest.permission.POST_NOTIFICATIONS
                                permissionRequest.launch(requested.toTypedArray())
                            },
                            onNotifications = { if (Build.VERSION.SDK_INT >= 33) permissionRequest.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                            onSelect = { selected = it },
                            onRefresh = { refresh() },
                            onSave = {
                                try {
                                    if (!hasConnectPermission()) {
                                        permission = false
                                        model.message("Autorizzazione Bluetooth necessaria")
                                    } else {
                                        val device = paired.firstOrNull { it.address == selected } ?: error("Seleziona un dispositivo associato")
                                        service.requestSaveFromUi(SavedTarget(device.name, device.address))
                                    }
                                } catch (e: Exception) {
                                    model.message(e.message ?: "Collegamento non riuscito")
                                }
                            },
                            onManualConnect = { service.requestManualFromUi() },
                            onRecheckVehicle = model::recheckVehicle,
                            onDemo = { service.requestDemoFromUi() },
                            onExitDemo = { service.requestExitDemoFromUi() },
                            onDisconnect = model::disconnect,
                        ),
                    )

                    1 -> ParametersContent(state, model::readSnapshot, model::fuel)

                    2 -> DiagnosisContent(state, model::startDiagnosis, model::interruptDiagnosis, model::fuel) { model.exportDiagnosis()?.let(::exportReport) }

                    else -> SessionRegisterContent(state) { exportReport(model.exportText()) }
                }
            }
        }
    }
}
