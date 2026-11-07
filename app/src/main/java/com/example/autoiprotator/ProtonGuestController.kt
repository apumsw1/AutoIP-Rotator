package com.example.autoiprotator

class ProtonGuestController(
    private val backend: ProtonGuestBackend,
    private val onStateChanged: (ProtonGuestState) -> Unit,
    private val serverSelector: ProtonGuestServerSelector =
        ProtonGuestServerSelector()
) {
    @Volatile
    private var currentSession: ProtonGuestSession? = null

    @Volatile
    private var currentServer: ProtonFreeServer? = null

    @Volatile
    private var operationInProgress = false

    @Synchronized
    fun connect(rotate: Boolean = false) {
        if (operationInProgress || backend.isConnected()) return

        operationInProgress = true
        onStateChanged(ProtonGuestState.CreatingGuestSession)

        backend.createGuestSession { result ->
            result.fold(
                onSuccess = { session ->
                    currentSession = session
                    discoverServers(session, rotate)
                },
                onFailure = ::fail
            )
        }
    }

    private fun discoverServers(
        session: ProtonGuestSession,
        rotate: Boolean
    ) {
        onStateChanged(ProtonGuestState.DiscoveringServers)

        backend.discoverFreeServers(session) { result ->
            result.fold(
                onSuccess = { servers ->
                    serverSelector.selectServer(servers, rotate).fold(
                        onSuccess = { server ->
                            currentServer = server
                            connectToServer(session, server)
                        },
                        onFailure = ::fail
                    )
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