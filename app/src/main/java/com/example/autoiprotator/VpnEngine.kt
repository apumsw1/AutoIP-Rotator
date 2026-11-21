package com.example.autoiprotator

import android.content.Context
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class VpnEngine(context: Context) {

    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)

    private val tunnel = object : Tunnel {

        override fun getName(): String = "ULTRA-VPN"

        override fun onStateChange(newState: Tunnel.State) {
            // WireGuard state callback
        }
    }

    fun getVpnPermissionIntent() =
        VpnService.prepare(appContext)

    @Synchronized
    fun connect(configText: String) {
        check(VpnService.prepare(appContext) == null) {
            "Android VPN permission is required."
        }

        require(configText.isNotBlank()) {
            "WireGuard configuration is empty."
        }

        val config = ByteArrayInputStream(
            configText.toByteArray(Charsets.UTF_8)
        ).use { input ->
            Config.parse(input)
        }

        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    @Synchronized
    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun isConnected(): Boolean {
        return backend.getState(tunnel) == Tunnel.State.UP
    }

    fun getConnectionState(): Tunnel.State {
        return backend.getState(tunnel)
    }
}
