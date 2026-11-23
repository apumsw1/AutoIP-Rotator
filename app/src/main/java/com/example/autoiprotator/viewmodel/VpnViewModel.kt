package com.example.autoiprotator.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoiprotator.TunnelTrafficStats
import com.example.autoiprotator.VpnEngine
import com.example.autoiprotator.data.VpnRepository
import com.example.autoiprotator.model.AuditEventType
import com.example.autoiprotator.model.ConnectionAuditLog
import com.example.autoiprotator.model.IpVerificationInfo
import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.model.UiState
import com.example.autoiprotator.model.UserPreferences
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.network.CloudflareWorkerApiClient
import com.example.autoiprotator.network.IpVerificationClient
import com.example.autoiprotator.network.ServerLatencyProber
import com.example.autoiprotator.vpn.WireGuardConfigParser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnViewModel(
    private val repository: VpnRepository,
    private val vpnEngine: VpnEngine,
    private val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String?
) : ViewModel() {

    val preferencesState: StateFlow<UiState<UserPreferences?>> =
        repository.observeUserPreferences(currentUserId)
            .map<UserPreferences?, UiState<UserPreferences?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user preferences", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load user preferences"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val serversState: StateFlow<UiState<List<VpnServerProfile>>> =
        repository.observeServers(currentUserId)
            .map<List<VpnServerProfile>, UiState<List<VpnServerProfile>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing servers", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load VPN servers"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val logsState: StateFlow<UiState<List<ConnectionAuditLog>>> =
        repository.observeLogs(currentUserId)
            .map<List<ConnectionAuditLog>, UiState<List<ConnectionAuditLog>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing logs", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load audit logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _tunnelStatus = MutableStateFlow(
        if (vpnEngine.isConnected()) TunnelStatus.CONNECTED else TunnelStatus.DISCONNECTED
    )
    val tunnelStatus = _tunnelStatus.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId = _selectedServerId.asStateFlow()

    private val _selectedCountryFilter = MutableStateFlow("ALL")
    val selectedCountryFilter = _selectedCountryFilter.asStateFlow()

    private val _ipInfo = MutableStateFlow<IpVerificationInfo?>(null)
    val ipInfo = _ipInfo.asStateFlow()

    private val _baselineIp = MutableStateFlow<String?>(null)
    val baselineIp = _baselineIp.asStateFlow()

    private val _isVerifyingIp = MutableStateFlow(false)
    val isVerifyingIp = _isVerifyingIp.asStateFlow()

    private val _trafficStats = MutableStateFlow(TunnelTrafficStats())
    val trafficStats = _trafficStats.asStateFlow()

    private val _isSyncingWorker = MutableStateFlow(false)
    val isSyncingWorker = _isSyncingWorker.asStateFlow()

    private val _isProbingLatencies = MutableStateFlow(false)
    val isProbingLatencies = _isProbingLatencies.asStateFlow()

    // Held strictly in memory (never written to Firestore or logs)
    private val _workerBearerToken = MutableStateFlow("")
    val workerBearerToken = _workerBearerToken.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage = _bannerMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var statsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureUserPreferences(currentUserId, initialDisplayName, initialEmail)
        }
        verifyPublicIp(recordBaseline = !vpnEngine.isConnected())
        if (vpnEngine.isConnected()) {
            startStatsPolling()
        }
    }

    fun selectCountryFilter(countryCode: String) {
        _selectedCountryFilter.value = countryCode
    }

    fun selectServer(serverId: String) {
        _selectedServerId.value = serverId
    }

    fun updateWorkerBearerTokenInMemory(token: String) {
        _workerBearerToken.value = token
    }

    fun dismissMessages() {
        _bannerMessage.value = null
        _errorMessage.value = null
    }

    fun currentServerList(): List<VpnServerProfile> =
        (serversState.value as? UiState.Success)?.data.orEmpty()

    fun resolveActiveServer(): VpnServerProfile? {
        val list = currentServerList()
        if (list.isEmpty()) return null
        val selected = _selectedServerId.value?.let { id -> list.firstOrNull { it.id == id } }
        if (selected != null) return selected
        val prefs = (preferencesState.value as? UiState.Success)?.data
        return ServerLatencyProber.selectBestServer(list, prefs?.preferredCountryCode ?: "AUTO")
    }

    fun connectWithAutoOrSelected(requestPermission: () -> Unit) {
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        startTunnelInternal(forceAutoSelect = false)
    }

    fun connectSpecificServer(server: VpnServerProfile, requestPermission: () -> Unit) {
        _selectedServerId.value = server.id
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        startTunnelInternal(forceAutoSelect = false)
    }

    fun autoSelectAndConnect(requestPermission: () -> Unit) {
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        startTunnelInternal(forceAutoSelect = true)
    }

    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) {
            startTunnelInternal(forceAutoSelect = false)
        } else {
            _errorMessage.value = "Android VPN permission is required to activate the WireGuard tunnel."
        }
    }

    private fun startTunnelInternal(forceAutoSelect: Boolean) {
        val servers = currentServerList()
        if (servers.isEmpty()) {
            _errorMessage.value = "No WireGuard servers configured yet. Scan a QR code or sync your Cloudflare Worker API."
            return
        }

        viewModelScope.launch {
            _errorMessage.value = null
            _bannerMessage.value = null
            val prefs = (preferencesState.value as? UiState.Success)?.data
            val useAuto = forceAutoSelect || (prefs?.autoSelectEnabled == true && _selectedServerId.value == null)

            val targetServer = if (useAuto) {
                _tunnelStatus.value = TunnelStatus.AUTO_SELECTING
                val latencies = ServerLatencyProber.measureServers(servers)
                val updatedList = servers.map { s ->
                    val measured = latencies[s.id] ?: s.latencyMs
                    if (measured != s.latencyMs) {
                        repository.updateServerLatency(currentUserId, s.id, measured)
                    }
                    s.copy(latencyMs = measured)
                }
                ServerLatencyProber.selectBestServer(
                    updatedList,
                    prefs?.preferredCountryCode ?: "AUTO"
                )
            } else {
                resolveActiveServer()
            }

            if (targetServer == null) {
                _tunnelStatus.value = TunnelStatus.DISCONNECTED
                _errorMessage.value = "Unable to select a WireGuard server."
                return@launch
            }

            _selectedServerId.value = targetServer.id
            _tunnelStatus.value = TunnelStatus.CONNECTING

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    vpnEngine.connect(targetServer.toWireGuardConfigString())
                    check(vpnEngine.isConnected()) { "WireGuard tunnel did not enter UP state." }
                }
            }

            result.fold(
                onSuccess = {
                    _tunnelStatus.value = TunnelStatus.CONNECTED
                    _bannerMessage.value = "Connected to ${targetServer.name} (${targetServer.countryCode})"
                    startStatsPolling()
                    verifyPublicIp(recordBaseline = false, logContextServer = targetServer)
                },
                onFailure = { err ->
                    _tunnelStatus.value = TunnelStatus.ERROR
                    _errorMessage.value = err.message ?: "WireGuard connection failed."
                }
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _tunnelStatus.value = TunnelStatus.DISCONNECTING
            val active = resolveActiveServer()
            val result = withContext(Dispatchers.IO) {
                runCatching { vpnEngine.disconnect() }
            }
            statsJob?.cancel()
            _trafficStats.value = TunnelTrafficStats()
            result.fold(
                onSuccess = {
                    _tunnelStatus.value = TunnelStatus.DISCONNECTED
                    _bannerMessage.value = "WireGuard tunnel disconnected."
                    if (active != null) {
                        repository.appendLog(
                            userId = currentUserId,
                            serverName = active.name,
                            countryCode = active.countryCode,
                            verifiedIp = _ipInfo.value?.ip.orEmpty(),
                            verifiedCountry = _ipInfo.value?.countryName.orEmpty(),
                            eventType = AuditEventType.DISCONNECTED
                        )
                    }
                    verifyPublicIp(recordBaseline = true)
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to disconnect tunnel."
                }
            )
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive && vpnEngine.isConnected()) {
                _trafficStats.value = withContext(Dispatchers.IO) {
                    vpnEngine.getStatistics()
                }
                delay(1500L)
            }
        }
    }

    fun verifyPublicIp(
        recordBaseline: Boolean = false,
        logContextServer: VpnServerProfile? = null
    ) {
        viewModelScope.launch {
            _isVerifyingIp.value = true
            val result = IpVerificationClient.verifyPublicIp()
            _isVerifyingIp.value = false

            result.fold(
                onSuccess = { info ->
                    _ipInfo.value = info
                    if (recordBaseline && _baselineIp.value == null) {
                        _baselineIp.value = info.ip
                    }
                    if (logContextServer != null) {
                        repository.appendLog(
                            userId = currentUserId,
                            serverName = logContextServer.name,
                            countryCode = logContextServer.countryCode,
                            verifiedIp = info.ip,
                            verifiedCountry = info.countryName,
                            eventType = AuditEventType.CONNECTED
                        )
                    }
                },
                onFailure = { err ->
                    Log.w(TAG, "IP verification failed: ${err.message}")
                }
            )
        }
    }

    fun probeAllServerLatencies() {
        val list = currentServerList()
        if (list.isEmpty()) return
        viewModelScope.launch {
            _isProbingLatencies.value = true
            val latencies = ServerLatencyProber.measureServers(list)
            latencies.forEach { (id, ms) ->
                repository.updateServerLatency(currentUserId, id, ms)
            }
            _isProbingLatencies.value = false
            _bannerMessage.value = "Updated latency checks for ${latencies.size} servers."
        }
    }

    fun syncFromCloudflareWorker(workerUrl: String, bearerToken: String) {
        viewModelScope.launch {
            _isSyncingWorker.value = true
            _errorMessage.value = null
            _bannerMessage.value = null
            _workerBearerToken.value = bearerToken

            val prefs = (preferencesState.value as? UiState.Success)?.data
            val cleanUrlResult = CloudflareWorkerApiClient.normalizeAndValidateBaseUrl(workerUrl)
            if (cleanUrlResult.isFailure) {
                _isSyncingWorker.value = false
                _errorMessage.value = cleanUrlResult.exceptionOrNull()?.message ?: "Invalid Worker URL."
                return@launch
            }
            val cleanUrl = cleanUrlResult.getOrThrow()

            repository.updateWorkerSettings(
                userId = currentUserId,
                workerBaseUrl = cleanUrl,
                autoSelectEnabled = prefs?.autoSelectEnabled ?: true,
                preferredCountryCode = prefs?.preferredCountryCode ?: "AUTO"
            )

            val fetchResult = CloudflareWorkerApiClient.fetchServers(
                baseUrl = cleanUrl,
                bearerToken = bearerToken,
                userId = currentUserId
            )

            _isSyncingWorker.value = false
            fetchResult.fold(
                onSuccess = { servers ->
                    if (servers.isEmpty()) {
                        _bannerMessage.value = "Connected to Worker API, but 0 servers were returned in catalog."
                    } else {
                        servers.forEach { server ->
                            repository.upsertServer(currentUserId, server)
                        }
                        _bannerMessage.value = "Synced ${servers.size} WireGuard server(s) from Cloudflare Worker."
                    }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Worker sync failed."
                }
            )
        }
    }

    fun saveWorkerSettings(
        workerBaseUrl: String,
        autoSelectEnabled: Boolean,
        preferredCountryCode: String
    ) {
        viewModelScope.launch {
            val trimmedUrl = workerBaseUrl.trim()
            if (trimmedUrl.isNotBlank()) {
                val valid = CloudflareWorkerApiClient.normalizeAndValidateBaseUrl(trimmedUrl)
                if (valid.isFailure) {
                    _errorMessage.value = valid.exceptionOrNull()?.message ?: "Worker URL must start with https://"
                    return@launch
                }
            }
            val res = repository.updateWorkerSettings(
                userId = currentUserId,
                workerBaseUrl = trimmedUrl,
                autoSelectEnabled = autoSelectEnabled,
                preferredCountryCode = preferredCountryCode
            )
            res.fold(
                onSuccess = { _bannerMessage.value = "Settings saved." },
                onFailure = { _errorMessage.value = it.message ?: "Failed to save settings." }
            )
        }
    }

    fun importWireGuardConfig(
        rawConf: String,
        customName: String,
        countryCode: String,
        city: String,
        source: ServerSource,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val parsedResult = WireGuardConfigParser.parse(rawConf)
        if (parsedResult.isFailure) {
            _errorMessage.value = parsedResult.exceptionOrNull()?.message ?: "Invalid WireGuard configuration."
            onComplete(false)
            return
        }
        val profile = WireGuardConfigParser.toServerProfile(
            parsed = parsedResult.getOrThrow(),
            userId = currentUserId,
            name = customName,
            countryCode = countryCode,
            city = city,
            source = source
        )
        viewModelScope.launch {
            val saveRes = repository.upsertServer(currentUserId, profile)
            saveRes.fold(
                onSuccess = { savedId ->
                    _selectedServerId.value = savedId
                    _bannerMessage.value = "Imported ${profile.name} (${profile.countryCode})."
                    onComplete(true)
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to save imported config."
                    onComplete(false)
                }
            )
        }
    }

    fun rotateServerClientKey(server: VpnServerProfile) {
        viewModelScope.launch {
            _errorMessage.value = null
            val newKeys = VpnEngine.generateClientKeyPair()
            val prefs = (preferencesState.value as? UiState.Success)?.data
            val workerUrl = prefs?.workerBaseUrl.orEmpty()
            var updatedAddress: String? = null

            if (workerUrl.isNotBlank() && _workerBearerToken.value.isNotBlank()) {
                val workerRotate = CloudflareWorkerApiClient.rotatePeerKey(
                    baseUrl = workerUrl,
                    bearerToken = _workerBearerToken.value,
                    serverId = server.id,
                    oldPublicKey = null,
                    newPublicKey = newKeys.publicKey
                )
                updatedAddress = workerRotate.getOrNull()
            }

            val res = repository.rotateClientKey(
                userId = currentUserId,
                serverId = server.id,
                newPrivateKey = newKeys.privateKey,
                newClientAddress = updatedAddress
            )
            res.fold(
                onSuccess = {
                    repository.appendLog(
                        userId = currentUserId,
                        serverName = server.name,
                        countryCode = server.countryCode,
                        verifiedIp = "",
                        verifiedCountry = "",
                        eventType = AuditEventType.KEY_ROTATED
                    )
                    _bannerMessage.value = "Rotated client WireGuard keypair for ${server.name}. New public key: ${newKeys.publicKey.take(12)}…"
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Failed to rotate keypair."
                }
            )
        }
    }

    fun toggleFavorite(server: VpnServerProfile) {
        viewModelScope.launch {
            repository.toggleFavorite(currentUserId, server.id, !server.favorite)
        }
    }

    fun deleteServer(server: VpnServerProfile) {
        viewModelScope.launch {
            if (_selectedServerId.value == server.id && vpnEngine.isConnected()) {
                disconnect()
            }
            repository.deleteServer(currentUserId, server.id)
        }
    }

    fun cleanupOnSignOut() {
        statsJob?.cancel()
        if (vpnEngine.isConnected()) {
            try {
                vpnEngine.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        statsJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "VpnViewModel"
    }
}
