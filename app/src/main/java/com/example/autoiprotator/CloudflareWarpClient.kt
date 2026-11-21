package com.example.autoiprotator

import android.content.Context
import android.os.Build
import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CloudflareWarpClient {

    private const val API =
        "https://api.cloudflareclient.com/v0a1922"

    private const val CLIENT_VERSION = "a-6.3-1922"

    private const val PREFS = "ultra_warp_settings"

    private const val PRIVATE_KEY = "private_key"
    private const val PROFILE = "warp_profile"
    private const val DEVICE_ID = "device_id"
    private const val TOKEN = "warp_token"

    private const val DNS = "1.1.1.1, 1.0.0.1"

    private fun getTosTimestamp(): String {
        val formatter = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        )
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    /**
     * Register device with Cloudflare WARP.
     * Must run on a background thread.
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

        require(privateKey.isNotBlank() && publicKey.isNotBlank()) {
            "WireGuard key generation failed."
        }

        val requestBody = JSONObject().apply {
            put("key", publicKey)
            put("install_id", "")
            put("fcm_token", "")
            put("tos", getTosTimestamp())
            put("model", Build.MODEL ?: "Android")
            put("serial_number", "")
            put("os_version", Build.VERSION.RELEASE ?: "")
            put("key_type", "curve25519")
            put("tunnel_type", "wireguard")
            put("locale", Locale.getDefault().toString())
        }

        val response = request(
            url = "$API/reg",
            method = "POST",
            body = requestBody
        )

        val root = try {
            JSONObject(response)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Invalid Cloudflare registration response: $response",
                e
            )
        }

        val id = root.optString("id")
        val token = root.optString("token")

        require(id.isNotBlank()) {
            "Cloudflare registration ID missing: $response"
        }

        val config = root.optJSONObject("config")
            ?: throw IllegalStateException(
                "Cloudflare configuration missing: $response"
            )

        val interfaceConfig =
            config.optJSONObject("interface")
                ?: throw IllegalStateException(
                    "WARP interface configuration missing."
                )

        val addresses = interfaceConfig.optJSONObject("addresses")
            ?: throw IllegalStateException(
                "WARP IP addresses missing."
            )

        val ipv4 = addresses.optString("v4")
        val ipv6 = addresses.optString("v6")

        val peers = config.optJSONArray("peers")
            ?: throw IllegalStateException(
                "WARP peer configuration missing."
            )

        require(peers.length() > 0) {
            "Cloudflare returned no WARP peers."
        }

        val peer = peers.getJSONObject(0)

        val serverPublicKey = peer.optString("public_key")

        val endpoint = peer.optJSONObject("endpoint")
            ?: throw IllegalStateException(
                "WARP endpoint missing."
            )

        val endpointHost = endpoint.optString("host")
        val endpointPort = endpoint.optInt("port", 2408)

        require(
            ipv4.isNotBlank() &&
            serverPublicKey.isNotBlank() &&
            endpointHost.isNotBlank()
        ) {
            "Incomplete WARP configuration."
        }

        fun withPrefix(address: String, prefix: Int): String {
            return if (address.contains("/")) {
                address
            } else {
                "$address/$prefix"
            }
        }

        val addressList = buildList {
            add(withPrefix(ipv4, 32))
            if (ipv6.isNotBlank()) {
                add(withPrefix(ipv6, 128))
            }
        }.joinToString(", ")

        val finalEndpoint = if (
            endpointHost.substringAfterLast(":").all { it.isDigit() } &&
            endpointHost.contains(":")
        ) {
            endpointHost
        } else {
            "$endpointHost:$endpointPort"
        }

        val profile = """
            [Interface]
            PrivateKey = $privateKey
            Address = $addressList
            DNS = $DNS
            MTU = 1280

            [Peer]
            PublicKey = $serverPublicKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = $finalEndpoint
            PersistentKeepalive = 25
        """.trimIndent()

        val editor = prefs.edit()
            .putString(PRIVATE_KEY, privateKey)
            .putString(PROFILE, profile)
            .putString(DEVICE_ID, id)

        if (token.isNotBlank()) {
            editor.putString(TOKEN, token)
        }

        editor.apply()

        return profile
    }

    /**
     * Get saved WireGuard profile.
     */
    fun getSavedProfile(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROFILE, null)
    }

    /**
     * Clear locally saved registration.
     */
    fun clearSavedProfile(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    /**
     * Get saved registration ID.
     */
    fun getDeviceId(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(DEVICE_ID, null)
    }

    /**
     * Verify Cloudflare connection.
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
     * HTTP request helper with detailed error response.
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
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.useCaches = false

            connection.setRequestProperty(
                "User-Agent",
                "okhttp/3.12.1"
            )

            connection.setRequestProperty(
                "CF-Client-Version",
                CLIENT_VERSION
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Connection",
                "Keep-Alive"
            )

            if (body != null) {
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
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
                    "Cloudflare API HTTP $code\n$response"
                )
            }

            return response

        } finally {
            connection.disconnect()
        }
    }
}
