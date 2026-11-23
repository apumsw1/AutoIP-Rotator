package com.example.autoiprotator.vpn

import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryNameFromCode
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.Locale

data class ParsedWireGuardConfig(
    val privateKey: String,
    val clientAddress: String,
    val dns: String,
    val mtu: Long,
    val serverPublicKey: String,
    val presharedKey: String?,
    val endpoint: String,
    val allowedIps: String,
    val persistentKeepalive: Long
)

object WireGuardConfigParser {

    fun parse(rawText: String): Result<ParsedWireGuardConfig> = runCatching {
        val trimmed = rawText.trim()
        require(trimmed.isNotBlank()) { "WireGuard configuration is empty." }

        // Validate natively using wireguard-android's Config.parse first
        ByteArrayInputStream(trimmed.toByteArray(Charsets.UTF_8)).use { stream ->
            Config.parse(stream)
        }

        var section = ""
        var privateKey = ""
        var address = ""
        var dns = "1.1.1.1, 1.0.0.1"
        var mtu = 1280L
        var publicKey = ""
        var presharedKey: String? = null
        var endpoint = ""
        var allowedIps = "0.0.0.0/0, ::/0"
        var keepalive = 25L

        for (rawLine in trimmed.lines()) {
            val line = rawLine.substringBefore("#").trim()
            if (line.isEmpty()) continue
            when {
                line.equals("[Interface]", ignoreCase = true) -> section = "interface"
                line.equals("[Peer]", ignoreCase = true) -> section = "peer"
                line.contains("=") -> {
                    val key = line.substringBefore("=").trim().lowercase(Locale.US)
                    val value = line.substringAfter("=").trim()
                    if (section == "interface") {
                        when (key) {
                            "privatekey" -> privateKey = value
                            "address" -> address = value
                            "dns" -> if (value.isNotBlank()) dns = value
                            "mtu" -> mtu = value.toLongOrNull()?.coerceIn(1200L, 1500L) ?: 1280L
                        }
                    } else if (section == "peer") {
                        when (key) {
                            "publickey" -> publicKey = value
                            "presharedkey" -> if (value.isNotBlank()) presharedKey = value
                            "endpoint" -> endpoint = value
                            "allowedips" -> if (value.isNotBlank()) allowedIps = value
                            "persistentkeepalive" -> keepalive = value.toLongOrNull()?.coerceIn(0L, 120L) ?: 25L
                        }
                    }
                }
            }
        }

        require(privateKey.length in 40..64) { "Invalid or missing WireGuard PrivateKey." }
        require(address.isNotBlank()) { "Interface Address is required." }
        require(publicKey.length in 40..64) { "Invalid or missing Peer PublicKey." }
        require(endpoint.contains(":")) { "Peer Endpoint must include host:port." }

        ParsedWireGuardConfig(
            privateKey = privateKey,
            clientAddress = address,
            dns = dns,
            mtu = mtu,
            serverPublicKey = publicKey,
            presharedKey = presharedKey,
            endpoint = endpoint,
            allowedIps = allowedIps,
            persistentKeepalive = keepalive
        )
    }

    fun toServerProfile(
        parsed: ParsedWireGuardConfig,
        userId: String,
        name: String,
        countryCode: String,
        city: String,
        source: ServerSource
    ): VpnServerProfile {
        val normalizedCode = countryCode.trim().uppercase(Locale.US).ifBlank { "US" }.take(2)
        val resolvedName = name.trim().ifBlank {
            "${countryNameFromCode(normalizedCode)} Node (${parsed.endpoint.substringBeforeLast(":")})"
        }
        return VpnServerProfile(
            userId = userId,
            name = resolvedName.take(100),
            countryCode = normalizedCode,
            countryName = countryNameFromCode(normalizedCode),
            city = city.trim().take(80),
            endpoint = parsed.endpoint.take(200),
            serverPublicKey = parsed.serverPublicKey.take(64),
            clientAddress = parsed.clientAddress.take(120),
            clientPrivateKey = parsed.privateKey.take(64),
            presharedKey = parsed.presharedKey?.take(64),
            dns = parsed.dns.take(120),
            allowedIps = parsed.allowedIps.take(120),
            mtu = parsed.mtu,
            persistentKeepalive = parsed.persistentKeepalive,
            latencyMs = -1L,
            loadPercent = 0L,
            source = source.wireValue,
            favorite = false
        )
    }
}
