
package com.example.autoiprotator

class ProtonGuestUnavailableConfigSource : ProtonGuestConfigSource {

    private fun unavailable() =
        IllegalStateException(
            "Proton Guest session service is not configured."
        )

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
