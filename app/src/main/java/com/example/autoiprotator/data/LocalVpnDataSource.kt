package com.example.autoiprotator.data

import android.content.Context
import com.example.autoiprotator.BuildConfig
import com.example.autoiprotator.model.AuditEventType
import com.example.autoiprotator.model.ConnectionAuditLog
import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.UserPreferences
import com.example.autoiprotator.model.VpnServerProfile
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class LocalVpnDataSource(context: Context) : VpnDataSource {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val mutex = Mutex()

    private val _userPrefs = MutableStateFlow(loadPreferences())
    private val _servers = MutableStateFlow(sortServers(loadServers()))
    private val _logs = MutableStateFlow(loadLogs())

    override fun observeUserPreferences(userId: String): Flow<UserPreferences?> =
        _userPrefs.asStateFlow()

    override suspend fun ensureUserPreferences(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                if (_userPrefs.value == null) {
                    val created = UserPreferences(
                        id = userId,
                        userId = userId,
                        displayName = displayName.ifBlank { "LOCAL OPERATOR" },
                        email = email,
                        workerBaseUrl = BuildConfig.DEFAULT_WORKER_URL.trim(),
                        autoSelectEnabled = true,
                        preferredCountryCode = "AUTO",
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                    savePreferences(created)
                    _userPrefs.value = created
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateWorkerSettings(
        userId: String,
        workerBaseUrl: String,
        autoSelectEnabled: Boolean,
        preferredCountryCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                val base = _userPrefs.value ?: UserPreferences(
                    id = userId,
                    userId = userId,
                    displayName = "LOCAL OPERATOR"
                )
                val updated = base.copy(
                    workerBaseUrl = workerBaseUrl.trim().take(500),
                    autoSelectEnabled = autoSelectEnabled,
                    preferredCountryCode = preferredCountryCode.trim().uppercase(Locale.US)
                        .ifBlank { "AUTO" }.take(12),
                    updatedAt = Timestamp.now()
                )
                savePreferences(updated)
                _userPrefs.value = updated
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeServers(userId: String): Flow<List<VpnServerProfile>> =
        _servers.asStateFlow()

    override suspend fun upsertServer(userId: String, server: VpnServerProfile): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                require(server.name.isNotBlank()) { "Server name is required." }
                require(server.endpoint.length >= 3) { "Endpoint is invalid." }
                require(server.serverPublicKey.length >= 40) { "Server public key is invalid." }
                require(server.clientPrivateKey.length >= 40) { "Client private key is invalid." }

                val finalId = mutex.withLock {
                    val cleanId = server.id
                        .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        .take(120)
                        .ifBlank { UUID.randomUUID().toString() }

                    val current = _servers.value.toMutableList()
                    val existingIdx = current.indexOfFirst { it.id == cleanId }
                    val now = Timestamp.now()
                    val normalized = server.copy(
                        id = cleanId,
                        userId = userId,
                        name = server.name.take(100),
                        countryCode = server.countryCode.uppercase(Locale.US).take(8),
                        countryName = server.countryName.take(80),
                        city = server.city.take(80),
                        endpoint = server.endpoint.take(200),
                        serverPublicKey = server.serverPublicKey.take(64),
                        clientAddress = server.clientAddress.take(120),
                        clientPrivateKey = server.clientPrivateKey.take(64),
                        presharedKey = server.presharedKey?.takeIf { it.isNotBlank() }?.take(64),
                        dns = server.dns.take(120),
                        allowedIps = server.allowedIps.take(120),
                        mtu = server.mtu.coerceIn(1200L, 1500L),
                        persistentKeepalive = server.persistentKeepalive.coerceIn(0L, 120L),
                        latencyMs = server.latencyMs.coerceIn(-1L, 60000L),
                        loadPercent = server.loadPercent.coerceIn(0L, 100L),
                        source = ServerSource.fromRaw(server.source).wireValue,
                        createdAt = if (existingIdx >= 0) current[existingIdx].createdAt ?: now else now,
                        updatedAt = now
                    )
                    if (existingIdx >= 0) {
                        current[existingIdx] = normalized
                    } else {
                        current.add(normalized)
                    }
                    val sorted = sortServers(current)
                    saveServers(sorted)
                    _servers.value = sorted
                    cleanId
                }
                Result.success(finalId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun toggleFavorite(
        userId: String,
        serverId: String,
        favorite: Boolean
    ): Result<Unit> = updateServerInPlace(serverId) {
        it.copy(favorite = favorite, updatedAt = Timestamp.now())
    }

    override suspend fun updateServerLatency(
        userId: String,
        serverId: String,
        latencyMs: Long
    ): Result<Unit> = updateServerInPlace(serverId) {
        it.copy(latencyMs = latencyMs.coerceIn(-1L, 60000L), updatedAt = Timestamp.now())
    }

    override suspend fun rotateClientKey(
        userId: String,
        serverId: String,
        newPrivateKey: String,
        newClientAddress: String?
    ): Result<Unit> = updateServerInPlace(serverId) {
        require(newPrivateKey.length in 40..64) { "Invalid rotated private key." }
        it.copy(
            clientPrivateKey = newPrivateKey,
            clientAddress = newClientAddress?.takeIf { addr -> addr.isNotBlank() }?.take(120)
                ?: it.clientAddress,
            updatedAt = Timestamp.now()
        )
    }

    override suspend fun deleteServer(userId: String, serverId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(serverId.isNotBlank()) { "Server ID must not be blank." }
                mutex.withLock {
                    val updated = _servers.value.filterNot { it.id == serverId }
                    saveServers(updated)
                    _servers.value = updated
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun observeLogs(userId: String): Flow<List<ConnectionAuditLog>> = _logs.asStateFlow()

    override suspend fun appendLog(
        userId: String,
        serverName: String,
        countryCode: String,
        verifiedIp: String,
        verifiedCountry: String,
        eventType: AuditEventType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                val entry = ConnectionAuditLog(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    serverName = serverName.trim().ifBlank { "WireGuard Node" }.take(100),
                    countryCode = countryCode.trim().uppercase(Locale.US).ifBlank { "XX" }.take(8),
                    verifiedIp = verifiedIp.trim().take(64),
                    verifiedCountry = verifiedCountry.trim().take(80),
                    eventType = eventType.wireValue,
                    createdAt = Timestamp.now()
                )
                val updated = (listOf(entry) + _logs.value).take(25)
                saveLogs(updated)
                _logs.value = updated
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateServerInPlace(
        serverId: String,
        transform: (VpnServerProfile) -> VpnServerProfile
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(serverId.isNotBlank()) { "Server ID must not be blank." }
            mutex.withLock {
                val updated = _servers.value.map {
                    if (it.id == serverId) transform(it) else it
                }
                val sorted = sortServers(updated)
                saveServers(sorted)
                _servers.value = sorted
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sortServers(list: List<VpnServerProfile>): List<VpnServerProfile> =
        list.sortedWith(
            compareByDescending<VpnServerProfile> { it.favorite }
                .thenBy { if (it.latencyMs < 0L) Long.MAX_VALUE else it.latencyMs }
                .thenBy { it.countryName }
        )

    private fun loadPreferences(): UserPreferences? {
        val raw = prefs.getString(KEY_PREFS, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            UserPreferences(
                id = o.optString("id", "local_device"),
                userId = o.optString("userId", "local_device"),
                displayName = o.optString("displayName", "LOCAL OPERATOR"),
                email = o.optString("email", "").takeIf { it.isNotBlank() },
                workerBaseUrl = o.optString("workerBaseUrl", ""),
                autoSelectEnabled = o.optBoolean("autoSelectEnabled", true),
                preferredCountryCode = o.optString("preferredCountryCode", "AUTO")
            )
        }.getOrNull()
    }

    private fun savePreferences(p: UserPreferences) {
        val o = JSONObject().apply {
            put("id", p.id)
            put("userId", p.userId)
            put("displayName", p.displayName)
            put("email", p.email.orEmpty())
            put("workerBaseUrl", p.workerBaseUrl)
            put("autoSelectEnabled", p.autoSelectEnabled)
            put("preferredCountryCode", p.preferredCountryCode)
        }
        prefs.edit().putString(KEY_PREFS, o.toString()).apply()
    }

    private fun loadServers(): List<VpnServerProfile> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        VpnServerProfile(
                            id = o.optString("id"),
                            userId = o.optString("userId", "local_device"),
                            name = o.optString("name"),
                            countryCode = o.optString("countryCode", "US"),
                            countryName = o.optString("countryName", "United States"),
                            city = o.optString("city"),
                            endpoint = o.optString("endpoint"),
                            serverPublicKey = o.optString("serverPublicKey"),
                            clientAddress = o.optString("clientAddress", "10.0.0.2/32"),
                            clientPrivateKey = o.optString("clientPrivateKey"),
                            presharedKey = o.optString("presharedKey").takeIf { it.isNotBlank() },
                            dns = o.optString("dns", "1.1.1.1, 1.0.0.1"),
                            allowedIps = o.optString("allowedIps", "0.0.0.0/0, ::/0"),
                            mtu = o.optLong("mtu", 1280L),
                            persistentKeepalive = o.optLong("persistentKeepalive", 25L),
                            latencyMs = o.optLong("latencyMs", -1L),
                            loadPercent = o.optLong("loadPercent", 0L),
                            source = o.optString("source", ServerSource.MANUAL.wireValue),
                            favorite = o.optBoolean("favorite", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveServers(list: List<VpnServerProfile>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("userId", s.userId)
                    put("name", s.name)
                    put("countryCode", s.countryCode)
                    put("countryName", s.countryName)
                    put("city", s.city)
                    put("endpoint", s.endpoint)
                    put("serverPublicKey", s.serverPublicKey)
                    put("clientAddress", s.clientAddress)
                    put("clientPrivateKey", s.clientPrivateKey)
                    put("presharedKey", s.presharedKey.orEmpty())
                    put("dns", s.dns)
                    put("allowedIps", s.allowedIps)
                    put("mtu", s.mtu)
                    put("persistentKeepalive", s.persistentKeepalive)
                    put("latencyMs", s.latencyMs)
                    put("loadPercent", s.loadPercent)
                    put("source", s.source)
                    put("favorite", s.favorite)
                }
            )
        }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    private fun loadLogs(): List<ConnectionAuditLog> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        ConnectionAuditLog(
                            id = o.optString("id"),
                            userId = o.optString("userId", "local_device"),
                            serverName = o.optString("serverName"),
                            countryCode = o.optString("countryCode", "XX"),
                            verifiedIp = o.optString("verifiedIp"),
                            verifiedCountry = o.optString("verifiedCountry"),
                            eventType = o.optString("eventType", AuditEventType.CONNECTED.wireValue)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLogs(list: List<ConnectionAuditLog>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("userId", l.userId)
                    put("serverName", l.serverName)
                    put("countryCode", l.countryCode)
                    put("verifiedIp", l.verifiedIp)
                    put("verifiedCountry", l.verifiedCountry)
                    put("eventType", l.eventType)
                }
            )
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "ultra_vpn_local_store"
        const val KEY_PREFS = "prefs_json"
        const val KEY_SERVERS = "servers_json"
        const val KEY_LOGS = "logs_json"
    }
}
