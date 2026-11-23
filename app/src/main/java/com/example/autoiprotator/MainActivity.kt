package com.example.autoiprotator

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.autoiprotator.auth.authStateFlow
import com.example.autoiprotator.auth.signOut
import com.example.autoiprotator.data.VpnRepository
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.model.UiState
import com.example.autoiprotator.network.ServerLatencyProber
import com.example.autoiprotator.ui.AuthScreen
import com.example.autoiprotator.ui.CountriesScreen
import com.example.autoiprotator.ui.DashboardScreen
import com.example.autoiprotator.ui.QrScannerScreen
import com.example.autoiprotator.ui.SettingsScreen
import com.example.autoiprotator.ui.components.HackerBackdrop
import com.example.autoiprotator.ui.components.QuickSetupSheet
import com.example.autoiprotator.ui.components.ScrambleRevealText
import com.example.autoiprotator.ui.components.TypewriterTerminalText
import com.example.autoiprotator.ui.components.rememberHackerSpectrumColor
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.UltraVpnTheme
import com.example.autoiprotator.ui.theme.VpnPalette
import com.example.autoiprotator.viewmodel.VpnViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CONNECT("TUNNEL", Icons.Filled.Shield, Icons.Outlined.Shield, "nav_tunnel"),
    COUNTRIES("NODES", Icons.Filled.Public, Icons.Outlined.Public, "nav_countries"),
    QR_SCAN("SCAN_QR", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner, "nav_qr"),
    SETTINGS("SYS_CFG", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltraVpnTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedVpnApp(
                currentUserId = user.uid,
                displayName = user.displayName ?: "VPN Operator",
                email = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedVpnApp(
    currentUserId: String,
    displayName: String,
    email: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val vpnEngine = remember(context) { VpnEngine(context.applicationContext) }

    val viewModel: VpnViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VpnViewModel(
                    repository = VpnRepository(db),
                    vpnEngine = vpnEngine,
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    )

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    val requestVpnPermission = {
        val intent = vpnEngine.getVpnPermissionIntent()
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.onVpnPermissionResult(true)
        }
    }

    val preferencesState by viewModel.preferencesState.collectAsStateWithLifecycle()
    val serversState by viewModel.serversState.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val tunnelStatus by viewModel.tunnelStatus.collectAsStateWithLifecycle()
    val selectedServerId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val selectedCountryFilter by viewModel.selectedCountryFilter.collectAsStateWithLifecycle()
    val ipInfo by viewModel.ipInfo.collectAsStateWithLifecycle()
    val baselineIp by viewModel.baselineIp.collectAsStateWithLifecycle()
    val isVerifyingIp by viewModel.isVerifyingIp.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val sessionSeconds by viewModel.sessionSeconds.collectAsStateWithLifecycle()
    val isSyncingWorker by viewModel.isSyncingWorker.collectAsStateWithLifecycle()
    val isProbingLatencies by viewModel.isProbingLatencies.collectAsStateWithLifecycle()
    val showQuickSetupSheet by viewModel.showQuickSetupSheet.collectAsStateWithLifecycle()
    val workerBearerToken by viewModel.workerBearerToken.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.CONNECT) }

    val serverList = (serversState as? UiState.Success)?.data.orEmpty()
    val prefs = (preferencesState as? UiState.Success)?.data
    val activeServer = remember(serverList, selectedServerId, prefs) {
        selectedServerId?.let { id -> serverList.firstOrNull { it.id == id } }
            ?: ServerLatencyProber.selectBestServer(serverList, prefs?.preferredCountryCode ?: "AUTO")
    }

    val spectrumPrimary = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0f)
    val spectrumSecondary = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0.4f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        HackerBackdrop(
            accentColor = spectrumPrimary,
            secondaryColor = spectrumSecondary,
            isConnected = tunnelStatus == TunnelStatus.CONNECTED
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            ScrambleRevealText(
                                text = "[>_ ULTRA MULTI VPN]",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = VpnPalette.TextPrimary
                            )
                            TypewriterTerminalText(
                                text = when (tunnelStatus) {
                                    TunnelStatus.CONNECTED -> "WG_KERNEL::ENCRYPTED_ONLINE"
                                    TunnelStatus.CONNECTING,
                                    TunnelStatus.AUTO_SELECTING -> "WG_KERNEL::HANDSHAKE_ACTIVE"
                                    TunnelStatus.ERROR -> "WG_KERNEL::FAULT_DETECTED"
                                    else -> "WG_KERNEL::STANDBY"
                                },
                                prefix = "",
                                color = spectrumPrimary
                            )
                        }
                    },
                    actions = {
                        Surface(
                            modifier = Modifier.padding(end = 12.dp),
                            shape = CutCornerShape(6.dp),
                            color = spectrumPrimary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, spectrumPrimary.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = if (tunnelStatus == TunnelStatus.CONNECTED) "LIVE" else "READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = spectrumPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = VpnPalette.ObsidianBg.copy(alpha = 0.88f)
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = VpnPalette.SlateSurface.copy(alpha = 0.94f)
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = spectrumPrimary,
                                    selectedTextColor = spectrumPrimary,
                                    indicatorColor = spectrumPrimary.copy(alpha = 0.18f),
                                    unselectedIconColor = VpnPalette.TextSecondary,
                                    unselectedTextColor = VpnPalette.TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = VpnPalette.SlateSurface.copy(alpha = 0.94f)
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = spectrumPrimary,
                                    selectedTextColor = spectrumPrimary,
                                    indicatorColor = spectrumPrimary.copy(alpha = 0.18f),
                                    unselectedIconColor = VpnPalette.TextSecondary,
                                    unselectedTextColor = VpnPalette.TextSecondary
                                )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    val activeMsg = errorMessage ?: bannerMessage
                    if (!activeMsg.isNullOrBlank()) {
                        val isErr = errorMessage != null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = CutCornerShape(8.dp),
                            color = if (isErr) VpnPalette.CoralDim else VpnPalette.EmeraldDim,
                            border = BorderStroke(
                                1.dp,
                                if (isErr) VpnPalette.CoralError else spectrumPrimary
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "> $activeMsg",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = VpnPalette.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.dismissMessages() }) {
                                    Text("[OK]", color = spectrumPrimary)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (currentTab) {
                            AppTab.CONNECT -> DashboardScreen(
                                tunnelStatus = tunnelStatus,
                                activeServer = activeServer,
                                servers = serverList,
                                autoSelectEnabled = prefs?.autoSelectEnabled ?: true,
                                sessionSeconds = sessionSeconds,
                                ipInfo = ipInfo,
                                baselineIp = baselineIp,
                                isVerifyingIp = isVerifyingIp,
                                trafficStats = trafficStats,
                                onConnectClick = { viewModel.connectWithAutoOrSelected(requestVpnPermission) },
                                onAutoConnectClick = { viewModel.autoSelectAndConnect(requestVpnPermission) },
                                onQuickCountryConnect = { code ->
                                    viewModel.oneClickConnectCountry(code, requestVpnPermission)
                                },
                                onRotateNodeClick = { viewModel.rotateToNextNode(requestVpnPermission) },
                                onDisconnectClick = { viewModel.disconnect() },
                                onVerifyIpClick = { viewModel.verifyPublicIp() },
                                onOpenCountries = { currentTab = AppTab.COUNTRIES },
                                onOpenQrScanner = { currentTab = AppTab.QR_SCAN },
                                onOpenQuickSetup = { viewModel.openQuickSetupSheet() }
                            )

                            AppTab.COUNTRIES -> CountriesScreen(
                                serversState = serversState,
                                selectedServerId = selectedServerId,
                                selectedCountryFilter = selectedCountryFilter,
                                tunnelStatus = tunnelStatus,
                                isProbingLatencies = isProbingLatencies,
                                onSelectCountryFilter = { viewModel.selectCountryFilter(it) },
                                onProbeLatencies = { viewModel.probeAllServerLatencies() },
                                onAutoSelectConnect = {
                                    viewModel.autoSelectAndConnect(requestVpnPermission)
                                    currentTab = AppTab.CONNECT
                                },
                                onConnectServer = { server ->
                                    viewModel.connectSpecificServer(server, requestVpnPermission)
                                    currentTab = AppTab.CONNECT
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onRotateKey = { viewModel.rotateServerClientKey(it) },
                                onDeleteServer = { viewModel.deleteServer(it) },
                                onOpenQrScanner = { currentTab = AppTab.QR_SCAN },
                                onOpenWorkerSettings = { currentTab = AppTab.SETTINGS }
                            )

                            AppTab.QR_SCAN -> QrScannerScreen(
                                onImportConfig = { raw, name, code, city, source ->
                                    viewModel.importWireGuardConfig(
                                        rawConf = raw,
                                        customName = name,
                                        countryCode = code,
                                        city = city,
                                        source = source
                                    ) { ok ->
                                        if (ok) currentTab = AppTab.COUNTRIES
                                    }
                                }
                            )

                            AppTab.SETTINGS -> SettingsScreen(
                                userDisplayName = displayName,
                                userEmail = email,
                                preferencesState = preferencesState,
                                logsState = logsState,
                                workerBearerToken = workerBearerToken,
                                isSyncingWorker = isSyncingWorker,
                                onBearerTokenChange = { viewModel.updateWorkerBearerTokenInMemory(it) },
                                onSavePreferences = { url, auto, country ->
                                    viewModel.saveWorkerSettings(url, auto, country)
                                },
                                onSyncWorker = { url, token ->
                                    viewModel.syncFromCloudflareWorker(url, token)
                                },
                                onSignOutClick = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        beforeFirebaseSignOut = {
                                            viewModel.cleanupOnSignOut()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showQuickSetupSheet) {
            QuickSetupSheet(
                initialWorkerUrl = prefs?.workerBaseUrl.orEmpty(),
                initialToken = workerBearerToken,
                accentColor = spectrumPrimary,
                secondaryColor = spectrumSecondary,
                onDismiss = { viewModel.dismissQuickSetupSheet() },
                onSyncAndAutoConnect = { url, token ->
                    viewModel.quickSyncAndConnect(url, token, requestVpnPermission)
                },
                onOpenQrOrConf = {
                    currentTab = AppTab.QR_SCAN
                }
            )
        }
    }
}
