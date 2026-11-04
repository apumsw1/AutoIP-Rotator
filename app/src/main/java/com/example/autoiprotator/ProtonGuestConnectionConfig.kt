
package com.example.autoiprotator

/**
 * Validated connection data returned by an authorized Proton VPN API.
 * Never fill these fields with invented or placeholder credentials.
 */
data class ProtonGuestConnectionConfig(
    val serverId: String,
    val serverName: String,
    val countryCode: String,
    val serverAddress: String,
    val serverPort: Int,
    val serverPublicKey: String,
    val clientPrivateKey: String,
    val clientAddress: String
) {
    fun validate(): Result<ProtonGuestConnectionConfig> {
        if (serverId.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server ID."))
        }

        if (serverName.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server name."))
        }

        if (!countryCode.matches(Regex("^[A-Z]{2}$"))) {
            return Result.failure(IllegalArgumentException("Invalid country code."))
        }

        if (serverAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server address."))
        }

        if (serverPort !in 1..65535) {
            return Result.failure(IllegalArgumentException("Invalid server port."))
        }

        if (serverPublicKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server public key."))
        }

        if (clientPrivateKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing client private key."))
        }

        if (clientAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing client address."))
        }

        return Result.success(this)
    }
}
