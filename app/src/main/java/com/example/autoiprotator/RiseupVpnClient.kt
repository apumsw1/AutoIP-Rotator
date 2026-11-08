
package com.example.autoiprotator

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
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

    fun discoverServers(
        callback: (Result<List<RiseupServer>>) -> Unit
    ) {
        Thread({
            val result = runCatching {
                val ca = downloadCaCertificate()
                val ssl = createProviderSslContext(ca)

                val service = JSONObject(
                    httpsGet(EIP_URL, ssl)
                )

                val gateways = service.optJSONArray("gateways")
                    ?: error("Riseup API response has no gateways list.")

                val servers = mutableListOf<RiseupServer>()

                for (index in 0 until gateways.length()) {
                    val gateway =
                        gateways.optJSONObject(index) ?: continue

                    val ip = gateway.optString("ip_address").trim()

                    if (!isIpAddress(ip)) continue

                    val capabilities =
                        gateway.optJSONObject("capabilities")
                            ?: JSONObject()

                    val host = gateway.optString("host")
                        .ifBlank { ip }

                    val location = gateway.optString("location")
                        .ifBlank { "Riseup" }

                    val endpoint =
                        selectOpenVpnEndpoint(capabilities)
                            ?: continue

                    servers.add(
                        RiseupServer(
                            name = host,
                            ipAddress = ip,
                            location = location.replace('_', ' '),
                            protocol = endpoint.first,
                            port = endpoint.second
                        )
                    )
                }

                check(servers.isNotEmpty()) {
                    "Riseup API returned no usable OpenVPN gateways."
                }

                servers.distinctBy {
                    "${it.ipAddress}:${it.port}/${it.protocol}"
                }
            }

            callback(result)

        }, "Riseup-Discovery").start()
    }

    fun connect(
        context: Context,
        server: RiseupServer,
        callback: (Result<Unit>) -> Unit
    ) {
        Thread({
            val result = runCatching {

                check(VpnService.prepare(context) == null) {
                    "Android VPN permission has not been granted."
                }

                require(isIpAddress(server.ipAddress)) {
                    "Invalid Riseup gateway address."
                }

                require(server.protocol in listOf("udp", "tcp")) {
                    "Unsupported OpenVPN transport."
                }

                require(server.port in 1..65535) {
                    "Invalid Riseup gateway port."
                }

                val caPem = httpsGet(CA_URL)
                val caCertificate = parseCaCertificate(caPem)
                val ssl = createProviderSslContext(caCertificate)

                val eip = JSONObject(
                    httpsGet(EIP_URL, ssl)
                )

                val credentials = httpsGet(CERT_URL, ssl)

                val profile = buildOpenVpnProfile(
                    server = server,
                    eip = eip,
                    caPem = caPem,
                    credentialsPem = credentials
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

                Unit
            }

            callback(result)

        }, "Riseup-Connect").start()
    }

    fun disconnect(context: Context) {
        val intent = Intent(
            context,
            OpenVpnVpnService::class.java
        ).apply {
            action = OpenVpnVpnService.ACTION_DISCONNECT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun downloadCaCertificate(): X509Certificate {
        return parseCaCertificate(httpsGet(CA_URL))
    }

    private fun parseCaCertificate(
        pem: String
    ): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")

        return factory.generateCertificate(
            ByteArrayInputStream(
                pem.toByteArray(Charsets.UTF_8)
            )
        ) as X509Certificate
    }

    private fun createProviderSslContext(
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
                    "Client trust check is not used."
                )
            }

            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                require(chain.isNotEmpty()) {
                    "Provider returned an empty TLS certificate chain."
                }

                val pathCertificates = chain.filterNot {
                    it.subjectX500Principal ==
                        ca.subjectX500Principal
                }

                require(pathCertificates.isNotEmpty()) {
                    "Provider TLS certificate chain is invalid."
                }

                val path = CertificateFactory
                    .getInstance("X.509")
                    .generateCertPath(pathCertificates)

                val parameters = PKIXParameters(
                    setOf(TrustAnchor(ca, null))
                ).apply {
                    isRevocationEnabled = false
                }

                CertPathValidator
                    .getInstance("PKIX")
                    .validate(path, parameters)
            }
        }

        return SSLContext.getInstance("TLS").apply {
            init(
                null,
                arrayOf(trustManager),
                SecureRandom()
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
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
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
                connection.sslSocketFactory = ssl.socketFactory
            }

            val status = connection.responseCode

            if (status !in 200..299) {
                val errorBody = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }

                error(
                    "Riseup API HTTP $status" +
                        if (errorBody.isNullOrBlank()) {
                            ""
                        } else {
                            ": ${errorBody.take(180)}"
                        }
                )
            }

            return connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

        } finally {
            connection.disconnect()
        }
    }

    private fun selectOpenVpnEndpoint(
        capabilities: JSONObject
    ): Pair<String, Int>? {

        val transports =
            capabilities.optJSONArray("transport")

        if (transports != null) {
            for (i in 0 until transports.length()) {
                val item = transports.optJSONObject(i)
                    ?: continue

                if (!item.optString("type")
                        .contains("openvpn", ignoreCase = true)
                ) continue

                val protocols = stringValues(
                    item.optJSONArray("protocols")
                )

                val ports = stringValues(
                    item.optJSONArray("ports")
                ).mapNotNull {
                    it.toIntOrNull()
                }.filter {
                    it in 1..65535
                }

                val supported = mutableListOf<Pair<String, Int>>()

                if ("udp" in protocols) {
                    if (1194 in ports) {
                        supported.add("udp" to 1194)
                    }

                    if (80 in ports) {
                        supported.add("udp" to 80)
                    }
                }

                if ("tcp" in protocols && 53 in ports) {
                    supported.add("tcp" to 53)
                }

                if (supported.isNotEmpty()) {
                    return supported.first()
                }
            }
        }

        // Older EIP response compatibility

        val legacyTransport =
            capabilities.optJSONArray("transport")

        val hasLegacyOpenVpn =
            legacyTransport != null &&
                (0 until legacyTransport.length()).any {
                    legacyTransport.optString(it)
                        .equals("openvpn", true)
                }

        if (hasLegacyOpenVpn) {
            val protocols = stringValues(
                capabilities.optJSONArray("protocols")
            )

            val ports = stringValues(
                capabilities.optJSONArray("ports")
            ).mapNotNull {
                it.toIntOrNull()
            }

            if ("udp" in protocols && 1194 in ports) {
                return "udp" to 1194
            }

            if ("udp" in protocols && 80 in ports) {
                return "udp" to 80
            }

            if ("tcp" in protocols && 53 in ports) {
                return "tcp" to 53
            }
        }

        return null
    }

    private fun stringValues(
        array: JSONArray?
    ): List<String> {
        if (array == null) return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            when (val value = array.opt(index)) {
                is String -> value
                is Number -> value.toString()
                else -> null
            }
        }
    }

    private fun isIpAddress(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() }) {
            return false
        }

        return runCatching {
            InetAddress.getByName(value).hostAddress != null
        }.getOrDefault(false)
    }

    private fun buildOpenVpnProfile(
        server: RiseupServer,
        eip: JSONObject,
        caPem: String,
        credentialsPem: String
    ): String {

        val certificate = Regex(
            """-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----"""
        ).find(credentialsPem)?.value
            ?: error(
                "Riseup client certificate missing from /3/cert response."
            )

        val privateKey = Regex(
            """-----BEGIN (?:RSA |EC |)PRIVATE KEY-----[\s\S]*?-----END (?:RSA |EC |)PRIVATE KEY-----"""
        ).find(credentialsPem)?.value
            ?: error(
                "Riseup private key missing from /3/cert response."
            )

        val config = eip.optJSONObject("openvpn_configuration")
            ?: error(
                "Riseup EIP response has no OpenVPN configuration."
            )

        val profile = StringBuilder()

        profile.appendLine("client")
        profile.appendLine("dev tun")
        profile.appendLine("proto ${server.protocol}")
        profile.appendLine(
            "remote ${server.ipAddress} ${server.port}"
        )
        profile.appendLine("resolv-retry infinite")
        profile.appendLine("nobind")
        profile.appendLine("persist-key")
        profile.appendLine("persist-tun")
        profile.appendLine("remote-cert-tls server")
        profile.appendLine("auth-nocache")
        profile.appendLine("verb 3")

        val allowedOptions = setOf(
            "auth",
            "cipher",
            "data-ciphers",
            "tls-cipher",
            "tls-version-min",
            "key-direction",
            "reneg-sec",
            "remote-random",
            "explicit-exit-notify",
            "tun-mtu"
        )

        for (key in allowedOptions) {
            val value = config.opt(key) ?: continue

            when (value) {
                is String, is Number -> {
                    val optionValue = value.toString()

                    require(
                        optionValue.matches(
                            Regex("""[A-Za-z0-9_.:+,/ -]{1,160}""")
                        )
                    ) {
                        "Invalid value in Riseup OpenVPN option: $key"
                    }

                    profile.appendLine("$key $optionValue")
                }

                is JSONArray -> {
                    val values = stringValues(value)

                    if (
                        values.isNotEmpty() &&
                        values.all {
                            it.matches(
                                Regex("""[A-Za-z0-9_.:+,/ -]{1,80}""")
                            )
                        }
                    ) {
                        profile.appendLine(
                            "$key ${values.joinToString(" ")}"
                        )
                    }
                }
            }
        }

        profile.appendLine("<ca>")
        profile.appendLine(caPem.trim())
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
