package com.example.autoiprotator.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class TunnelStatus {
    DISCONNECTED,
    AUTO_SELECTING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

enum class TunnelProtocol(val wireValue: String) {
    TOR("tor"),
    WIREGUARD("wireguard");

    companion object {
        fun fromRaw(raw: String?): TunnelProtocol =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: WIREGUARD
    }
}

enum class ServerSource(val wireValue: String) {
    OPEN_SOURCE("open"),
    WORKER("worker"),
    QR("qr"),
    MANUAL("manual");

    companion object {
        fun fromRaw(raw: String?): ServerSource =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

enum class AuditEventType(val wireValue: String) {
    CONNECTED("connected"),
    DISCONNECTED("disconnected"),
    IP_VERIFIED("ip_verified"),
    KEY_ROTATED("key_rotated");

    companion object {
        fun fromRaw(raw: String?): AuditEventType =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: CONNECTED
    }
}

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val workerBaseUrl: String = "",
    val autoSelectEnabled: Boolean = true,
    val preferredCountryCode: String = "AUTO",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VpnServerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val city: String = "",
    val endpoint: String = "",
    val serverPublicKey: String = "",
    val clientAddress: String = "10.0.0.2/32",
    val clientPrivateKey: String = "",
    val presharedKey: String? = null,
    val dns: String = "1.1.1.1, 1.0.0.1",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val mtu: Long = 1280L,
    val persistentKeepalive: Long = 25L,
    val latencyMs: Long = -1L,
    val loadPercent: Long = 0L,
    val source: String = ServerSource.MANUAL.wireValue,
    val favorite: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun isOpenSourceTor(): Boolean =
        source.equals(ServerSource.OPEN_SOURCE.wireValue, ignoreCase = true) ||
            id.startsWith("tor_open_")

    fun protocol(): TunnelProtocol =
        if (isOpenSourceTor()) TunnelProtocol.TOR else TunnelProtocol.WIREGUARD

    fun toWireGuardConfigString(): String {
        val sb = StringBuilder()
        sb.appendLine("[Interface]")
        sb.appendLine("PrivateKey = $clientPrivateKey")
        sb.appendLine("Address = $clientAddress")
        if (dns.isNotBlank()) {
            sb.appendLine("DNS = $dns")
        }
        sb.appendLine("MTU = $mtu")
        sb.appendLine()
        sb.appendLine("[Peer]")
        sb.appendLine("PublicKey = $serverPublicKey")
        if (!presharedKey.isNullOrBlank()) {
            sb.appendLine("PresharedKey = $presharedKey")
        }
        sb.appendLine("Endpoint = $endpoint")
        sb.appendLine("AllowedIPs = $allowedIps")
        if (persistentKeepalive > 0L) {
            sb.appendLine("PersistentKeepalive = $persistentKeepalive")
        }
        return sb.toString().trim()
    }

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "name" to name.take(100),
            "countryCode" to countryCode.uppercase(Locale.US).take(8),
            "countryName" to countryName.take(80),
            "city" to city.take(80),
            "endpoint" to endpoint.take(200),
            "serverPublicKey" to serverPublicKey.take(64),
            "clientAddress" to clientAddress.take(120),
            "clientPrivateKey" to clientPrivateKey.take(64),
            "presharedKey" to presharedKey?.takeIf { it.isNotBlank() }?.take(64),
            "dns" to dns.take(120),
            "allowedIps" to allowedIps.take(120),
            "mtu" to mtu.coerceIn(1200L, 1500L),
            "persistentKeepalive" to persistentKeepalive.coerceIn(0L, 120L),
            "latencyMs" to latencyMs.coerceIn(-1L, 60000L),
            "loadPercent" to loadPercent.coerceIn(0L, 100L),
            "source" to ServerSource.fromRaw(source).wireValue,
            "favorite" to favorite,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class ConnectionAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val serverName: String = "",
    val countryCode: String = "XX",
    val verifiedIp: String = "",
    val verifiedCountry: String = "",
    val eventType: String = AuditEventType.CONNECTED.wireValue,
    val createdAt: Timestamp? = null
)

data class IpVerificationInfo(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val city: String,
    val isp: String,
    val verifiedAtMillis: Long,
    val source: String
)

fun countryCodeToFlag(countryCode: String): String {
    val clean = countryCode.trim().uppercase(Locale.US)
    if (clean == "AUTO" || clean == "ALL") return "\u26A1"
    if (clean.length != 2 || !clean.all { it in 'A'..'Z' }) return "\uD83C\uDF10"
    val first = Character.codePointAt(clean, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(clean, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

fun countryNameFromCode(code: String): String {
    val clean = code.trim().uppercase(Locale.US)
    if (clean == "AUTO") return "Global Auto Fastest"
    if (clean.length != 2) return clean.ifBlank { "Global" }
    val locale = Locale("", clean)
    val display = locale.getDisplayCountry(Locale.ENGLISH)
    return if (display.isNullOrBlank()) clean else display
}
