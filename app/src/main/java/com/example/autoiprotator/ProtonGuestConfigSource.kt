package com.example.autoiprotator

interface ProtonGuestConfigSource {
    fun createGuestSession(
        callback: (Result<ProtonGuestSession>) -> Unit
    )

    fun discoverFreeServers(
        session: ProtonGuestSession,
        callback: (Result<List<ProtonFreeServer>>) -> Unit
    )

    fun getWireGuardConfig(
        session: ProtonGuestSession,
        server: ProtonFreeServer,
        callback: (Result<ProtonGuestConnectionConfig>) -> Unit
    )
}