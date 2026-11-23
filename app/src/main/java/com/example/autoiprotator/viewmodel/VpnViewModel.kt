package com.example.autoiprotator.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoiprotator.BuildConfig
import com.example.autoiprotator.TunnelTrafficStats
import com.example.autoiprotator.VpnEngine
import com.example.autoiprotator.data.VpnDataSource
import com.example.autoiprotator.model.AuditEventType
import com.example.autoiprotator.model.ConnectionAuditLog
import com.example.autoiprotator.model.IpVerificationInfo
import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.TunnelProtocol
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.model.UiState
import com.example.autoiprotator.model.UserPreferences
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.network.CloudflareWorkerApiClient
import com.example.autoiprotator.network.IpVerificationClient
import com.example.autoiprotator.network.ServerLatencyProber
import com.example.autoiprotator.vpn.OpenSourceCatalog
import com.example.autoiprotator.vpn.OpenSourceNodeStore
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PendingConnectMode {
    AUTO_OR_SELECTED,
    SPECIFIC_SERVER,
    FORCE_AUTO,
    COUNTRY_QUICK_PICK,
    ROTATE_NEXT
}

class VpnViewModel(
    private val repository: VpnDataSource,
    private val vpnEngine: VpnEngine,
    private val openSourceNodeStore: OpenSourceNodeStore,
    private val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String?
) : ViewModel() {

    private val _openNodesVersion = MutableStateFlow(0)

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
        combine(
            repository.observeServers(currentUserId),
            _openNodesVersion
        ) { customServers, _ ->
            val builtInOpenNodes = OpenSourceCatalog.buildProfiles(currentUserId, openSourceNodeStore)
            val combined = (customServers + builtInOpenNodes).sortedWith(
                compareByDescending<VpnServerProfile> { it.favorite }
                    .thenBy { if (!it.isOpenSourceTor() && it.latencyMs >= 0L) 0 else 1 }
                    .thenBy { if (it.countryCode.equals("AUTO", ignoreCase = true)) 0 else 1 }
                    .thenBy { if (it.latencyMs < 0L) Long.MAX_VALUE else it.latencyMs }
                    .thenBy { it.countryName }
            )
            UiState.Success(combined) as UiState<List<VpnServerProfile>>
        }
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
                initialValue = UiState.Success(
                    OpenSourceCatalog.buildProfiles(currentUserId, openSourceNodeStore)
                )
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

    private val _activeProtocol = MutableStateFlow(vpnEngine.activeProtocol())
    val activeProtocol = _activeProtocol.asStateFlow()

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

    private val _sessionSeconds = MutableStateFlow(0L)
    val sessionSeconds = _sessionSeconds.asStateFlow()

    private val _isSyncingWorker = MutableStateFlow(false)
    val isSyncingWorker = _isSyncingWorker.asStateFlow()

    private val _isProbingLatencies = MutableStateFlow(false)
    val isProbingLatencies = _isProbingLatencies.asStateFlow()

    private val _showQuickSetupSheet = MutableStateFlow(false)
    val showQuickSetupSheet = _showQuickSetupSheet.asStateFlow()

    // Held strictly in memory (never written to Firestore or logs)
    private val _workerBearerToken = MutableStateFlow(BuildConfig.DEFAULT_WORKER_TOKEN.trim())
    val workerBearerToken = _workerBearerToken.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage = _bannerMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var statsJob: Job? = null
    private var pendingMode: PendingConnectMode = PendingConnectMode.AUTO_OR_SELECTED
    private var pendingCountryCode: String = "AUTO"

    init {
        viewModelScope.launch {
            repository.ensureUserPreferences(currentUserId, initialDisplayName, initialEmail)
            bootstrapDefaultFleetIfNeeded()
            probeAllServerLatencies(showToast = false)
        }
        verifyPublicIp(recordBaseline = !vpnEngine.isConnected())
        if (vpnEngine.isConnected()) {
            startStatsPolling()
        }
    }

    private suspend fun bootstrapDefaultFleetIfNeeded() {
        val existingCustomServers = runCatching {
            repository.observeServers(currentUserId).firstOrNull().orEmpty()
        }.getOrDefault(emptyList())
        if (existingCustomServers.isNotEmpty()) return

        val prefs = runCatching {
            repository.observeUserPreferences(currentUserId).firstOrNull()
        }.getOrNull()
        val effectiveUrl = prefs?.workerBaseUrl?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_WORKER_URL.trim()
        if (effectiveUrl.isBlank()) return

        val effectiveToken = _workerBearerToken.value.ifBlank {
            BuildConfig.DEFAULT_WORKER_TOKEN.trim()
        }
        _isSyncingWorker.value = true
        val synced = CloudflareWorkerApiClient.fetchServers(
            baseUrl = effectiveUrl,
            bearerToken = effectiveToken,
            userId = currentUserId
        ).getOrNull().orEmpty()
        if (synced.isNotEmpty()) {
            val latencies = ServerLatencyProber.measureServers(synced)
            synced.forEach { server ->
                val measured = latencies[server.id] ?: server.latencyMs
                repository.upsertServer(currentUserId, server.copy(latencyMs = measured))
            }
            _bannerMessage.value = "Loaded ${synced.size} Worker node(s) alongside Open-Source Tor nodes."
        }
        _isSyncingWorker.value = false
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

    fun dismissQuickSetupSheet() {
        _showQuickSetupSheet.value = false
    }

    fun openQuickSetupSheet() {
        _showQuickSetupSheet.value = true
    }

    fun currentServerList(): List<VpnServerProfile> =
        (serversState.value as? UiState.Success)?.data
            ?: OpenSourceCatalog.buildProfiles(currentUserId, openSourceNodeStore)

    fun resolveActiveServer(): VpnServerProfile? {
        val list = currentServerList()
        if (list.isEmpty()) return null
        val selected = _selectedServerId.value?.let { id -> list.firstOrNull { it.id == id } }
        if (selected != null) return selected
        val prefs = (preferencesState.value as? UiState.Success)?.data
        return ServerLatencyProber.selectBestServer(list, prefs?.preferredCountryCode ?: "AUTO")
    }

    fun connectWithAutoOrSelected(requestPermission: () -> Unit) {
        pendingMode = PendingConnectMode.AUTO_OR_SELECTED
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        executePendingConnect()
    }

    fun connectSpecificServer(server: VpnServerProfile, requestPermission: () -> Unit) {
        _selectedServerId.value = server.id
        pendingMode = PendingConnectMode.SPECIFIC_SERVER
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        executePendingConnect()
    }

    fun autoSelectAndConnect(requestPermission: () -> Unit) {
        pendingMode = PendingConnectMode.FORCE_AUTO
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        executePendingConnect()
    }

    fun oneClickConnectCountry(countryCode: String, requestPermission: () -> Unit) {
        if (countryCode.equals("AUTO", ignoreCase = true)) {
            autoSelectAndConnect(requestPermission)
            return
        }
        pendingMode = PendingConnectMode.COUNTRY_QUICK_PICK
        pendingCountryCode = countryCode.uppercase()
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        executePendingConnect()
    }

    fun rotateToNextNode(requestPermission: () -> Unit) {
        pendingMode = PendingConnectMode.ROTATE_NEXT
        if (vpnEngine.getVpnPermissionIntent() != null) {
            requestPermission()
            return
        }
        executePendingConnect()
    }

    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) {
            executePendingConnect()
        } else {
            _errorMessage.value = "Android VPN permission is required to activate the VPN tunnel."
        }
    }

    fun quickSyncAndConnect(
        workerUrl: String,
        bearerToken: String,
        requestPermission: () -> Unit
    ) {
        viewModelScope.launch {
            _showQuickSetupSheet.value = false
            _isSyncingWorker.value = true
            _errorMessage.value = null
            _workerBearerToken.value = bearerToken

            val cleanUrlResult = CloudflareWorkerApiClient.normalizeAndValidateBaseUrl(workerUrl)
            if (cleanUrlResult.isFailure) {
                _isSyncingWorker.value = false
                _errorMessage.value = cleanUrlResult.exceptionOrNull()?.message ?: "Invalid Worker URL."
                return@launch
            }
            val cleanUrl = cleanUrlResult.getOrThrow()
            val prefs = (preferencesState.value as? UiState.Success)?.data
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
                onSuccess = { syncedServers ->
                    if (syncedServers.isEmpty()) {
                        _errorMessage.value = "Worker API responded, but returned 0 WireGuard servers."
                    } else {
                        syncedServers.forEach { repository.upsertServer(currentUserId, it) }
                        pendingMode = PendingConnectMode.FORCE_AUTO
                        if (vpnEngine.getVpnPermissionIntent() != null) {
                            requestPermission()
                        } else {
                            startTunnelWithServers(
                                initialServers = currentServerList(),
                                mode = PendingConnectMode.FORCE_AUTO,
                                countryCode = "AUTO"
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _errorMessage.value = err.message ?: "Worker sync failed."
                }
            )
        }
    }

    private fun executePendingConnect() {
        val mode = pendingMode
        val country = pendingCountryCode
        viewModelScope.launch {
            val servers = currentServerList()
            startTunnelWithServers(
                initialServers = servers,
                mode = mode,
                countryCode = country
            )
        }
    }

    private suspend fun startTunnelWithServers(
        initialServers: List<VpnServerProfile>,
        mode: PendingConnectMode,
        countryCode: String
    ) {
        _errorMessage.value = null
        _bannerMessage.value = null
        val prefs = (preferencesState.value as? UiState.Success)?.data
        val useAuto = when (mode) {
            PendingConnectMode.SPECIFIC_SERVER -> false
            PendingConnectMode.AUTO_OR_SELECTED -> prefs?.autoSelectEnabled ?: true
            else -> true
        }

        val targetServer = if (useAuto) {
            _tunnelStatus.value = TunnelStatus.AUTO_SELECTING
            val latencies = ServerLatencyProber.measureServers(initialServers)
            val updatedList = initialServers.map { s ->
                val probed = latencies[s.id] ?: -1L
                val measured = if (probed >= 0L) probed else s.latencyMs
                if (measured != s.latencyMs) {
                    persistServerLatency(s, measured)
                }
                s.copy(latencyMs = measured)
            }
            when (mode) {
                PendingConnectMode.COUNTRY_QUICK_PICK -> {
                    ServerLatencyProber.selectBestServer(updatedList, countryCode)
                }
                PendingConnectMode.ROTATE_NEXT -> {
                    val currentId = _selectedServerId.value
                    val candidates = updatedList
                        .filter { it.id != currentId && !it.countryCode.equals("AUTO", ignoreCase = true) }
                        .ifEmpty { updatedList.filter { it.id != currentId } }
                        .ifEmpty { updatedList }
                    ServerLatencyProber.selectBestServer(candidates, "AUTO")
                }
                else -> {
                    ServerLatencyProber.selectBestServer(
                        updatedList,
                        prefs?.preferredCountryCode ?: "AUTO"
                    )
                }
            }
        } else {
            resolveActiveServer()
        }

        if (targetServer == null) {
            _tunnelStatus.value = TunnelStatus.DISCONNECTED
            _errorMessage.value = "Unable to select a VPN route."
            return
        }

        _selectedServerId.value = targetServer.id
        _tunnelStatus.value = TunnelStatus.CONNECTING
        _activeProtocol.value = targetServer.protocol()

        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (targetServer.isOpenSourceTor()) {
                    vpnEngine.connectOpenSourceTor(targetServer.countryCode) { note ->
                        _bannerMessage.value = note
                    }
                    check(vpnEngine.isOpenSourceConnected()) {
                        "Open-Source Tor tunnel did not enter active state."
                    }
                } else {
                    vpnEngine.connect(targetServer.toWireGuardConfigString())
                    check(vpnEngine.isWireGuardConnected()) {
                        "WireGuard tunnel did not enter UP state."
                    }
                }
            }
        }

        result.fold(
            onSuccess = {
                _tunnelStatus.value = TunnelStatus.CONNECTED
                _activeProtocol.value = targetServer.protocol()
                val engineLabel = if (targetServer.isOpenSourceTor()) "Open Tor" else "WireGuard"
                _bannerMessage.value = "Connected ($engineLabel): ${targetServer.name} (${targetServer.countryCode})"
                startStatsPolling(resetSessionTimer = true)
                verifyPublicIp(recordBaseline = false, logContextServer = targetServer)
            },
            onFailure = { err ->
                _tunnelStatus.value = TunnelStatus.ERROR
                _errorMessage.value = err.message ?: "VPN connection failed."
            }
        )
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
            _sessionSeconds.value = 0L
            result.fold(
                onSuccess = {
                    _tunnelStatus.value = TunnelStatus.DISCONNECTED
                    _activeProtocol.value = TunnelProtocol.WIREGUARD
                    _bannerMessage.value = "VPN tunnel disconnected."
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

    private fun startStatsPolling(resetSessionTimer: Boolean = false) {
        statsJob?.cancel()
        if (resetSessionTimer) {
            _sessionSeconds.value = 0L
        }
        statsJob = viewModelScope.launch {
            var prevRx = 0L
            var prevTx = 0L
            var firstSample = true
            while (isActive && vpnEngine.isConnected()) {
                val raw = withContext(Dispatchers.IO) {
                    vpnEngine.getStatistics()
                }
                val rxRate = if (firstSample) 0L else (raw.rxBytes - prevRx).coerceAtLeast(0L)
                val txRate = if (firstSample) 0L else (raw.txBytes - prevTx).coerceAtLeast(0L)
                prevRx = raw.rxBytes
                prevTx = raw.txBytes
                firstSample = false

                _trafficStats.value = raw.copy(
                    rxBytesPerSec = rxRate,
                    txBytesPerSec = txRate
                )
                _sessionSeconds.value = _sessionSeconds.value + 1L
                delay(1000L)
            }
        }
    }

    fun verifyPublicIp(
        recordBaseline: Boolean = false,
        logContextServer: VpnServerProfile? = null
    ) {
        viewModelScope.launch {
            _isVerifyingIp.value = true
            val socksPort = vpnEngine.activeTorSocksPort()
            val result = IpVerificationClient.verifyPublicIp(socksPort = socksPort)
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

    fun probeAllServerLatencies(showToast: Boolean = true) {
        val list = currentServerList()
        if (list.isEmpty()) return
        viewModelScope.launch {
            _isProbingLatencies.value = true
            val latencies = ServerLatencyProber.measureServers(list)
            list.forEach { server ->
                val ms = latencies[server.id] ?: -1L
                persistServerLatency(server, ms)
            }
            _isProbingLatencies.value = false
            if (showToast) {
                _bannerMessage.value = "Updated latency checks for ${latencies.size} servers."
            }
        }
    }

    private suspend fun persistServerLatency(server: VpnServerProfile, latencyMs: Long) {
        if (server.isOpenSourceTor()) {
            openSourceNodeStore.updateLatency(server.id, latencyMs)
            _openNodesVersion.value += 1
        } else {
            repository.updateServerLatency(currentUserId, server.id, latencyMs)
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
        if (server.isOpenSourceTor()) {
            _bannerMessage.value = "Open-Source Tor circuits rotate keys automatically—tap ROTATE_IP when connected for a new Exit IP."
            return
        }
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
        if (server.isOpenSourceTor()) {
            openSourceNodeStore.setFavorite(server.id, !server.favorite)
            _openNodesVersion.value += 1
            return
        }
        viewModelScope.launch {
            repository.toggleFavorite(currentUserId, server.id, !server.favorite)
        }
    }

    fun deleteServer(server: VpnServerProfile) {
        if (server.isOpenSourceTor()) {
            _bannerMessage.value = "Built-in Open-Source Tor country nodes are always available."
            return
        }
        viewModelScope.launch {
            if (_selectedServerId.value == server.id && vpnEngine.isConnected()) {
                disconnect()
            }
            repository.deleteServer(currentUserId, server.id)
        }
    }

    fun cleanupOnSignOut() {
        statsJob?.cancel()
        _sessionSeconds.value = 0L
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
