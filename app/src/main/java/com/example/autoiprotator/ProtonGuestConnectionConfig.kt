package com.example.autoiprotator

data class ProtonGuestConnectionConfig(
    val serverId: String,
    val serverAddress: String,
    val serverPort: Int,
    val clientPrivateKey: String,
    val clientAddress: String,
    val serverPublicKey: String
) {
    fun validate(): Result<ProtonGuestConnectionConfig> {
        if (serverId.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server ID"))
        }

        if (serverAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server address"))
        }

        if (serverPort !in 1..65535) {
            return Result.failure(IllegalArgumentException("Invalid server port"))
        }

        if (clientPrivateKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing client private key"))
        }

        if (clientAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing client address"))
        }

        if (serverPublicKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing server public key"))
        }

        return Result.success(this)
    }
}