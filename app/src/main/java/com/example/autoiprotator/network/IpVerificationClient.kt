package com.example.autoiprotator.network

import com.example.autoiprotator.model.IpVerificationInfo
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryNameFromCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

object IpVerificationClient {

    private const val TIMEOUT_MS = 10000

    suspend fun verifyPublicIp(): Result<IpVerificationInfo> = withContext(Dispatchers.IO) {
        // Primary: ipwho.is (returns IP, country, city, ISP)
        val primary = runCatching {
            val raw = fetchText("https://ipwho.is/")
            val json = JSONObject(raw)
            check(json.optBoolean("success", true)) {
                json.optString("message", "IP lookup failed")
            }
            val ip = json.optString("ip").trim()
            check(ip.isNotBlank()) { "IP missing in response" }
            val code = json.optString("country_code", "XX").uppercase(Locale.US)
            val country = json.optString("country").ifBlank { countryNameFromCode(code) }
            val city = json.optString("city", "")
            val isp = json.optJSONObject("connection")?.optString("isp", "") ?: ""
            IpVerificationInfo(
                ip = ip,
                countryCode = code,
                countryName = country,
                city = city,
                isp = isp,
                verifiedAtMillis = System.currentTimeMillis(),
                source = "ipwho.is"
            )
        }
        if (primary.isSuccess) return@withContext primary

        // Fallback: Cloudflare trace endpoint (works globally over HTTPS)
        runCatching {
            val raw = fetchText("https://www.cloudflare.com/cdn-cgi/trace")
            val map = raw.lines().mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }.toMap()
            val ip = map["ip"]?.trim().orEmpty()
            check(ip.isNotBlank()) { "Public IP unavailable" }
            val code = (map["loc"] ?: "XX").trim().uppercase(Locale.US)
            IpVerificationInfo(
                ip = ip,
                countryCode = code,
                countryName = countryNameFromCode(code),
                city = map["colo"]?.let { "Edge $it" }.orEmpty(),
                isp = "Verified via TLS Trace",
                verifiedAtMillis = System.currentTimeMillis(),
                source = "cloudflare-trace"
            )
        }
    }

    private fun fetchText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.useCaches = false
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}

object ServerLatencyProber {

    suspend fun measureServers(servers: List<VpnServerProfile>): Map<String, Long> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                servers.map { server ->
                    async {
                        server.id to probeSingle(server.endpoint)
                    }
                }.awaitAll().toMap()
            }
        }

    fun selectBestServer(
        servers: List<VpnServerProfile>,
        preferredCountryCode: String
    ): VpnServerProfile? {
        if (servers.isEmpty()) return null
        val normalizedPref = preferredCountryCode.trim().uppercase(Locale.US)
        val candidates = if (normalizedPref == "AUTO" || normalizedPref.isBlank()) {
            servers
        } else {
            servers.filter { it.countryCode.equals(normalizedPref, ignoreCase = true) }
                .ifEmpty { servers }
        }
        return candidates.minWithOrNull(
            compareBy<VpnServerProfile>(
                { if (it.latencyMs < 0L) Long.MAX_VALUE / 2 else it.latencyMs + (it.loadPercent * 3L) },
                { if (it.favorite) 0 else 1 },
                { it.loadPercent }
            )
        )
    }

    private fun probeSingle(endpoint: String): Long {
        val host = endpoint.substringBeforeLast(":").trim('[', ']', ' ')
        if (host.isBlank()) return -1L

        val startNs = System.nanoTime()
        return try {
            // Measure DNS + TCP probe on 443/80 if server exposes web/management port, else DNS+Socket timing
            val addr = InetAddress.getByName(host)
            val elapsedDnsMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val probePort = 443
            val tcpStartNs = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(addr, probePort), 1200)
                }
                ((System.nanoTime() - tcpStartNs) / 1_000_000L).coerceAtLeast(1L)
            } catch (_: Exception) {
                // WireGuard nodes may block TCP 443; fall back to reachability check timing
                val reachableStart = System.nanoTime()
                val reachable = try {
                    addr.isReachable(1000)
                } catch (_: Exception) {
                    false
                }
                if (reachable) {
                    ((System.nanoTime() - reachableStart) / 1_000_000L).coerceAtLeast(1L)
                } else {
                    elapsedDnsMs.coerceAtLeast(15L)
                }
            }
        } catch (_: Exception) {
            -1L
        }
    }
}
