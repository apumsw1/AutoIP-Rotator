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

    @Volatile
    private var currentState = Tunnel.State.DOWN

    private val tunnel = object : Tunnel {

        override fun getName(): String {
            return "ULTRA-VPN"
        }

        override fun onStateChange(newState: Tunnel.State) {
            currentState = newState
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

        val input = ByteArrayInputStream(
            configText.toByteArray(Charsets.UTF_8)
        )

        val config = input.use {
            Config.parse(it)
        }

        backend.setState(
            tunnel,
            Tunnel.State.UP,
            config
        )

        currentState = backend.getState(tunnel)
    }

    @Synchronized
    fun disconnect() {

        backend.setState(
            tunnel,
            Tunnel.State.DOWN,
            null
        )

        currentState = Tunnel.State.DOWN
    }

    fun isConnected(): Boolean {
        return backend.getState(tunnel) == Tunnel.State.UP
    }

    fun getConnectionState(): Tunnel.State {
        return backend.getState(tunnel)
    }

    fun getLastKnownState(): Tunnel.State {
        return currentState
    }
}
