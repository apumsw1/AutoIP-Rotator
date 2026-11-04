package com.example.autoiprotator

/**
 * Supplies guest session and authorized Proton VPN server configuration.
 * Implementations must use an officially supported Proton authentication flow.
 */
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