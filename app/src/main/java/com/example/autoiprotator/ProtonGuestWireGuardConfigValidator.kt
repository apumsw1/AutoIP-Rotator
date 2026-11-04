package com.example.autoiprotator

import android.util.Base64

object ProtonGuestWireGuardConfigValidator {

    fun validate(
        config: ProtonGuestConnectionConfig
    ): Result<Unit> {
        val validation = config.validate()

        if (validation.isFailure) {
            return Result.failure(
                validation.exceptionOrNull()
                    ?: IllegalArgumentException("Invalid VPN configuration.")
            )
        }

        return try {
            validateKey(config.serverPublicKey, "Server public key")
            validateKey(config.clientPrivateKey, "Client private key")

            require(config.serverAddress.isNotBlank()) {
                "Server address is missing."
            }

            require(config.serverAddress.none { it.isWhitespace() }) {
                "Server address contains invalid whitespace."
            }

            require(config.clientAddress.contains("/")) {
                "Client address must include a CIDR prefix."
            }

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun validateKey(
        value: String,
        label: String
    ) {
        val decoded = try {
            Base64.decode(value.trim(), Base64.DEFAULT)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("$label is not valid Base64.")
        }

        require(decoded.size == 32) {
            "$label must decode to exactly 32 bytes."
        }
    }
}