
package com.example.autoiprotator

import android.content.Context

class ProtonGuestWireGuardBackend(
    context: Context,
    private val configSource: ProtonGuestConfigSource
) : ProtonGuestBackend {

    private val tunnel = ProtonGuestWireGuardTunnel(context)

    @Volatile
    private var connected = false

    override fun createGuestSession(
        callback: (Result<ProtonGuestSession>) -> Unit
    ) {
        configSource.createGuestSession(callback)
    }

    override fun discoverFreeServers(
        session: ProtonGuestSession,
        callback: (Result<List<ProtonFreeServer>>) -> Unit
    ) {
        configSource.discoverFreeServers(session, callback)
    }

    override fun connect(
        session: ProtonGuestSession,
        server: ProtonFreeServer,
        callback: (Result<Unit>) -> Unit
    ) {
        configSource.getWireGuardConfig(session, server) { result ->
            result.fold(
                onSuccess = { connectionConfig ->
                    val validation = connectionConfig.validate()

                    if (validation.isFailure) {
                        callback(Result.failure(
                            validation.exceptionOrNull()
                                ?: IllegalStateException("Invalid VPN config")
                        ))
                        return@getWireGuardConfig
                    }

                    val configText = """
                        [Interface]
                        PrivateKey = ${connectionConfig.clientPrivateKey}
                        Address = ${connectionConfig.clientAddress}

                        [Peer]
                        PublicKey = ${connectionConfig.serverPublicKey}
                        Endpoint = ${connectionConfig.serverAddress}:${connectionConfig.serverPort}
                        AllowedIPs = 0.0.0.0/0, ::/0
                        PersistentKeepalive = 25
                    """.trimIndent()

                    tunnel.connect(configText) { tunnelResult ->
                        connected = tunnelResult.isSuccess
                        callback(tunnelResult)
                    }
                },
                onFailure = {
                    callback(Result.failure(it))
                }
            )
        }
    }

    override fun disconnect(callback: (Result<Unit>) -> Unit) {
        tunnel.disconnect { result ->
            connected = false
            callback(result)
        }
    }

    override fun isConnected(): Boolean = connected

    fun shutdown() {
        tunnel.shutdown()
    }
}
