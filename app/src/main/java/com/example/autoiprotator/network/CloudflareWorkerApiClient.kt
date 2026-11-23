package com.example.autoiprotator.network

import com.example.autoiprotator.VpnEngine
import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryNameFromCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WorkerHealthStatus(
    val ok: Boolean,
    val service: String,
    val serverCountHint: Int? = null
)

data class WorkerPeerRegistration(
    val clientAddress: String,
    val dns: String,
    val mtu: Long,
    val allowedIps: String
)

object CloudflareWorkerApiClient {

    private const val TIMEOUT_MS = 15000

    fun normalizeAndValidateBaseUrl(rawUrl: String): Result<String> = runCatching {
        val trimmed = rawUrl.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "Cloudflare Worker URL is required." }
        val parsed = URL(trimmed)
        require(parsed.protocol.equals("https", ignoreCase = true)) {
            "Worker API URL must use HTTPS."
        }
        require(!parsed.host.isNullOrBlank()) { "Invalid Worker host." }
        trimmed
    }

    suspend fun checkHealth(baseUrl: String): Result<WorkerHealthStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = normalizeAndValidateBaseUrl(baseUrl).getOrThrow()
            val body = request(
                url = "$cleanBase/api/health",
                method = "GET",
                bearerToken = null
            )
            val json = JSONObject(body)
            WorkerHealthStatus(
                ok = json.optBoolean("ok", true),
                service = json.optString("service", "cloudflare-worker")
            )
        }
    }

    suspend fun fetchServers(
        baseUrl: String,
        bearerToken: String,
        userId: String
    ): Result<List<VpnServerProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = normalizeAndValidateBaseUrl(baseUrl).getOrThrow()
            val body = request(
                url = "$cleanBase/api/servers",
                method = "GET",
                bearerToken = bearerToken
            )
            val root = JSONObject(body)
            val arr: JSONArray = root.optJSONArray("servers") ?: JSONArray()
            val result = mutableListOf<VpnServerProfile>()

            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val endpoint = item.optString("endpoint").trim()
                val publicKey = item.optString("publicKey").ifBlank { item.optString("serverPublicKey") }.trim()
                if (endpoint.isBlank() || publicKey.isBlank()) continue

                val countryCode = item.optString("countryCode", "US").trim().uppercase(Locale.US).take(2)
                val countryName = item.optString("countryName").ifBlank { countryNameFromCode(countryCode) }
                val serverId = item.optString("id").ifBlank {
                    "${countryCode.lowercase(Locale.US)}_${i + 1}"
                }.replace(Regex("[^a-zA-Z0-9_-]"), "_")

                // Generate a fresh Curve25519 client keypair on-device; never request private keys over HTTP unless explicitly provisioned
                var clientPrivateKey = item.optString("clientPrivateKey").trim()
                var clientAddress = item.optString("clientAddress").trim()
                var dns = item.optString("dns", "1.1.1.1, 1.0.0.1").trim()
                var mtu = item.optLong("mtu", 1280L).coerceIn(1200L, 1500L)
                var allowedIps = item.optString("allowedIps", "0.0.0.0/0, ::/0").trim()

                if (clientPrivateKey.isBlank()) {
                    val generated = VpnEngine.generateClientKeyPair()
                    clientPrivateKey = generated.privateKey
                    val registered = registerPeerInternal(
                        cleanBase = cleanBase,
                        bearerToken = bearerToken,
                        serverId = serverId,
                        clientPublicKey = generated.publicKey
                    )
                    if (registered != null) {
                        clientAddress = registered.clientAddress
                        dns = registered.dns
                        mtu = registered.mtu
                        allowedIps = registered.allowedIps
                    }
                }

                if (clientAddress.isBlank()) {
                    clientAddress = "10.8.0.2/32"
                }

                result.add(
                    VpnServerProfile(
                        id = serverId,
                        userId = userId,
                        name = item.optString("name").ifBlank { "$countryName Node" }.take(100),
                        countryCode = countryCode,
                        countryName = countryName.take(80),
                        city = item.optString("city", "").take(80),
                        endpoint = endpoint.take(200),
                        serverPublicKey = publicKey.take(64),
                        clientAddress = clientAddress.take(120),
                        clientPrivateKey = clientPrivateKey.take(64),
                        presharedKey = item.optString("presharedKey").takeIf { it.isNotBlank() }?.take(64),
                        dns = dns.take(120),
                        allowedIps = allowedIps.take(120),
                        mtu = mtu,
                        persistentKeepalive = item.optLong("persistentKeepalive", 25L).coerceIn(0L, 120L),
                        latencyMs = item.optLong("latencyMs", -1L).coerceIn(-1L, 60000L),
                        loadPercent = item.optLong("loadPercent", 0L).coerceIn(0L, 100L),
                        source = ServerSource.WORKER.wireValue,
                        favorite = false
                    )
                )
            }
            result
        }
    }

    suspend fun rotatePeerKey(
        baseUrl: String,
        bearerToken: String,
        serverId: String,
        oldPublicKey: String?,
        newPublicKey: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = normalizeAndValidateBaseUrl(baseUrl).getOrThrow()
            val payload = JSONObject().apply {
                put("serverId", serverId)
                if (!oldPublicKey.isNullOrBlank()) put("oldPublicKey", oldPublicKey)
                put("newPublicKey", newPublicKey)
            }
            val response = request(
                url = "$cleanBase/api/peers/rotate",
                method = "POST",
                bearerToken = bearerToken,
                body = payload
            )
            val json = JSONObject(response)
            json.optString("clientAddress").takeIf { it.isNotBlank() }
        }
    }

    private fun registerPeerInternal(
        cleanBase: String,
        bearerToken: String,
        serverId: String,
        clientPublicKey: String
    ): WorkerPeerRegistration? {
        return try {
            val payload = JSONObject().apply {
                put("serverId", serverId)
                put("clientPublicKey", clientPublicKey)
            }
            val response = request(
                url = "$cleanBase/api/peers/register",
                method = "POST",
                bearerToken = bearerToken,
                body = payload
            )
            val json = JSONObject(response)
            WorkerPeerRegistration(
                clientAddress = json.optString("clientAddress", "10.8.0.2/32"),
                dns = json.optString("dns", "1.1.1.1, 1.0.0.1"),
                mtu = json.optLong("mtu", 1280L).coerceIn(1200L, 1500L),
                allowedIps = json.optString("allowedIps", "0.0.0.0/0, ::/0")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun request(
        url: String,
        method: String,
        bearerToken: String?,
        body: JSONObject? = null
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("Worker API HTTP $code: ${text.take(200)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}
