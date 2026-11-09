package com.example.autoiprotator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.net.InetAddress

class OpenVpnVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT =
            "com.example.autoiprotator.OPENVPN_CONNECT"
        const val ACTION_DISCONNECT =
            "com.example.autoiprotator.OPENVPN_DISCONNECT"
        const val ACTION_STATUS =
            "com.example.autoiprotator.OPENVPN_STATUS"

        const val EXTRA_CONFIG = "openvpn_config"
        const val EXTRA_CONNECTED = "openvpn_connected"
        const val EXTRA_MESSAGE = "openvpn_message"

        private const val TAG = "AutoIPOpenVPN"
        private const val CHANNEL_ID = "autoip_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val STATUS_INTERVAL = 1200L
    }

    @Volatile
    private var vpnBuilder: Builder? = null

    @Volatile
    private var connected = false

    @Volatile
    private var monitoringNativeStatus = false

    @Volatile
    private var lastNativeStatus = ""

    private val statusHandler = Handler(Looper.getMainLooper())

    private val nativeStatusMonitor = object : Runnable {
        override fun run() {
            if (!monitoringNativeStatus) return

            val status = OpenVpn3Bridge.status()
                .getOrNull()
                ?.trim()
                .orEmpty()

            if (status.isNotBlank() && status != lastNativeStatus) {
                lastNativeStatus = status
                Log.i(TAG, "OpenVPN status: $status")

                val normalized = status
                    .substringBefore(":")
                    .trim()
                    .uppercase()

                val isConnectedEvent =
                    normalized == "CONNECTED" ||
                    normalized == "CONNECTION_ESTABLISHED"

                val isDisconnectedEvent =
                    normalized == "DISCONNECTED" ||
                    normalized == "CONNECTION_CLOSED" ||
                    normalized == "CONNECTION_TIMEOUT" ||
                    normalized == "CONNECTION_FAILED" ||
                    normalized == "AUTH_FAILED" ||
                    normalized == "AUTHENTICATION_FAILED"

                val isFatalMessage = listOf(
                    "authentication failed",
                    "auth_failed",
                    "connection timeout",
                    "tls error",
                    "server poll timeout",
                    "connection error:",
                    "fatal error:"
                ).any {
                    status.contains(it, ignoreCase = true)
                }

                when {
                    isConnectedEvent -> {
                        connected = true
                        publishStatus(true, "Riseup VPN connected")
                        updateNotification("Riseup VPN connected")
                    }

                    isDisconnectedEvent || isFatalMessage -> {
                        connected = false
                        publishStatus(false, status)
                        updateNotification(status.take(100))

                        monitoringNativeStatus = false
                        statusHandler.removeCallbacks(this)

                        disconnectNativeOnly()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return
                    }

                    else -> {
                        if (!connected) {
                            publishStatus(false, status)
                            updateNotification(status.take(100))
                        }
                    }
                }
            }

            if (monitoringNativeStatus) {
                statusHandler.postDelayed(
                    this,
                    STATUS_INTERVAL
                )
            }
        }
    }

    private fun startNativeStatusMonitor() {
        monitoringNativeStatus = true
        lastNativeStatus = ""
        statusHandler.removeCallbacks(nativeStatusMonitor)
        statusHandler.post(nativeStatusMonitor)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {

            ACTION_CONNECT -> {
                val config =
                    intent.getStringExtra(EXTRA_CONFIG)

                if (config.isNullOrBlank()) {
                    publishStatus(
                        false,
                        "OpenVPN configuration is empty"
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(
                            "Connecting to Riseup VPN..."
                        )
                    )
                } catch (error: Exception) {
                    Log.e(TAG, "Foreground start failed", error)
                    publishStatus(
                        false,
                        error.message ?: "Foreground service failed"
                    )
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                publishStatus(
                    false,
                    "Starting OpenVPN connection..."
                )

                connectVpn(config)
            }

            ACTION_DISCONNECT -> {
                disconnectVpn()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun publishStatus(
        isConnected: Boolean,
        message: String
    ) {
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_CONNECTED, isConnected)
            putExtra(EXTRA_MESSAGE, message)
        }

        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoIP VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN connection status"
                setShowBadge(false)
            }

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        message: String
    ): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AutoIP Rotator")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AutoIP Rotator")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(message: String) {
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification(message)
        )
    }

    private fun connectVpn(config: String) {
        Thread({
            try {
                disconnectNativeOnly()

                val result =
                    OpenVpn3Bridge.start(config, this)

                result.onSuccess {
                    Log.i(TAG, "OpenVPN engine started")

                    updateNotification(
                        "Waiting for OpenVPN connection..."
                    )

                    publishStatus(
                        false,
                        "Waiting for OpenVPN connection..."
                    )

                    startNativeStatusMonitor()
                }

                result.onFailure { error ->
                    Log.e(TAG, "OpenVPN start failed", error)

                    publishStatus(
                        false,
                        error.message ?: "OpenVPN start failed"
                    )

                    disconnectNativeOnly()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

            } catch (error: Exception) {
                Log.e(TAG, "OpenVPN connection error", error)

                publishStatus(
                    false,
                    error.message ?: "VPN connection error"
                )

                disconnectNativeOnly()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "OpenVPN-Starter").start()
    }

    private fun disconnectNativeOnly() {
        monitoringNativeStatus = false
        statusHandler.removeCallbacks(nativeStatusMonitor)

        connected = false
        vpnBuilder = null

        runCatching {
            OpenVpn3Bridge.stop()
        }
    }

    private fun disconnectVpn() {
        disconnectNativeOnly()

        publishStatus(false, "VPN disconnected")
        Log.i(TAG, "VPN disconnect requested")
    }

    @Synchronized
    fun nativeNewTunnel(): Boolean {
        return try {
            vpnBuilder =
                Builder().setSession("AutoIP Rotator")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Tunnel creation failed", error)
            false
        }
    }

    fun nativeSetLayer(layer: Int): Boolean {
        return layer == 3
    }

    fun nativeSetRemoteAddress(
        address: String,
        ipv6: Boolean
    ): Boolean {
        return address.isNotBlank()
    }

    @Synchronized
    fun nativeAddAddress(
        address: String,
        prefix: Int,
        gateway: String,
        ipv6: Boolean,
        net30: Boolean
    ): Boolean {
        return try {
            require(address.isNotBlank())
            require(prefix in 0..if (ipv6) 128 else 32)

            vpnBuilder?.addAddress(address, prefix) != null
        } catch (error: Exception) {
            Log.e(TAG, "Add address failed", error)
            false
        }
    }

    fun nativeSetRouteMetricDefault(
        metric: Int
    ): Boolean {
        return true
    }

    @Synchronized
    fun nativeRerouteGateway(
        ipv4: Boolean,
        ipv6: Boolean,
        flags: Int
    ): Boolean {
        return try {
            val builder = vpnBuilder ?: return false

            if (ipv4) {
                builder.addRoute("0.0.0.0", 0)
            }

            if (ipv6) {
                builder.addRoute("::", 0)
            }

            true
        } catch (error: Exception) {
            Log.e(TAG, "Default route setup failed", error)
            false
        }
    }

    @Synchronized
    fun nativeAddRoute(
        address: String,
        prefix: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        return try {
            vpnBuilder?.addRoute(address, prefix) != null
        } catch (error: Exception) {
            Log.e(TAG, "Add route failed", error)
            false
        }
    }

    @Synchronized
    fun nativeExcludeRoute(
        address: String,
        prefix: Int,
        metric: Int,
        ipv6: Boolean
    ): Boolean {
        return try {
            val builder = vpnBuilder ?: return false

            if (Build.VERSION.SDK_INT >= 33) {
                builder.excludeRoute(
                    android.net.IpPrefix(
                        InetAddress.getByName(address),
                        prefix
                    )
                )
            }

            true
        } catch (error: Exception) {
            Log.e(TAG, "Exclude route failed", error)
            false
        }
    }

    @Synchronized
    fun nativeAddDns(address: String): Boolean {
        return try {
            if (address.isBlank()) return false

            val dnsAddress =
                InetAddress.getByName(address)

            vpnBuilder?.addDnsServer(dnsAddress) != null
        } catch (error: Exception) {
            Log.e(TAG, "DNS setup failed: $address", error)
            false
        }
    }

    @Synchronized
    fun nativeSetMtu(mtu: Int): Boolean {
        return try {
            if (mtu !in 576..9000) return false

            vpnBuilder?.setMtu(mtu) != null
        } catch (error: Exception) {
            Log.e(TAG, "MTU setup failed", error)
            false
        }
    }

    @Synchronized
    fun nativeSetSession(name: String): Boolean {
        return try {
            if (name.isBlank()) return false

            vpnBuilder?.setSession(name) != null
        } catch (error: Exception) {
            Log.e(TAG, "Session setup failed", error)
            false
        }
    }

    @Synchronized
    fun nativeEstablish(): Int {
        return try {
            val builder = vpnBuilder ?: return -1

            val descriptor: ParcelFileDescriptor =
                builder.establish() ?: return -1

            vpnBuilder = null

            // TUN created does not mean OpenVPN is connected.
            // CONNECTED is published only after native engine status.

            Log.i(TAG, "Android VPN interface established")

            descriptor.detachFd()

        } catch (error: Exception) {
            Log.e(TAG, "VPN establish failed", error)

            publishStatus(
                false,
                error.message
                    ?: "VPN tunnel establishment failed"
            )

            -1
        }
    }

    fun nativeProtectSocket(fd: Int): Boolean {
        return try {
            protect(fd)
        } catch (error: Exception) {
            Log.e(TAG, "Socket protection failed", error)
            false
        }
    }

    @Synchronized
    fun nativeTeardown(disconnect: Boolean) {
        vpnBuilder = null
        connected = false

        Log.i(TAG, "Native tunnel teardown")

        publishStatus(
            false,
            "VPN tunnel disconnected"
        )
    }

    override fun onDestroy() {
        monitoringNativeStatus = false
        statusHandler.removeCallbacks(nativeStatusMonitor)

        disconnectVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()
    }
}
