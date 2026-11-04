
package com.example.autoiprotator

/**
 * Safe fallback until Proton provides an officially supported
 * third-party Guest authentication and server configuration flow.
 *
 * This implementation never creates fake sessions or credentials.
 */
class ProtonGuestUnavailableConfigSource : ProtonGuestConfigSource {

    private fun unavailable(): IllegalStateException {
        return IllegalStateException(
            "Proton Guest access is not available through a verified public API."
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
