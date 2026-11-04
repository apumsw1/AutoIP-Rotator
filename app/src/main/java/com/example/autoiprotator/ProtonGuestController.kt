
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

        backend.createGuestSession { sessionResult ->
            sessionResult.fold(
                onSuccess = { session ->
                    currentSession = session
                    discoverServer(session)
                },
                onFailure = { error ->
                    fail(error)
                }
            )
        }
    }

    private fun discoverServer(session: ProtonGuestSession) {
        onStateChanged(ProtonGuestState.DiscoveringServers)

        backend.discoverFreeServers(session) { serverResult ->
            serverResult.fold(
                onSuccess = { servers ->
                    val server = servers.firstOrNull()

                    if (server == null) {
                        fail(
                            IllegalStateException(
                                "No Proton Free servers were returned."
                            )
                        )
                    } else {
                        currentServer = server
                        connectToServer(session, server)
                    }
                },
                onFailure = { error ->
                    fail(error)
                }
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
                                "Backend did not confirm an active VPN tunnel."
                            )
                        )
                    }
                },
                onFailure = { error ->
                    fail(error)
                }
            )
        }
    }

    fun disconnect() {
        if (operationInProgress) return

        operationInProgress = true

        backend.disconnect { result ->
            operationInProgress = false

            result.fold(
                onSuccess = {
                    currentSession = null
                    currentServer = null
                    onStateChanged(ProtonGuestState.Disconnected)
                },
                onFailure = { error ->
                    fail(error)
                }
            )
        }
    }

    private fun fail(error: Throwable) {
        operationInProgress = false

        onStateChanged(
            ProtonGuestState.Error(
                error.message ?: "Guest VPN operation failed."
            )
        )
    }
}
