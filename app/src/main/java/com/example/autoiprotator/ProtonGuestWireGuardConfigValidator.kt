package com.example.autoiprotator

object ProtonGuestWireGuardConfigValidator {

    fun validate(config: ProtonGuestConnectionConfig): Result<ProtonGuestConnectionConfig> {
        val basicValidation = config.validate()
        if (basicValidation.isFailure) {
            return basicValidation
        }

        val keyPattern = Regex("^[A-Za-z0-9+/]{43}=$")

        if (!keyPattern.matches(config.clientPrivateKey.trim())) {
            return Result.failure(
                IllegalArgumentException("Invalid WireGuard client private key")
            )
        }

        if (!keyPattern.matches(config.serverPublicKey.trim())) {
            return Result.failure(
                IllegalArgumentException("Invalid WireGuard server public key")
            )
        }

        if (config.serverAddress.isBlank() ||
            config.serverAddress.any { it.isWhitespace() }
        ) {
            return Result.failure(
                IllegalArgumentException("Invalid WireGuard server address")
            )
        }

        if (config.clientAddress.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Invalid WireGuard client address")
            )
        }

        return Result.success(config)
    }
}