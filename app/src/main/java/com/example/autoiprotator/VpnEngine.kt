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

        override fun getName(): String {
            return "ULTRA-VPN"
        }

        override fun onStateChange(newState: Tunnel.State) {
            // WireGuard tunnel state callback.
        }
    }

    /**
     * Returns the Android VPN permission intent.
     * If null is returned, permission is already granted.
     */
    fun getVpnPermissionIntent() =
        VpnService.prepare(appContext)

    /**
     * Connect using a WireGuard configuration.
     * Call this method from a background thread.
     */
    @Synchronized
    fun connect(configText: String) {

        check(VpnService.prepare(appContext) == null) {
            "Android VPN permission is required."
        }

        require(configText.isNotBlank()) {
            "WireGuard configuration is empty."
        }

        val inputStream = ByteArrayInputStream(
            configText.toByteArray(Charsets.UTF_8)
        )

        val config = inputStream.use {
            Config.parse(it)
        }

        backend.setState(
            tunnel,
            Tunnel.State.UP,
            config
        )
    }

    /**
     * Disconnect the active VPN tunnel.
     * Call this method from a background thread.
     */
    @Synchronized
    fun disconnect() {

        backend.setState(
            tunnel,
            Tunnel.State.DOWN,
            null
        )
    }

    /**
     * Check the current WireGuard tunnel state.
     */
    fun isConnected(): Boolean {
        return backend.getState(tunnel) == Tunnel.State.UP
    }

    /**
     * Get the current tunnel state.
     */
    fun getConnectionState(): Tunnel.State {
        return backend.getState(tunnel)
    }
}
