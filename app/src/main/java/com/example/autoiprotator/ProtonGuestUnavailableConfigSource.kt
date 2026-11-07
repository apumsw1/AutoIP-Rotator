package com.example.autoiprotator

/**
 * Safe fallback when no verified Proton Guest API is configured.
 * Never generates fake sessions, credentials, servers, or VPN configs.
 */
class ProtonGuestUnavailableConfigSource : ProtonGuestConfigSource {

    private fun unavailable(): IllegalStateException {
        return IllegalStateException(
            "Proton Guest service is not configured. No verified public API is available."
        )
    }

    override fun createGuestSession(
        callback: (Result<ProtonGuestSession>) -> Unit
    ) {
        callback(Result.failure(unavailable()))
    }

    override fun discoverFreeServers(
        session: ProtonGuestSession,
        callback: (Result<List<ProtonFreeServer>>) -> Unit
    ) {
        callback(Result.failure(unavailable()))
    }

    override fun getWireGuardConfig(
        session: ProtonGuestSession,
        server: ProtonFreeServer,
        callback: (Result<ProtonGuestConnectionConfig>) -> Unit
    ) {
        callback(Result.failure(unavailable()))
    }
}