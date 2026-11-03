
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
        override fun getName(): String = "AutoIPRotator"

        override fun onStateChange(newState: Tunnel.State) {
            // WireGuard manages tunnel state.
        }
    }

    fun getVpnPermissionIntent() =
        VpnService.prepare(appContext)

    fun connect(configText: String) {
        check(VpnService.prepare(appContext) == null) {
            "VPN permission is required first."
        }

        require(configText.isNotBlank()) {
            "WireGuard configuration is empty."
        }

        val input = ByteArrayInputStream(
            configText.toByteArray(Charsets.UTF_8)
        )

        val config = Config.parse(input)

        backend.setState(
            tunnel,
            Tunnel.State.UP,
            config
        )
    }

    fun disconnect() {
        backend.setState(
            tunnel,
            Tunnel.State.DOWN,
            null
        )
    }

    fun isConnected(): Boolean {
        return backend.getState(tunnel) == Tunnel.State.UP
    }
}
