package com.example.autoiprotator.vpn

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.TrafficStats
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import engine.Engine
import engine.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.torproject.jni.TorService
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale

data class OpenSourceTunnelSnapshot(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val countryCode: String = "AUTO",
    val socksPort: Int = -1,
    val statusNote: String = "",
    val errorMessage: String? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val connectionSeq: Long = 0L
)

object OpenSourceVpnState {
    private val _snapshot = MutableStateFlow(OpenSourceTunnelSnapshot())
    val snapshot = _snapshot.asStateFlow()

    @Volatile
    private var baselineRx: Long = 0L

    @Volatile
    private var baselineTx: Long = 0L

    internal fun markConnecting(countryCode: String, note: String) {
        _snapshot.value = OpenSourceTunnelSnapshot(
            connected = false,
            connecting = true,
            countryCode = countryCode,
            socksPort = -1,
            statusNote = note,
            errorMessage = null
        )
    }

    internal fun updateProgress(note: String) {
        val current = _snapshot.value
        _snapshot.value = current.copy(statusNote = note)
    }

    internal fun markConnected(countryCode: String, socksPort: Int) {
        val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        baselineRx = totalRx
        baselineTx = totalTx
        val nextSeq = _snapshot.value.connectionSeq + 1L
        _snapshot.value = OpenSourceTunnelSnapshot(
            connected = true,
            connecting = false,
            countryCode = countryCode,
            socksPort = socksPort,
            statusNote = "TOR_ONION_ACTIVE",
            errorMessage = null,
            rxBytes = 0L,
            txBytes = 0L,
            connectionSeq = nextSeq
        )
    }

    internal fun markDisconnected(errorMessage: String? = null) {
        val keepSeq = _snapshot.value.connectionSeq
        _snapshot.value = OpenSourceTunnelSnapshot(
            connected = false,
            connecting = false,
            countryCode = "AUTO",
            socksPort = -1,
            statusNote = "",
            errorMessage = errorMessage,
            rxBytes = 0L,
            txBytes = 0L,
            connectionSeq = keepSeq
        )
    }

    internal fun sampleTraffic(): Pair<Long, Long> {
        val snap = _snapshot.value
        if (!snap.connected) return 0L to 0L
        val rx = (TrafficStats.getTotalRxBytes() - baselineRx).coerceAtLeast(0L)
        val tx = (TrafficStats.getTotalTxBytes() - baselineTx).coerceAtLeast(0L)
        return rx to tx
    }
}

class OpenSourceVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var torService: TorService? = null

    @Volatile
    private var torBound = false

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    @Volatile
    private var socksBridge: LocalTorSocksBridge? = null

    @Volatile
    private var tun2SocksStarted = false

    @Volatile
    private var statusReceiverRegistered = false

    @Volatile
    private var statusDeferred: CompletableDeferred<Int>? = null

    @Volatile
    private var latestTorError: String? = null

    private val torStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                TorService.ACTION_STATUS -> {
                    val status = intent.getStringExtra(TorService.EXTRA_STATUS)
                    if (status == TorService.STATUS_ON) {
                        val port = torService?.socksPort?.takeIf { it > 0 }
                            ?: TorService.socksPort.takeIf { it > 0 }
                            ?: -1
                        if (port > 0) {
                            statusDeferred?.complete(port)
                        }
                    } else if (status == TorService.STATUS_STARTING) {
                        OpenSourceVpnState.updateProgress("Bootstrapping Tor circuit...")
                    }
                }
                TorService.ACTION_ERROR -> {
                    val msg = intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: "Tor daemon encountered an error."
                    latestTorError = msg
                    statusDeferred?.completeExceptionally(IllegalStateException(msg))
                }
            }
        }
    }

    private val torConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TorService.LocalBinder
            val instance = binder?.service
            torService = instance
            val port = instance?.socksPort?.takeIf { it > 0 }
                ?: TorService.socksPort.takeIf { it > 0 }
                ?: -1
            if (port > 0) {
                statusDeferred?.complete(port)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torService = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    teardownAll()
                    OpenSourceVpnState.markDisconnected()
                    stopSelf()
                }
            }
            ACTION_ROTATE -> {
                val targetCountry = normalizeCountry(intent.getStringExtra(EXTRA_COUNTRY_CODE))
                serviceScope.launch {
                    rotateExitLive(targetCountry)
                }
            }
            ACTION_CONNECT -> {
                val targetCountry = normalizeCountry(intent.getStringExtra(EXTRA_COUNTRY_CODE))
                serviceScope.launch {
                    connectInternal(targetCountry)
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun connectInternal(countryCode: String) {
        val currentSnap = OpenSourceVpnState.snapshot.value
        if (currentSnap.connected && torService != null && tunInterface != null) {
            rotateExitLive(countryCode)
            return
        }

        teardownAll()
        latestTorError = null
        OpenSourceVpnState.markConnecting(countryCode, "Starting open-source Tor network...")

        try {
            val dnsPort = findAvailableUdpPort()
            writeTorConfig(countryCode = countryCode, dnsPort = dnsPort)

            val deferred = CompletableDeferred<Int>()
            statusDeferred = deferred
            registerTorReceiverIfNeeded()
            TorService.setBroadcastPackageName(packageName)

            val bindIntent = Intent(this, TorService::class.java)
            torBound = bindService(bindIntent, torConnection, Context.BIND_AUTO_CREATE)
            if (!torBound) {
                throw IllegalStateException("Could not bind TorService.")
            }

            // Poll fallback in case broadcast arrived before receiver registration
            val pollJob = serviceScope.launch {
                while (!deferred.isCompleted) {
                    val service = torService
                    val port = service?.socksPort?.takeIf { it > 0 }
                        ?: TorService.socksPort.takeIf { it > 0 }
                        ?: -1
                    val ctrl = service?.torControlConnection
                    if (ctrl != null) {
                        val bootstrap = runCatching { ctrl.getInfo("status/bootstrap-phase") }.getOrNull()
                        if (!bootstrap.isNullOrBlank()) {
                            val pct = Regex("PROGRESS=(\\d+)").find(bootstrap)?.groupValues?.getOrNull(1)
                            if (pct != null) {
                                OpenSourceVpnState.updateProgress("Tor bootstrap $pct% ($countryCode)...")
                            }
                            if (bootstrap.contains("PROGRESS=100") && port > 0) {
                                deferred.complete(port)
                                break
                            }
                        }
                    }
                    if (port > 0 && ctrl != null) {
                        val circ = runCatching { ctrl.getInfo("status/circuit-established") }.getOrNull()
                        if (circ?.trim() == "1") {
                            deferred.complete(port)
                            break
                        }
                    }
                    delay(450L)
                }
            }

            val torSocksPort = withTimeoutOrNull(45_000L) { deferred.await() }
            pollJob.cancel()

            if (torSocksPort == null || torSocksPort <= 0) {
                throw IllegalStateException(
                    latestTorError ?: "Tor circuit bootstrap timed out. Check internet connection."
                )
            }

            // Start local SOCKS5+DNS bridge for tun2socks
            val bridge = LocalTorSocksBridge(
                torSocksPort = torSocksPort,
                torDnsPort = dnsPort
            )
            bridge.start()
            socksBridge = bridge

            // Build Android TUN interface (exclude our own app so Tor & IP-trace stay loop-free)
            val builder = Builder()
                .setSession("Ultra Multi VPN (Open Tor)")
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
            runCatching { builder.addDisallowedApplication(packageName) }

            val pfd = builder.establish()
                ?: throw IllegalStateException("Android denied VPN TUN interface creation.")
            tunInterface = pfd

            val key = Key().apply {
                setMTU(1500L)
                setDevice("fd://${pfd.fd}")
                setProxy("socks5://127.0.0.1:${bridge.listenPort}")
                setLogLevel("silent")
            }
            Engine.insert(key)
            tun2SocksStarted = true
            Thread({
                runCatching { Engine.start() }
                    .onFailure { Log.w(TAG, "tun2socks Engine exited: ${it.message}") }
            }, "tun2socks-engine").start()

            OpenSourceVpnState.markConnected(countryCode = countryCode, socksPort = torSocksPort)
        } catch (e: Exception) {
            Log.e(TAG, "Open-source Tor VPN connect failed", e)
            teardownAll()
            OpenSourceVpnState.markDisconnected(
                errorMessage = e.message ?: "Failed to establish open-source Tor tunnel."
            )
            stopSelf()
        }
    }

    private suspend fun rotateExitLive(countryCode: String) {
        val ctrl = torService?.torControlConnection
        if (ctrl == null) {
            connectInternal(countryCode)
            return
        }
        try {
            OpenSourceVpnState.updateProgress("Rotating Tor exit node ($countryCode)...")
            if (countryCode == "AUTO") {
                ctrl.resetConf(listOf("ExitNodes", "StrictNodes"))
            } else {
                val cc = countryCode.lowercase(Locale.US)
                ctrl.setConf(listOf("ExitNodes {$cc}", "StrictNodes 1"))
            }
            ctrl.signal("NEWNYM")
            val port = torService?.socksPort?.takeIf { it > 0 }
                ?: OpenSourceVpnState.snapshot.value.socksPort
            delay(1200L)
            OpenSourceVpnState.markConnected(countryCode = countryCode, socksPort = port)
        } catch (e: Exception) {
            Log.w(TAG, "Live exit rotation fallback to reconnect: ${e.message}")
            connectInternal(countryCode)
        }
    }

    private fun writeTorConfig(countryCode: String, dnsPort: Int) {
        val torrc = TorService.getTorrc(this)
        torrc.parentFile?.mkdirs()
        val lines = mutableListOf(
            "DNSPort 127.0.0.1:$dnsPort",
            "AutomapHostsOnResolve 1",
            "VirtualAddrNetworkIPv4 10.192.0.0/10",
            "ClientOnly 1",
            "AvoidDiskWrites 1"
        )
        if (countryCode != "AUTO" && countryCode.length == 2) {
            lines.add("ExitNodes {${countryCode.lowercase(Locale.US)}}")
            lines.add("StrictNodes 1")
        }
        torrc.writeText(lines.joinToString("\n") + "\n")
    }

    private fun registerTorReceiverIfNeeded() {
        if (statusReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(TorService.ACTION_STATUS)
            addAction(TorService.ACTION_ERROR)
        }
        ContextCompat.registerReceiver(
            this,
            torStatusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        statusReceiverRegistered = true
    }

    private fun teardownAll() {
        if (tun2SocksStarted) {
            runCatching { Engine.stop() }
            tun2SocksStarted = false
        }
        runCatching { tunInterface?.close() }
        tunInterface = null

        runCatching { socksBridge?.stop() }
        socksBridge = null

        if (statusReceiverRegistered) {
            runCatching { unregisterReceiver(torStatusReceiver) }
            statusReceiverRegistered = false
        }
        if (torBound) {
            runCatching { unbindService(torConnection) }
            torBound = false
        }
        torService = null
        statusDeferred = null
    }

    private fun findAvailableUdpPort(): Int {
        return runCatching {
            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        }.getOrDefault(5400)
    }

    private fun normalizeCountry(raw: String?): String {
        val clean = raw?.trim()?.uppercase(Locale.US).orEmpty()
        return if (clean.length == 2 && clean.all { it in 'A'..'Z' }) clean else "AUTO"
    }

    override fun onRevoke() {
        teardownAll()
        OpenSourceVpnState.markDisconnected("VPN permission revoked by system.")
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownAll()
        OpenSourceVpnState.markDisconnected()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OpenSourceVpnService"
        private const val ACTION_CONNECT = "com.example.autoiprotator.vpn.OPEN_CONNECT"
        private const val ACTION_ROTATE = "com.example.autoiprotator.vpn.OPEN_ROTATE"
        private const val ACTION_DISCONNECT = "com.example.autoiprotator.vpn.OPEN_DISCONNECT"
        private const val EXTRA_COUNTRY_CODE = "extra_country_code"

        fun connectIntent(context: Context, countryCode: String): Intent =
            Intent(context, OpenSourceVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_COUNTRY_CODE, countryCode)
            }

        fun rotateIntent(context: Context, countryCode: String): Intent =
            Intent(context, OpenSourceVpnService::class.java).apply {
                action = ACTION_ROTATE
                putExtra(EXTRA_COUNTRY_CODE, countryCode)
            }

        fun disconnectIntent(context: Context): Intent =
            Intent(context, OpenSourceVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
    }
}
