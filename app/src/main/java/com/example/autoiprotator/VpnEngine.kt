package com.example.autoiprotator

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.ByteArrayInputStream

data class TunnelTrafficStats(
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L
)

data class GeneratedWireGuardKeys(
    val privateKey: String,
    val publicKey: String
)

class VpnEngine(context: Context) {

    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)

    @Volatile
    private var stateListener: ((Tunnel.State) -> Unit)? = null

    private val tunnel = object : Tunnel {
        override fun getName(): String = "ultra_multi_wg0"

        override fun onStateChange(newState: Tunnel.State) {
            stateListener?.invoke(newState)
        }
    }

    fun setOnStateChanged(listener: ((Tunnel.State) -> Unit)?) {
        stateListener = listener
    }

    fun getVpnPermissionIntent(): Intent? = VpnService.prepare(appContext)

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

        if (backend.getState(tunnel) == Tunnel.State.UP) {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        }

        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    @Synchronized
    fun disconnect() {
        if (backend.getState(tunnel) != Tunnel.State.DOWN) {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        }
    }

    fun isConnected(): Boolean = backend.getState(tunnel) == Tunnel.State.UP

    fun getConnectionState(): Tunnel.State = backend.getState(tunnel)

    fun getStatistics(): TunnelTrafficStats {
        return try {
            val stats: Statistics = backend.getStatistics(tunnel)
            TunnelTrafficStats(
                rxBytes = stats.totalRx(),
                txBytes = stats.totalTx()
            )
        } catch (_: Exception) {
            TunnelTrafficStats()
        }
    }

    companion object {
        fun generateClientKeyPair(): GeneratedWireGuardKeys {
            val keyPair = KeyPair()
            return GeneratedWireGuardKeys(
                privateKey = keyPair.privateKey.toBase64(),
                publicKey = keyPair.publicKey.toBase64()
            )
        }
    }
}
