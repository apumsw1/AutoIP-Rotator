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

        if (server.id.isBlank() || config.serverId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Missing server identifier.")
            )
        }

        if (server.id != config.serverId) {
            return Result.failure(
                IllegalArgumentException(
                    "Configuration does not match the selected server."
                )
            )
        }

        if (server.host.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Selected server address is missing.")
            )
        }

        if (config.serverAddress.isBlank()) {
            return Result.failure(
                IllegalArgumentException("VPN endpoint address is missing.")
            )
        }

        return ProtonGuestWireGuardConfigValidator.validate(config)
    }
}