package com.example.autoiprotator

import android.content.Context
import android.os.Build
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object CloudflareWarpClient {

    private const val API =
        "https://api.cloudflareclient.com/v0a1922"

    private const val PREFS = "ultra_warp_settings"

    private const val PRIVATE_KEY = "private_key"
    private const val PROFILE = "warp_profile"
    private const val DEVICE_ID = "device_id"

    private const val DNS = "1.1.1.1, 1.0.0.1"

    /**
     * Registers a device with Cloudflare WARP
     * and returns a WireGuard configuration.
     *
     * Call from a background thread.
     */
    @Synchronized
    fun register(context: Context): String {

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val savedProfile = prefs.getString(PROFILE, null)

        if (!savedProfile.isNullOrBlank()) {
            return savedProfile
        }

        val keyPair = KeyPair()

        val privateKey = keyPair.privateKey.toBase64()
        val publicKey = keyPair.publicKey.toBase64()

        val requestBody = JSONObject().apply {
            put("key", publicKey)
            put("install_id", "")
            put("fcm_token", "")
            put("referrer", "")
            put("warp_enabled", true)
            put("tos", "")
            put("type", "Android")
            put("model", Build.MODEL)
            put("name", "ULTRA VPN")
            put("locale", Locale.getDefault().toString())
        }

        val response = request(
            url = "$API/reg",
            method = "POST",
            body = requestBody
        )

        val root = JSONObject(response)

        val id = root.optString("id")

        require(id.isNotBlank()) {
            "Cloudflare registration ID missing."
        }

        val config = root.optJSONObject("config")
            ?: throw IllegalStateException(
                "Cloudflare configuration missing."
            )

        val interfaceConfig =
            config.optJSONObject("interface")
                ?: throw IllegalStateException(
                    "WARP interface configuration missing."
                )

        val peers = config.optJSONArray("peers")
            ?: throw IllegalStateException(
                "WARP peer configuration missing."
            )

        require(peers.length() > 0) {
            "Cloudflare returned no WARP peers."
        }

        val peer = peers.getJSONObject(0)

        val addresses = interfaceConfig.optJSONObject("addresses")
            ?: throw IllegalStateException(
                "WARP IP addresses missing."
            )

        val ipv4 = addresses.optString("v4")
        val ipv6 = addresses.optString("v6")

        val serverPublicKey = peer.optString("public_key")

        val endpointObject = peer.optJSONObject("endpoint")
            ?: throw IllegalStateException(
                "WARP endpoint missing."
            )

        val endpointHost = endpointObject.optString("host")
        val endpointPort = endpointObject.optInt("port", 2408)

        require(
            privateKey.isNotBlank() &&
            ipv4.isNotBlank() &&
            serverPublicKey.isNotBlank() &&
            endpointHost.isNotBlank()
        ) {
            "Incomplete WARP configuration."
        }

        val addressList = buildList {
            add(ipv4)
            if (ipv6.isNotBlank()) add(ipv6)
        }.joinToString(", ")

        val profile = """
            [Interface]
            PrivateKey = $privateKey
            Address = $addressList
            DNS = $DNS
            MTU = 1280

            [Peer]
            PublicKey = $serverPublicKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = $endpointHost:$endpointPort
            PersistentKeepalive = 25
        """.trimIndent()

        prefs.edit()
            .putString(PRIVATE_KEY, privateKey)
            .putString(PROFILE, profile)
            .putString(DEVICE_ID, id)
            .apply()

        return profile
    }

    /**
     * Returns an already registered WARP profile.
     */
    fun getSavedProfile(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROFILE, null)
    }

    /**
     * Clears locally saved WARP registration.
     * This does not guarantee server-side deregistration.
     */
    fun clearSavedProfile(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    /**
     * Checks Cloudflare connection information.
     */
    fun checkTrace(): Map<String, String> {

        val response = request(
            url = "https://www.cloudflare.com/cdn-cgi/trace",
            method = "GET"
        )

        return response.lines()
            .mapNotNull { line ->
                val index = line.indexOf("=")

                if (index <= 0) {
                    null
                } else {
                    line.substring(0, index) to
                        line.substring(index + 1)
                }
            }
            .toMap()
    }

    /**
     * HTTP request helper.
     */
    private fun request(
        url: String,
        method: String,
        body: JSONObject? = null
    ): String {

        val connection =
            URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.setRequestProperty(
                "User-Agent",
                "okhttp/3.12.1"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (body != null) {
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use {
                    it.write(body.toString())
                    it.flush()
                }
            }

            val code = connection.responseCode

            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream?.bufferedReader()
                ?.use { it.readText() }
                ?: ""

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Cloudflare API error $code: $response"
                )
            }

            return response

        } finally {
            connection.disconnect()
        }
    }
}
