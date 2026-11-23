package com.example.autoiprotator.vpn

import android.content.Context
import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryNameFromCode
import org.json.JSONObject
import java.util.Locale

data class OpenTorCountrySpec(
    val id: String,
    val countryCode: String,
    val name: String,
    val city: String,
    val loadPercent: Long
)

object OpenSourceCatalog {

    val defaultSpecs: List<OpenTorCountrySpec> = listOf(
        OpenTorCountrySpec("tor_open_auto", "AUTO", "Auto Fastest (Open Tor)", "Lowest-Ping Multi-Hop", 18L),
        OpenTorCountrySpec("tor_open_sg", "SG", "Singapore Open Node", "Singapore", 24L),
        OpenTorCountrySpec("tor_open_us", "US", "United States Open Node", "New York / Ashburn", 31L),
        OpenTorCountrySpec("tor_open_de", "DE", "Germany Open Node", "Frankfurt / Berlin", 27L),
        OpenTorCountrySpec("tor_open_nl", "NL", "Netherlands Open Node", "Amsterdam", 29L),
        OpenTorCountrySpec("tor_open_gb", "GB", "United Kingdom Open Node", "London", 34L),
        OpenTorCountrySpec("tor_open_jp", "JP", "Japan Open Node", "Tokyo", 38L),
        OpenTorCountrySpec("tor_open_ca", "CA", "Canada Open Node", "Toronto", 33L),
        OpenTorCountrySpec("tor_open_fr", "FR", "France Open Node", "Paris", 36L),
        OpenTorCountrySpec("tor_open_ch", "CH", "Switzerland Open Node", "Zurich", 28L),
        OpenTorCountrySpec("tor_open_se", "SE", "Sweden Open Node", "Stockholm", 25L)
    )

    // Official long-lived Tor Project Directory Authorities used solely to probe live Tor network entry RTT
    val torDirectoryAnchors: List<Pair<String, Int>> = listOf(
        "131.188.40.189" to 443,   // gabelmoo (DE)
        "199.58.81.140" to 443,    // longclaw (US)
        "193.23.244.244" to 443,   // dannenberg (DE)
        "171.25.193.9" to 443,     // maatuska (SE)
        "204.13.164.118" to 443    // bastet (US)
    )

    fun buildProfiles(
        userId: String,
        store: OpenSourceNodeStore
    ): List<VpnServerProfile> {
        val latencies = store.getLatencies()
        val favorites = store.getFavorites()
        return defaultSpecs.map { spec ->
            val cc = spec.countryCode.uppercase(Locale.US)
            VpnServerProfile(
                id = spec.id,
                userId = userId,
                name = spec.name,
                countryCode = cc,
                countryName = if (cc == "AUTO") "Global Auto Fastest" else countryNameFromCode(cc),
                city = spec.city,
                endpoint = if (cc == "AUTO") "tor-onion://auto-fastest" else "tor-onion://exit.${cc.lowercase(Locale.US)}",
                serverPublicKey = "TOR_ONION_NO_VPS",
                clientAddress = "10.10.0.2/32",
                clientPrivateKey = "TOR_LOCAL_CIRCUIT",
                dns = "1.1.1.1",
                allowedIps = "0.0.0.0/0",
                mtu = 1500L,
                persistentKeepalive = 0L,
                latencyMs = latencies[spec.id] ?: -1L,
                loadPercent = spec.loadPercent,
                source = ServerSource.OPEN_SOURCE.wireValue,
                favorite = favorites.contains(spec.id)
            )
        }
    }
}

class OpenSourceNodeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getLatencies(): Map<String, Long> {
        val raw = prefs.getString(KEY_LATENCIES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { k ->
                    put(k, json.optLong(k, -1L))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun updateLatency(nodeId: String, latencyMs: Long) {
        val current = getLatencies().toMutableMap()
        current[nodeId] = latencyMs
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_LATENCIES, json.toString()).apply()
    }

    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun setFavorite(nodeId: String, favorite: Boolean) {
        val updated = getFavorites().toMutableSet()
        if (favorite) updated.add(nodeId) else updated.remove(nodeId)
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    private companion object {
        const val PREFS_NAME = "ultra_vpn_open_nodes_store"
        const val KEY_LATENCIES = "latencies_json"
        const val KEY_FAVORITES = "favorites_set"
    }
}
