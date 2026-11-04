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

            result.onFailure { error ->
                callback(Result.failure(error))
            }

            result.onSuccess { connectionConfig ->

                ProtonGuestWireGuardConfigBuilder
                    .build(connectionConfig)
                    .onFailure { error ->
                        callback(Result.failure(error))
                    }
                    .onSuccess { wireGuardConfig ->
                        tunnel.connect(wireGuardConfig, callback)
                    }
            }
        }
    }

    override fun disconnect(
        callback: (Result<Unit>) -> Unit
    ) {
        tunnel.disconnect(callback)
    }

    override fun isConnected(): Boolean {
        return tunnel.isConnected()
    }
}