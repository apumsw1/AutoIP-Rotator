
package com.example.autoiprotator

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.IOException

class OpenVpnVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT =
            "com.example.autoiprotator.OPENVPN_CONNECT"
        const val ACTION_DISCONNECT =
            "com.example.autoiprotator.OPENVPN_DISCONNECT"
        const val EXTRA_CONFIG = "openvpn_config"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                connectVpn(config)
            }

            ACTION_DISCONNECT -> disconnectVpn()
        }

        return START_NOT_STICKY
    }

    private fun connectVpn(config: String) {
        try {
            disconnectVpn()

            val builder = Builder()
                .setSession("AutoIP Rotator")
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .setMtu(1500)

            val descriptor = builder.establish()
                ?: throw IOException("Android could not establish VPN interface.")

            vpnInterface = descriptor

            val result = OpenVpn3Bridge.start(
                config,
                descriptor.fd
            )

            result.onFailure { error ->
                disconnectVpn()
                throw IOException(
                    error.message ?: "OpenVPN native startup failed.",
                    error
                )
            }
        } catch (error: Exception) {
            disconnectVpn()
            stopSelf()
        }
    }

    private fun disconnectVpn() {
        runCatching { OpenVpn3Bridge.stop() }

        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    override fun onDestroy() {
        disconnectVpn()
        super.onDestroy()
    }
}
