package com.example.autoiprotator

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class RiseupServer(
    val name: String,
    val ipAddress: String,
    val location: String,
    val protocol: String,
    val port: Int
)

object RiseupVpnClient {

    private const val CA_URL =
        "https://black.riseup.net/ca.crt"

    private const val API_BASE =
        "https://api.black.riseup.net/3"

    private const val EIP_URL =
        "$API_BASE/config/eip-service.json"

    private const val CERT_URL =
        "$API_BASE/cert"

    /**
     * Fetches the Riseup CA certificate, verifies the API TLS connection,
     * and returns available OpenVPN gateways.
     */
    fun discoverServers(
        callback: (Result<List<RiseupServer>>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val ca = downloadCaCertificate()
                val ssl = createPinnedSslContext(ca)

                val json = JSONObject(
                    httpsGet(EIP_URL, ssl)
                )

                val gateways = json.getJSONArray("gateways")
                val servers = mutableListOf<RiseupServer>()

                for (i in 0 until gateways.length()) {
                    val gateway = gateways.getJSONObject(i)
                    val capabilities =
                        gateway.optJSONObject("capabilities")
                            ?: continue

                    val ip = gateway.optString("ip_address")
                    if (ip.isBlank()) continue

                    val host = gateway.optString("host", ip)
                    val location =
                        gateway.optString("location", "Unknown")

                    val selection = findOpenVpnTransport(
                        capabilities
                    ) ?: continue

                    servers.add(
                        RiseupServer(
                            name = host,
                            ipAddress = ip,
                            location = location,
                            protocol = selection.first,
                            port = selection.second
                        )
                    )
                }

                check(servers.isNotEmpty()) {
                    "Riseup did not return any OpenVPN servers."
                }

                servers
            }

            callback(result)
        }.start()
    }

    /**
     * Downloads fresh client credentials and creates a real OpenVPN
     * profile before starting the existing OpenVPN service.
     */
    fun connect(
        context: Context,
        server: RiseupServer,
        callback: (Result<Unit>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                check(VpnService.prepare(context) == null) {
                    "VPN permission is required. Grant VPN permission first."
                }

                val ca = downloadCaCertificate()
                val ssl = createPinnedSslContext(ca)

                val eip = JSONObject(
                    httpsGet(EIP_URL, ssl)
                )

                val credentialPem = httpsGet(CERT_URL, ssl)

                val profile = buildOpenVpnProfile(
                    server = server,
                    eip = eip,
                    caPem = ca,
                    credentialPem = credentialPem
                )

                val intent = Intent(
                    context,
                    OpenVpnVpnService::class.java
                ).apply {
                    action = OpenVpnVpnService.ACTION_CONNECT
                    putExtra(
                        OpenVpnVpnService.EXTRA_CONFIG,
                        profile
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }

            callback(result)
        }.start()
    }

    fun disconnect(context: Context) {
        val intent = Intent(
            context,
            OpenVpnVpnService::class.java
        ).apply {
            action = OpenVpnVpnService.ACTION_DISCONNECT
        }

        context.startService(intent)
    }

    private fun downloadCaCertificate(): X509Certificate {
        val pem = httpsGet(CA_URL)
        val factory = CertificateFactory.getInstance("X.509")

        return factory.generateCertificate(
            ByteArrayInputStream(pem.toByteArray())
        ) as X509Certificate
    }

    /**
     * Trusts the Riseup CA for API calls while retaining normal
     * HTTPS hostname verification.
     */
    private fun createPinnedSslContext(
        ca: X509Certificate
    ): SSLContext {
        val trustManager = object : X509TrustManager {

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf(ca)
            }

            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                throw UnsupportedOperationException(
                    "Client certificate validation is not used here."
                )
            }

            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                require(chain.isNotEmpty()) {
                    "The API server supplied no certificate."
                }

                val certificates = chain
                    .filterNot { it == ca }

                val certPath = CertificateFactory
                    .getInstance("X.509")
                    .generateCertPath(certificates)

                val parameters =
                    java.security.cert.PKIXParameters(
                        setOf(
                            java.security.cert.TrustAnchor(ca, null)
                        )
                    ).apply {
                        isRevocationEnabled = false
                    }

                java.security.cert.CertPathValidator
                    .getInstance("PKIX")
                    .validate(certPath, parameters)
            }
        }

        return SSLContext.getInstance("TLS").apply {
            init(
                null,
                arrayOf(trustManager),
                java.security.SecureRandom()
            )
        }
    }

    private fun httpsGet(
        address: String,
        ssl: SSLContext? = null
    ): String {
        val connection =
            URL(address).openConnection() as HttpsURLConnection

        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "Accept",
                "application/json, application/x-pem-file, */*"
            )
            connection.setRequestProperty(
                "User-Agent",
                "AutoIP-Rotator/1.0"
            )

            if (ssl != null) {
                connection.sslSocketFactory =
                    ssl.socketFactory
            }

            val status = connection.responseCode

            check(status in 200..299) {
                "Riseup API returned HTTP $status"
            }

            return connection.inputStream
                .bufferedReader()
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun findOpenVpnTransport(
        capabilities: JSONObject
    ): Pair<String, Int>? {

        val transport = capabilities.optJSONArray("transport")
        val fallbackPorts =
            capabilities.optJSONArray("ports")
        val fallbackProtocols =
            capabilities.optJSONArray("protocols")

        if (transport != null) {
            for (i in 0 until transport.length()) {
                val item = transport.opt(i)

                if (item is String) {
                    if (!item.equals("openvpn", true)) continue

                    val protocol = firstString(fallbackProtocols)
                        ?: continue
                    val port = firstString(fallbackPorts)
                        ?.toIntOrNull() ?: continue

                    if (protocol in listOf("udp", "tcp")) {
                        return protocol to port
                    }
                }

                if (item is JSONObject) {
                    if (!item.optString("type")
                            .equals("openvpn", true)
                    ) continue

                    val protocols =
                        item.optJSONArray("protocols")
                    val ports = item.optJSONArray("ports")

                    val protocol = firstString(protocols)
                        ?: continue
                    val port = firstString(ports)
                        ?.toIntOrNull() ?: continue

                    if (protocol in listOf("udp", "tcp")) {
                        return protocol to port
                    }
                }
            }
        }

        return null
    }

    private fun firstString(array: org.json.JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return array.optString(0).takeIf { it.isNotBlank() }
    }

    private fun buildOpenVpnProfile(
        server: RiseupServer,
        eip: JSONObject,
        caPem: X509Certificate,
        credentialPem: String
    ): String {

        require(server.ipAddress.matches(
            Regex("""[0-9a-fA-F:.]+""")
        )) {
            "Invalid VPN server address."
        }

        require(server.port in 1..65535) {
            "Invalid VPN server port."
        }

        val certificate = Regex(
            """-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----"""
        ).find(credentialPem)?.value
            ?: error("Riseup client certificate was not found.")

        val privateKey = Regex(
            """-----BEGIN (?:RSA |EC |)PRIVATE KEY-----[\s\S]*?-----END (?:RSA |EC |)PRIVATE KEY-----"""
        ).find(credentialPem)?.value
            ?: error("Riseup client private key was not found.")

        val caText = caPem.let {
            // Re-fetch the original PEM so the complete CA block is embedded.
            httpsGet(CA_URL)
        }

        val options =
            eip.optJSONObject("openvpn_configuration")
                ?: JSONObject()

        val allowedOptions = listOf(
            "auth",
            "cipher",
            "data-ciphers",
            "tls-cipher",
            "tls-version-min",
            "keepalive",
            "key-direction",
            "verb",
            "sndbuf",
            "rcvbuf"
        )

        val profile = StringBuilder()

        profile.appendLine("client")
        profile.appendLine("dev tun")
        profile.appendLine("proto ${server.protocol}")
        profile.appendLine("remote ${server.ipAddress} ${server.port}")
        profile.appendLine("resolv-retry infinite")
        profile.appendLine("nobind")
        profile.appendLine("persist-key")
        profile.appendLine("persist-tun")
        profile.appendLine("remote-cert-tls server")
        profile.appendLine("auth-nocache")

        for (key in allowedOptions) {
            val value = options.opt(key) ?: continue
            val text = value.toString()

            if (text.isBlank()) continue

            require(text.matches(Regex("""[A-Za-z0-9_.:+/ -]{1,100}"""))) {
                "Invalid OpenVPN option received: $key"
            }

            profile.appendLine("$key $text")
        }

        profile.appendLine("<ca>")
        profile.appendLine(caText.trim())
        profile.appendLine("</ca>")

        profile.appendLine("<cert>")
        profile.appendLine(certificate)
        profile.appendLine("</cert>")

        profile.appendLine("<key>")
        profile.appendLine(privateKey)
        profile.appendLine("</key>")

        return profile.toString()
    }
}