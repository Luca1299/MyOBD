package it.multipla.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import it.multipla.obd.bluetooth.RfcommTransport
import it.multipla.obd.core.ConnectionPhase
import it.multipla.obd.core.ElmSession
import it.multipla.obd.core.SavedTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

open class DiagnosticService : Service() {
    inner class LocalBinder : Binder() {
        val service get() = this@DiagnosticService
    }
    private val binder = LocalBinder()
    private lateinit var scope: CoroutineScope
    lateinit var session: DiagnosticsSession
        private set
    private lateinit var store: AndroidConnectionStore

    @Volatile private var foreground = false
    private var lastNotification = ""
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeJob: Job? = null
    private val pendingActions = AtomicInteger()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON && foreground && !session.state.value.demo) {
                session.appOpened()
            }
        }
    }

    protected open fun createScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected open fun hasConnectPermission() = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun prerequisite(target: SavedTarget): String? {
        if (!hasConnectPermission()) return "Autorizza l’accesso ai dispositivi nelle vicinanze"
        return try {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return "Bluetooth non disponibile"
            if (!adapter.isEnabled) return "Attiva il Bluetooth"
            val device = adapter.getRemoteDevice(target.address)
            when {
                device.bondState != BluetoothDevice.BOND_BONDED -> "Associa nuovamente il dispositivo dalle impostazioni Bluetooth"
                device.name?.equals(target.name, ignoreCase = true) != true -> "Il nome del dispositivo non corrisponde al profilo salvato"
                else -> null
            }
        } catch (_: SecurityException) {
            "Autorizzazione Bluetooth necessaria"
        } catch (_: IllegalArgumentException) {
            "Profilo Bluetooth non valido: seleziona nuovamente OBDII"
        }
    }

    @SuppressLint("MissingPermission")
    protected open fun createSession(scope: CoroutineScope): DiagnosticsSession = DiagnosticsSession(scope, store, ::prerequisite, results = (application as MultiplaApplication).diagnosisResults) { target, log ->
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth non disponibile")
        val transport = RfcommTransport(adapter.getRemoteDevice(target.address))
        val elm = ElmSession(transport, log)
        try {
            ElmAdapterLink(elm, transport, elm.connectAdapter())
        } catch (e: Exception) {
            elm.close()
            throw e
        }
    }

    override fun onCreate() {
        super.onCreate()
        scope = createScope()
        store = AndroidConnectionStore(this)
        session = createSession(scope)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Sessione diagnostica OBD", NotificationManager.IMPORTANCE_LOW),
        )
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        scope.launch {
            session.state.collect { state ->
                considerForegroundState()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean = true

    /** Called only by a visible Activity; an existing foreground session handles its own retries. */
    fun requestOpenFromUi() {
        val target = store.target()
        if (session.state.value.demo || store.manualRequired() || session.state.value.connection.phase == ConnectionPhase.STOPPED || target == null || prerequisite(target) != null) {
            session.appOpened()
        } else {
            startForegroundService(Intent(this, DiagnosticService::class.java).setAction(ACTION_OPEN))
        }
    }

    fun requestSaveFromUi(target: SavedTarget) {
        if (hasConnectPermission() && !store.manualRequired() && prerequisite(target) == null) {
            startForegroundService(
                Intent(this, DiagnosticService::class.java).setAction(ACTION_SAVE)
                    .putExtra(EXTRA_NAME, target.name).putExtra(EXTRA_ADDRESS, target.address),
            )
        } else {
            session.saveTarget(target)
        }
    }

    fun requestManualFromUi() {
        val target = store.target()
        if (target != null && prerequisite(target) == null) {
            startForegroundService(Intent(this, DiagnosticService::class.java).setAction(ACTION_MANUAL))
        } else {
            session.manualConnect()
        }
    }

    /** Mantiene la demo nello stesso servizio anche mentre il selettore documenti copre l’Activity. */
    fun requestDemoFromUi() {
        startService(Intent(this, DiagnosticService::class.java).setAction(ACTION_DEMO))
    }

    fun requestExitDemoFromUi() {
        stopSelf()
        session.exitDemo()
        requestOpenFromUi()
    }

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DEMO) {
            session.demo()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            session.disconnect()
            releaseForeground()
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasConnectPermission()) {
            session.message("Permesso Bluetooth necessario")
            session.disconnect()
            releaseForeground()
            return START_NOT_STICKY
        }
        pendingActions.incrementAndGet()
        try {
            foreground = true
            val text = DiagnosticsSession.label(session.connectionState)
            lastNotification = text
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            val command = when (intent.action) {
                ACTION_OPEN -> session.appOpened()

                ACTION_MANUAL -> session.manualConnect()

                ACTION_SAVE -> {
                    val name = intent.getStringExtra(EXTRA_NAME)
                    val address = intent.getStringExtra(EXTRA_ADDRESS)
                    if (name != null && address != null) {
                        session.saveTarget(SavedTarget(name, address))
                    } else {
                        session.message("Profilo Bluetooth incompleto")
                        null
                    }
                }

                else -> null
            }
            if (command == null) {
                pendingActions.decrementAndGet()
                releaseForeground()
            } else {
                scope.launch {
                    try {
                        command.await()
                    } finally {
                        pendingActions.decrementAndGet()
                        considerForegroundState()
                    }
                }
            }
        } catch (e: Exception) {
            pendingActions.decrementAndGet()
            session.message("Impossibile avviare il servizio di sessione: ${e.message}")
            session.disconnect()
            releaseForeground()
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun considerForegroundState() {
        val connection = session.connectionState
        if (session.state.value.demo && connection.phase != ConnectionPhase.STOPPED) {
            // La demo non usa il tipo connectedDevice né richiede il permesso Bluetooth.
            foreground = false
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else if (pendingActions.get() == 0 && connection.phase in setOf(ConnectionPhase.MANUAL_REQUIRED, ConnectionPhase.CONNECTION_FAILED, ConnectionPhase.STOPPED)) {
            releaseForeground()
        } else if (foreground) {
            if (connection.busy || connection.connected || session.state.value.busy) keepAwake() else releaseWakeLock()
            updateNotification(DiagnosticsSession.label(connection) + connection.detail?.let { " · $it" }.orEmpty())
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, DiagnosticService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_obd)
            .setContentTitle("Multipla OBD · sessione attiva").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Interrompi", stop).build()).build()
    }

    @Synchronized
    private fun updateNotification(text: String) {
        if (foreground && text != lastNotification) {
            lastNotification = text
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
        }
    }

    @Synchronized
    private fun keepAwake() {
        if (wakeJob?.isActive == true) return
        val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MultiplaOBD:session").apply {
                setReferenceCounted(false)
                wakeLock = this
            }
        wakeJob = scope.launch {
            while (isActive && foreground) {
                synchronized(this@DiagnosticService) { if (isActive && foreground) lock.acquire(120_000) }
                delay(60_000)
            }
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        wakeJob?.cancel()
        wakeJob = null
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    @Synchronized
    private fun releaseForeground() {
        foreground = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        unregisterReceiver(receiver)
        session.close()
        releaseForeground()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN = "it.multipla.obd.OPEN"
        const val ACTION_MANUAL = "it.multipla.obd.MANUAL"
        const val ACTION_SAVE = "it.multipla.obd.SAVE_TARGET"
        const val ACTION_DEMO = "it.multipla.obd.DEMO"
        const val ACTION_STOP = "it.multipla.obd.STOP"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_ADDRESS = "address"
        private const val CHANNEL = "obd-session"
        private const val NOTIFICATION_ID = 10
    }
}
