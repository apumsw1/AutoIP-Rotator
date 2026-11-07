package com.example.autoiprotator

object ProtonGuestConnectionValidator {

    fun validate(
        session: ProtonGuestSession?,
        server: ProtonFreeServer?,
        config: ProtonGuestConnectionConfig?
    ): Result<ProtonGuestConnectionConfig> {

        if (session == null) {
            return Result.failure(
                IllegalStateException("Guest session is missing.")
            )
        }

        if (server == null) {
            return Result.failure(
                IllegalStateException("VPN server is not selected.")
            )
        }

        if (config == null) {
            return Result.failure(
                IllegalStateException("VPN configuration is missing.")
            )
        }

        if (server.id != config.serverId) {
            return Result.failure(
                IllegalArgumentException(
                    "Configuration does not match the selected server."
                )
            )
        }

        return config.validate()
    }
}