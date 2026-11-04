package com.example.autoiprotator

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProtonGuestWireGuardTunnel(
    context: Context
) {

    private val backend = GoBackend(context.applicationContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val tunnel = object : Tunnel {
        override fun getName(): String = "ProtonGuest"

        override fun onStateChange(newState: Tunnel.State) {
            currentState = newState
        }
    }

    @Volatile
    private var currentState: Tunnel.State = Tunnel.State.DOWN

    fun connect(
        configText: String,
        callback: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            try {
                require(configText.isNotBlank()) {
                    "WireGuard configuration is empty."
                }

                val input = ByteArrayInputStream(
                    configText.toByteArray(StandardCharsets.UTF_8)
                )

                val config = input.use {
                    Config.parse(it)
                }

                backend.setState(
                    tunnel,
                    Tunnel.State.UP,
                    config
                )

                currentState = Tunnel.State.UP
                callback(Result.success(Unit))

            } catch (exception: Exception) {
                currentState = Tunnel.State.DOWN
                callback(Result.failure(exception))
            }
        }
    }

    fun disconnect(
        callback: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            try {
                backend.setState(
                    tunnel,
                    Tunnel.State.DOWN,
                    null
                )

                currentState = Tunnel.State.DOWN
                callback(Result.success(Unit))

            } catch (exception: Exception) {
                callback(Result.failure(exception))
            }
        }
    }

    fun isConnected(): Boolean {
        return currentState == Tunnel.State.UP
    }

    fun shutdown() {
        executor.shutdown()
    }
}