package com.example.autoiprotator

class ProtonGuestUnavailableConfigSource : ProtonGuestConfigSource {

    override fun createGuestSession(
        callback: (Result<ProtonGuestSession>) -> Unit
    ) {
        callback(
            Result.failure(
                UnsupportedOperationException(
                    "Proton Guest authentication is not integrated yet."
                )
            )
        )
    }

    override fun discoverFreeServers(
        session: ProtonGuestSession,
        callback: (Result<List<ProtonFreeServer>>) -> Unit
    ) {
        callback(
            Result.failure(
                UnsupportedOperationException(
                    "Proton Guest server discovery is not integrated yet."
                )
            )
        )
    }

    override fun getWireGuardConfig(
        session: ProtonGuestSession,
        server: ProtonFreeServer,
        callback: (Result<ProtonGuestConnectionConfig>) -> Unit
    ) {
        callback(
            Result.failure(
                UnsupportedOperationException(
                    "An authenticated Proton WireGuard configuration is required."
                )
            )
        )
    }
}