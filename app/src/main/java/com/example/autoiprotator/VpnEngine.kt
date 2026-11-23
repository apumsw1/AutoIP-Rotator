package com.example.autoiprotator

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.autoiprotator.model.TunnelProtocol
import com.example.autoiprotator.vpn.OpenSourceVpnService
import com.example.autoiprotator.vpn.OpenSourceVpnState
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

data class TunnelTrafficStats(
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L
)

data class GeneratedWireGuardKeys(
    val privateKey: String,
    val publicKey: String
)

class VpnEngine(context: Context) {

    private val appContext = context.applicationContext
    private val backend: GoBackend = getOrCreateBackend(appContext)

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

        if (isOpenSourceConnected()) {
            disconnectOpenSource()
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

    suspend fun connectOpenSourceTor(
        countryCode: String,
        onProgress: (String) -> Unit = {}
    ) {
        check(VpnService.prepare(appContext) == null) {
            "Android VPN permission is required."
        }
        if (backend.getState(tunnel) == Tunnel.State.UP) {
            synchronized(this) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            }
        }

        val snap = OpenSourceVpnState.snapshot.value
        val initialSeq = snap.connectionSeq
        val intent = if (snap.connected) {
            OpenSourceVpnService.rotateIntent(appContext, countryCode)
        } else {
            OpenSourceVpnService.connectIntent(appContext, countryCode)
        }
        appContext.startService(intent)

        val deadline = System.currentTimeMillis() + 50_000L
        var lastNote = ""
        while (System.currentTimeMillis() < deadline) {
            val current = OpenSourceVpnState.snapshot.value
            if (current.statusNote.isNotBlank() && current.statusNote != lastNote) {
                lastNote = current.statusNote
                onProgress(lastNote)
            }
            if (current.connected && current.connectionSeq > initialSeq) {
                return
            }
            if (!current.connecting && !current.connected && !current.errorMessage.isNullOrBlank()) {
                throw IllegalStateException(current.errorMessage)
            }
            delay(250L)
        }
        throw IllegalStateException("Tor circuit bootstrap timed out. Please retry.")
    }

    fun disconnectOpenSource() {
        appContext.startService(OpenSourceVpnService.disconnectIntent(appContext))
    }

    @Synchronized
    fun disconnect() {
        if (backend.getState(tunnel) != Tunnel.State.DOWN) {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        }
        val torSnap = OpenSourceVpnState.snapshot.value
        if (torSnap.connected || torSnap.connecting) {
            disconnectOpenSource()
        }
    }

    fun isWireGuardConnected(): Boolean = backend.getState(tunnel) == Tunnel.State.UP

    fun isOpenSourceConnected(): Boolean = OpenSourceVpnState.snapshot.value.connected

    fun isConnected(): Boolean = isWireGuardConnected() || isOpenSourceConnected()

    fun activeProtocol(): TunnelProtocol =
        if (isOpenSourceConnected()) TunnelProtocol.TOR else TunnelProtocol.WIREGUARD

    fun activeTorSocksPort(): Int? =
        OpenSourceVpnState.snapshot.value
            .takeIf { it.connected && it.socksPort > 0 }
            ?.socksPort

    fun getConnectionState(): Tunnel.State = backend.getState(tunnel)

    fun getStatistics(): TunnelTrafficStats {
        if (isOpenSourceConnected()) {
            val (rx, tx) = OpenSourceVpnState.sampleTraffic()
            return TunnelTrafficStats(rxBytes = rx, txBytes = tx)
        }
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
        @Volatile
        private var sharedBackend: GoBackend? = null

        @Volatile
        private var stateListener: ((Tunnel.State) -> Unit)? = null

        private val tunnel = object : Tunnel {
            override fun getName(): String = "ultra_multi_wg0"

            override fun onStateChange(newState: Tunnel.State) {
                stateListener?.invoke(newState)
            }
        }

        private fun getOrCreateBackend(appContext: Context): GoBackend {
            return sharedBackend ?: synchronized(this) {
                sharedBackend ?: GoBackend(appContext.applicationContext).also {
                    sharedBackend = it
                }
            }
        }

        fun generateClientKeyPair(): GeneratedWireGuardKeys {
            val keyPair = KeyPair()
            return GeneratedWireGuardKeys(
                privateKey = keyPair.privateKey.toBase64(),
                publicKey = keyPair.publicKey.toBase64()
            )
        }
    }
}
