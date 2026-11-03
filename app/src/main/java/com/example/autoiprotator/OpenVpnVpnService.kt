
package com.example.autoiprotator

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class OpenVpnVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT =
            "com.example.autoiprotator.OPENVPN_CONNECT"

        const val ACTION_DISCONNECT =
            "com.example.autoiprotator.OPENVPN_DISCONNECT"

        const val EXTRA_CONFIG = "openvpn_config"
        private const val TAG = "AutoIPOpenVPN"
    }

    @Volatile
    private var vpnBuilder: Builder? = null

    @Volatile
    private var connected = false

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
                    Log.e(TAG, "OpenVPN configuration is empty")
                    stopSelf()
                    return START_NOT_STICKY
                }

                connectVpn(config)
            }

            ACTION_DISCONNECT -> {
                disconnectVpn()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun connectVpn(config: String) {
        Thread({
            try {
                disconnectVpn()

                val result =
                    OpenVpn3Bridge.start(config, this)

                result.onFailure {
                    Log.e(TAG, "OpenVPN start failed", it)
                    stopSelf()
                }

            } catch (error: Exception) {
                Log.e(TAG, "OpenVPN connection error", error)
                stopSelf()
            }
        }, "OpenVPN-Starter").start()
    }

    private fun disconnectVpn() {
        connected = false
        vpnBuilder = null

        runCatching {
            OpenVpn3Bridge.stop()
        }

        Log.i(TAG, "VPN disconnect requested")
    }

    /*
     * The following methods are called from Rust JNI.
     * OpenVPN Core controls the tunnel configuration.
     */

    @Synchronized
    fun nativeNewTunnel(): Boolean {
        return try {
            vpnBuilder = Builder()
                .setSession("AutoIP Rotator")
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

    fun nativeSetRouteMetricDefault(metric: Int): Boolean {
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
                    android.net.IpPrefix(address, prefix)
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

            vpnBuilder?.addDnsServer(address) != null
        } catch (error: Exception) {
            Log.e(TAG, "DNS setup failed", error)
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
            connected = true

            Log.i(TAG, "Android VPN interface established")

            descriptor.detachFd()

        } catch (error: Exception) {
            Log.e(TAG, "VPN establish failed", error)
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
    }

    override fun onDestroy() {
        disconnectVpn()
        super.onDestroy()
    }
}
