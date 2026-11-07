package com.example.autoiprotator

class ProtonGuestBackendAdapter(
    private val configSource: ProtonGuestConfigSource,
    private val tunnel: ProtonGuestWireGuardTunnel
) : ProtonGuestBackend {

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
                    if (connectionConfig.serverId != server.id) {
                        callback(
                            Result.failure(
                                IllegalArgumentException(
                                    "VPN configuration does not match selected server."
                                )
                            )
                        )
                        return@getWireGuardConfig
                    }

                    ProtonGuestWireGuardConfigBuilder
                        .build(connectionConfig)
                        .fold(
                            onSuccess = { wireGuardConfig ->
                                tunnel.connect(wireGuardConfig, callback)
                            },
                            onFailure = { error ->
                                callback(Result.failure(error))
                            }
                        )
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    override fun disconnect(callback: (Result<Unit>) -> Unit) {
        tunnel.disconnect(callback)
    }

    override fun isConnected(): Boolean = tunnel.isConnected()
}