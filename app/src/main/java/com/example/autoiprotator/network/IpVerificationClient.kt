package com.example.autoiprotator.network

import com.example.autoiprotator.model.IpVerificationInfo
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryNameFromCode
import com.example.autoiprotator.vpn.OpenSourceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.Locale

object IpVerificationClient {

    private const val TIMEOUT_MS = 12000

    suspend fun verifyPublicIp(socksPort: Int? = null): Result<IpVerificationInfo> =
        withContext(Dispatchers.IO) {
            val proxy = socksPort?.takeIf { it > 0 }?.let { port ->
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
            }

            // Primary: ipwho.is (returns IP, country, city, ISP)
            val primary = runCatching {
                val raw = fetchText("https://ipwho.is/", proxy)
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
                    source = if (proxy != null) "ipwho.is (Tor Exit)" else "ipwho.is"
                )
            }
            if (primary.isSuccess) return@withContext primary

            // Fallback: Cloudflare trace endpoint (works globally over HTTPS)
            runCatching {
                val raw = fetchText("https://www.cloudflare.com/cdn-cgi/trace", proxy)
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
                    isp = if (proxy != null) "Verified via Tor Circuit" else "Verified via TLS Trace",
                    verifiedAtMillis = System.currentTimeMillis(),
                    source = if (proxy != null) "cloudflare-trace (Tor Exit)" else "cloudflare-trace"
                )
            }
        }

    private fun fetchText(url: String, proxy: Proxy?): String {
        val conn = (if (proxy != null) {
            URL(url).openConnection(proxy)
        } else {
            URL(url).openConnection()
        }) as HttpURLConnection
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
            val hasTorNodes = servers.any { it.isOpenSourceTor() }
            val torBaseRtt = if (hasTorNodes) probeTorEntryLatency() else -1L
            coroutineScope {
                servers.map { server ->
                    async {
                        if (server.isOpenSourceTor()) {
                            server.id to torBaseRtt
                        } else {
                            server.id to probeSingle(server.endpoint)
                        }
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
                // Reachable custom WireGuard VPS nodes take precedence over multi-hop Tor when available
                { if (!it.isOpenSourceTor() && it.latencyMs >= 0L) 0 else 1 },
                { if (it.latencyMs < 0L) Long.MAX_VALUE / 2 else it.latencyMs + (it.loadPercent * 2L) },
                { if (it.countryCode.equals("AUTO", ignoreCase = true)) 0 else 1 },
                { if (it.favorite) 0 else 1 },
                { it.loadPercent }
            )
        )
    }

    private fun probeTorEntryLatency(): Long {
        var bestRtt = Long.MAX_VALUE
        for ((host, port) in OpenSourceCatalog.torDirectoryAnchors) {
            val startNs = System.nanoTime()
            val ok = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1200)
                }
                true
            } catch (_: Exception) {
                false
            }
            if (ok) {
                val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                if (rtt < bestRtt) bestRtt = rtt
            }
        }
        return if (bestRtt == Long.MAX_VALUE) -1L else bestRtt
    }

    private fun probeSingle(endpoint: String): Long {
        val host = endpoint.substringBeforeLast(":").trim('[', ']', ' ')
        if (host.isBlank()) return -1L

        return try {
            val addr = InetAddress.getByName(host)
            val probePorts = intArrayOf(443, 80, 53)
            for (port in probePorts) {
                val tcpStartNs = System.nanoTime()
                val connected = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(addr, port), 1000)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
                if (connected) {
                    return ((System.nanoTime() - tcpStartNs) / 1_000_000L).coerceAtLeast(1L)
                }
            }

            // WireGuard-only hosts may drop TCP; measure ICMP/TCP-echo reachability
            val reachableStart = System.nanoTime()
            val reachable = try {
                addr.isReachable(1000)
            } catch (_: Exception) {
                false
            }
            if (reachable) {
                ((System.nanoTime() - reachableStart) / 1_000_000L).coerceAtLeast(1L)
            } else {
                -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }
}
