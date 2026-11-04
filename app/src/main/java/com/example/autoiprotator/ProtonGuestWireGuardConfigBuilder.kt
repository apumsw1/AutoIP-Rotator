package com.example.autoiprotator

object ProtonGuestWireGuardConfigBuilder {

    fun build(
        connection: ProtonGuestConnectionConfig
    ): Result<String> {

        val validated = connection.validate()

        if (validated.isFailure) {
            return Result.failure(
                validated.exceptionOrNull()
                    ?: IllegalArgumentException("Invalid connection configuration.")
            )
        }

        return try {
            val endpoint = if (connection.serverAddress.contains(":")) {
                "[${connection.serverAddress}]:${connection.serverPort}"
            } else {
                "${connection.serverAddress}:${connection.serverPort}"
            }

            val config = """
                [Interface]
                PrivateKey = ${connection.clientPrivateKey}
                Address = ${connection.clientAddress}

                [Peer]
                PublicKey = ${connection.serverPublicKey}
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