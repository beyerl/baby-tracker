package de.beyerl.babytracker.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.ServerSocket

/** What the sync screen shows. */
data class SyncStatus(
    val paired: Boolean = false,
    val running: Boolean = false,
    val peerFound: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0L,
    /** Entries changed on this phone by the last sync. */
    val lastChanged: Int? = null,
    val error: String? = null,
)

/**
 * Phone-to-phone sync over the local network, no server involved.
 *
 * While in use (app in the foreground or [SyncService] running) a paired
 * phone listens on a TCP port, announces it via NSD (mDNS) as
 * [SERVICE_TYPE] and looks for the other phone's announcement. A sync is one
 * [SyncProtocol] round trip, authenticated and encrypted with the pairing key.
 * It runs on start, when the peer is found, after local changes, every
 * [PERIOD_MS] and on demand.
 */
@OptIn(FlowPreview::class)
class SyncManager(
    context: Context,
    private val repository: EventRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    val prefs = SyncPrefs(appContext)

    private val _status = MutableStateFlow(SyncStatus(paired = prefs.isPaired, lastSyncAt = prefs.lastSyncAt))
    val status: StateFlow<SyncStatus> = _status

    private val store = object : SyncStore {
        override suspend fun allForSync(): List<Event> = repository.getAllForSync()
        override suspend fun applyRemote(events: List<Event>): Int = repository.applyRemote(events)
    }

    /** Serialises outgoing syncs of this phone. */
    private val lock = Mutex()
    private var users = 0
    private var runJob: Job? = null
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var ownServiceName: String? = null
    @Volatile private var peer: Pair<InetAddress, Int>? = null
    private var stampAfterSync: String? = null

    /** Called by the activity (onStart) and the background service; sync runs while anyone holds it. */
    @Synchronized
    fun acquire() {
        users++
        if (users == 1) start()
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) stop()
    }

    /** Stores the pairing key (shown or scanned) and starts syncing. */
    @Synchronized
    fun pair(key: ByteArray) {
        prefs.key = key
        _status.update { it.copy(paired = true, error = null) }
        if (users > 0) {
            stop()
            start()
        }
    }

    @Synchronized
    fun unpair() {
        stop()
        prefs.key = null
        _status.value = SyncStatus(paired = false, lastSyncAt = prefs.lastSyncAt)
    }

    fun syncNow() {
        scope.launch(Dispatchers.IO) { syncWithPeer(waitForPeer = true) }
    }

    private fun start() {
        val key = prefs.key ?: return
        runJob = scope.launch(Dispatchers.IO) {
            val socket = ServerSocket(0)
            server = socket
            _status.update { it.copy(running = true) }
            register(socket.localPort)
            discover()
            launch { acceptLoop(socket, key) }
            launch {
                // Local change (new, edited or deleted entry) -> push it to the other phone.
                repository.observeChanges().debounce(3_000).collect { stamp ->
                    if (stamp != stampAfterSync) syncWithPeer(waitForPeer = false)
                }
            }
            while (isActive) {
                delay(PERIOD_MS)
                syncWithPeer(waitForPeer = false)
            }
        }
    }

    private fun stop() {
        runJob?.cancel()
        runJob = null
        runCatching { server?.close() }
        server = null
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        peer = null
        _status.update { it.copy(running = false, peerFound = false, syncing = false) }
    }

    private suspend fun acceptLoop(socket: ServerSocket, key: ByteArray) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            // No [lock] here: if both phones start a sync at the same moment, each
            // must still be able to answer the other. Merges are atomic Room
            // transactions and last-writer-wins, so running them concurrently is safe.
            scope.launch(Dispatchers.IO) {
                finish(runCatching { SyncProtocol.serve(client, key, prefs.deviceId, store) })
            }
        }
    }

    private suspend fun syncWithPeer(waitForPeer: Boolean) {
        val key = prefs.key ?: return
        val target = peer ?: if (waitForPeer) {
            withTimeoutOrNull(PEER_WAIT_MS) { while (peer == null) delay(250); peer }
        } else {
            null
        }
        if (target == null) {
            if (waitForPeer) _status.update { it.copy(error = "Anderes Handy nicht gefunden – ist es im selben WLAN und die App dort geöffnet oder im Hintergrund aktiv?") }
            return
        }
        lock.withLock {
            _status.update { it.copy(syncing = true) }
            val result = runCatching {
                SyncProtocol.runClient(target.first.hostAddress!!, target.second, key, prefs.deviceId, store)
            }
            if (result.isFailure) peer = null // stale address; rediscover
            finish(result)
        }
    }

    private suspend fun finish(result: Result<SyncResult>) {
        result.onSuccess { r ->
            val now = System.currentTimeMillis()
            prefs.lastSyncAt = now
            _status.update { it.copy(syncing = false, lastSyncAt = now, lastChanged = r.changedHere, error = null) }
        }.onFailure { e ->
            Log.w(TAG, "sync failed", e)
            _status.update { it.copy(syncing = false, error = e.message ?: e.javaClass.simpleName) }
        }
        stampAfterSync = runCatching { repository.currentChangeStamp() }.getOrNull()
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "BabyTracker-" + prefs.deviceId.take(8)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                ownServiceName = info.serviceName
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun discover() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                val own = ownServiceName ?: "BabyTracker-" + prefs.deviceId.take(8)
                if (info.serviceName == own || !info.serviceName.startsWith("BabyTracker-")) return
                resolve(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                peer = null
                _status.update { it.copy(peerFound = false) }
            }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Suppress("DEPRECATION") // resolveService is the only option below API 34.
    private fun resolve(info: NsdServiceInfo) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host ?: return
                val isNew = peer == null
                peer = host to resolved.port
                _status.update { it.copy(peerFound = true) }
                if (isNew) scope.launch(Dispatchers.IO) { syncWithPeer(waitForPeer = false) }
            }
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD resolve failed: $errorCode")
            }
        })
    }

    companion object {
        const val SERVICE_TYPE = "_babytracker._tcp."
        private const val TAG = "SyncManager"
        private const val PERIOD_MS = 5 * 60_000L
        private const val PEER_WAIT_MS = 10_000L
    }
}
