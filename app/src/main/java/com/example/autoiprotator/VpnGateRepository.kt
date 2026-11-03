
package com.example.autoiprotator

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class VpnGateServer(
    val hostName: String,
    val ip: String,
    val country: String,
    val speedMbps: Double,
    val pingMs: Int,
    val sessions: Int,
    val openVpnConfig: String
)

object VpnGateRepository {

    private const val API_URL =
        "https://www.vpngate.net/api/iphone/"

    fun fetchServers(): List<VpnGateServer> {
        val connection =
            URL(API_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            connection.setRequestProperty(
                "User-Agent",
                "AutoIP-Rotator/1.0 Android"
            )

            check(connection.responseCode in 200..299) {
                "VPN Gate server list unavailable"
            }

            val csv = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            return parseCsv(csv)

        } finally {
            connection.disconnect()
        }
    }

    private fun parseCsv(csv: String): List<VpnGateServer> {
        val lines = csv.lineSequence()
            .filter { it.isNotBlank() }
            .toList()

        val headerIndex = lines.indexOfFirst {
            it.trimStart()
                .removePrefix("#")
                .startsWith("HostName", true)
        }

        require(headerIndex >= 0) {
            "Invalid VPN Gate server list"
        }

        val headers = parseLine(
            lines[headerIndex].trim().removePrefix("#")
        ).map { it.trim() }

        val indexes = headers.mapIndexed { index, name ->
            name.lowercase(Locale.ROOT) to index
        }.toMap()

        fun col(name: String, fallback: Int): Int =
            indexes[name.lowercase(Locale.ROOT)] ?: fallback

        val hostIndex = col("HostName", 0)
        val ipIndex = col("IP", 1)
        val pingIndex = col("Ping", 3)
        val speedIndex = col("Speed", 4)
        val countryIndex = col("CountryLong", 5)
        val sessionsIndex = col("NumVpnSessions", 7)
        val configIndex = col("OpenVPN_ConfigData_Base64", 14)

        val result = mutableListOf<VpnGateServer>()

        for (line in lines.drop(headerIndex + 1)) {
            if (line.startsWith("*") || line.startsWith("#")) {
                continue
            }

            val fields = try {
                parseLine(line)
            } catch (_: Exception) {
                continue
            }

            val requiredIndex = maxOf(
                hostIndex,
                ipIndex,
                pingIndex,
                speedIndex,
                countryIndex,
                sessionsIndex,
                configIndex
            )

            if (fields.size <= requiredIndex) continue

            val host = fields[hostIndex].trim()
            val ip = fields[ipIndex].trim()
            val encodedConfig = fields[configIndex].trim()

            if (host.isBlank() ||
                ip.isBlank() ||
                encodedConfig.isBlank()
            ) {
                continue
            }

            val config = try {
                Base64.decode(
                    encodedConfig,
                    Base64.DEFAULT
                ).toString(Charsets.UTF_8)
            } catch (_: Exception) {
                continue
            }

            if (!config.contains("client") ||
                !config.contains("remote ")
            ) {
                continue
            }

            val speed =
                fields[speedIndex].toDoubleOrNull() ?: 0.0

            result.add(
                VpnGateServer(
                    hostName = host,
                    ip = ip,
                    country = fields[countryIndex]
                        .trim()
                        .ifBlank { "Unknown" },
                    speedMbps = speed / 1_000_000.0,
                    pingMs = fields[pingIndex]
                        .toIntOrNull() ?: -1,
                    sessions = fields[sessionsIndex]
                        .toIntOrNull() ?: 0,
                    openVpnConfig = config
                )
            )
        }

        return result
            .distinctBy { it.ip }
            .sortedWith(
                compareByDescending<VpnGateServer> {
                    it.speedMbps
                }.thenBy {
                    if (it.pingMs >= 0) {
                        it.pingMs
                    } else {
                        Int.MAX_VALUE
                    }
                }
            )
    }

    private fun parseLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val char = line[index]

            when {
                char == '"' &&
                    quoted &&
                    index + 1 < line.length &&
                    line[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }

                char == '"' -> {
                    quoted = !quoted
                }

                char == ',' && !quoted -> {
                    values.add(field.toString())
                    field.setLength(0)
                }

                else -> {
                    field.append(char)
                }
            }

            index++
        }

        values.add(field.toString())
        return values
    }
}
