package com.example.autoiprotator

class ProtonGuestController(
    private val backend: ProtonGuestBackend,
    private val onStateChanged: (ProtonGuestState) -> Unit
) {
    @Volatile
    private var currentSession: ProtonGuestSession? = null

    @Volatile
    private var currentServer: ProtonFreeServer? = null

    @Volatile
    private var operationInProgress = false

    @Synchronized
    fun connect() {
        if (operationInProgress || backend.isConnected()) return

        operationInProgress = true
        onStateChanged(ProtonGuestState.CreatingGuestSession)

        backend.createGuestSession { result ->
            result.fold(
                onSuccess = { session ->
                    currentSession = session
                    discoverServers(session)
                },
                onFailure = ::fail
            )
        }
    }

    private fun discoverServers(session: ProtonGuestSession) {
        onStateChanged(ProtonGuestState.DiscoveringServers)

        backend.discoverFreeServers(session) { result ->
            result.fold(
                onSuccess = { servers ->
                    if (servers.isEmpty()) {
                        fail(IllegalStateException("No free servers available."))
                    } else {
                        val server = servers.first()
                        currentServer = server
                        connectToServer(session, server)
                    }
                },
                onFailure = ::fail
            )
        }
    }

    private fun connectToServer(
        session: ProtonGuestSession,
        server: ProtonFreeServer
    ) {
        onStateChanged(ProtonGuestState.Connecting)

        backend.connect(session, server) { result ->
            result.fold(
                onSuccess = {
                    if (backend.isConnected()) {
                        operationInProgress = false
                        onStateChanged(
                            ProtonGuestState.Connected(
                                serverName = server.name,
                                connectedAtMillis = System.currentTimeMillis()
                            )
                        )
                    } else {
                        fail(
                            IllegalStateException(
                                "VPN tunnel did not confirm connection."
                            )
                        )
                    }
                },
                onFailure = ::fail
            )
        }
    }

    fun disconnect() {
        synchronized(this) {
            if (operationInProgress) return
            operationInProgress = true
        }

        backend.disconnect { result ->
            operationInProgress = false

            result.fold(
                onSuccess = {
                    currentSession = null
                    currentServer = null
                    onStateChanged(ProtonGuestState.Disconnected)
                },
                onFailure = ::fail
            )
        }
    }

    @Synchronized
    private fun fail(error: Throwable) {
        operationInProgress = false
        onStateChanged(
            ProtonGuestState.Error(
                error.message ?: "Guest VPN operation failed."
            )
        )
    }
}