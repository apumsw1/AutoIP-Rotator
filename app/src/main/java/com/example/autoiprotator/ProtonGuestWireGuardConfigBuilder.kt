package com.example.autoiprotator

object ProtonGuestWireGuardConfigBuilder {

    fun build(
        connection: ProtonGuestConnectionConfig
    ): Result<String> {

        val validation = ProtonGuestWireGuardConfigValidator.validate(connection)

        if (validation.isFailure) {
            return Result.failure(
                validation.exceptionOrNull()
                    ?: IllegalArgumentException("Invalid WireGuard configuration.")
            )
        }

        return try {
            val address = connection.serverAddress.trim()
            val endpoint = if (address.contains(":") && !address.startsWith("[")) {
                "[$address]:${connection.serverPort}"
            } else {
                "$address:${connection.serverPort}"
            }

            val config = """
                [Interface]
                PrivateKey = ${connection.clientPrivateKey.trim()}
                Address = ${connection.clientAddress.trim()}

                [Peer]
                PublicKey = ${connection.serverPublicKey.trim()}
                Endpoint = $endpoint
                AllowedIPs = 0.0.0.0/0, ::/0
                PersistentKeepalive = 25
            """.trimIndent()

            Result.success(config)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}