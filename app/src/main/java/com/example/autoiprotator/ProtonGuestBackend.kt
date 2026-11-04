
package com.example.autoiprotator

/**
 * Contract for the authorized Proton Guest VPN implementation.
 * A real Proton implementation must provide the session,
 * server discovery, and tunnel operations.
 */
interface ProtonGuestBackend {

    fun createGuestSession(
        callback: (Result<ProtonGuestSession>) -> Unit
    )

    fun discoverFreeServers(
        session: ProtonGuestSession,
        callback: (Result<List<ProtonFreeServer>>) -> Unit
    )

    fun connect(
        session: ProtonGuestSession,
        server: ProtonFreeServer,
        callback: (Result<Unit>) -> Unit
    )

    fun disconnect(
        callback: (Result<Unit>) -> Unit
    )

    fun isConnected(): Boolean
}

/**
 * Opaque handle for a real authorized guest session.
 * The actual implementation must manage Proton credentials securely.
 */
interface ProtonGuestSession

/**
 * Server information returned by real Proton server discovery.
 */
data class ProtonFreeServer(
    val id: String,
    val name: String,
    val countryCode: String
)
