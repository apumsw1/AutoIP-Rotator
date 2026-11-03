
package com.example.autoiprotator

import android.content.Intent
import android.net.VpnService
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

    private var vpnInterface: ParcelFileDescriptor? = null
    private var connecting = false

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
        if (connecting) return
        connecting = true

        try {
            disconnectVpn()

            // The JNI engine must exist before a tunnel
            // can be reported as connected.
            val nativeReady = runCatching {
                OpenVpn3Bridge.status().getOrThrow()
            }

            if (nativeReady.isFailure) {
                Log.e(
                    TAG,
                    "OpenVPN native engine is missing",
                    nativeReady.exceptionOrNull()
                )
                stopSelf()
                return
            }

            val builder = Builder()
                .setSession("AutoIP Rotator")
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .setMtu(1500)

            val descriptor = builder.establish()
                ?: throw IllegalStateException(
                    "Android did not create the VPN interface"
                )

            vpnInterface = descriptor

            OpenVpn3Bridge.start(config, descriptor.fd)
                .onFailure { error ->
                    Log.e(TAG, "OpenVPN start failed", error)
                    disconnectVpn()
                    stopSelf()
                }

        } catch (error: Exception) {
            Log.e(TAG, "VPN connection failed", error)
            disconnectVpn()
            stopSelf()
        } finally {
            connecting = false
        }
    }

    private fun disconnectVpn() {
        runCatching {
            OpenVpn3Bridge.stop()
        }

        runCatching {
            vpnInterface?.close()
        }

        vpnInterface = null
    }

    override fun onDestroy() {
        disconnectVpn()
        super.onDestroy()
    }
}
