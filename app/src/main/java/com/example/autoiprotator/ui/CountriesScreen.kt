package com.example.autoiprotator.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.model.UiState
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryCodeToFlag
import com.example.autoiprotator.ui.components.CyberHudCard
import com.example.autoiprotator.ui.components.ScrambleRevealText
import com.example.autoiprotator.ui.components.TypewriterTerminalText
import com.example.autoiprotator.ui.components.rememberHackerSpectrumColor
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.VpnPalette

@Composable
fun CountriesScreen(
    serversState: UiState<List<VpnServerProfile>>,
    selectedServerId: String?,
    selectedCountryFilter: String,
    tunnelStatus: TunnelStatus,
    isProbingLatencies: Boolean,
    onSelectCountryFilter: (String) -> Unit,
    onProbeLatencies: () -> Unit,
    onAutoSelectConnect: () -> Unit,
    onConnectServer: (VpnServerProfile) -> Unit,
    onToggleFavorite: (VpnServerProfile) -> Unit,
    onRotateKey: (VpnServerProfile) -> Unit,
    onDeleteServer: (VpnServerProfile) -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenWorkerSettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val spectrumPrimary = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0.1f)
    val spectrumSecondary = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0.45f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            when (serversState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = spectrumPrimary)
                            TypewriterTerminalText(
                                text = "SYNCING_COUNTRY_NODES...",
                                color = spectrumPrimary
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ERR] ${serversState.message}",
                            color = VpnPalette.CoralError,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }

                is UiState.Success -> {
                    val allServers = serversState.data
                    val countryBuckets = remember(allServers) {
                        allServers
                            .filterNot { it.countryCode.equals("AUTO", ignoreCase = true) }
                            .groupBy { it.countryCode }
                            .entries
                            .sortedBy { it.value.firstOrNull()?.countryName ?: it.key }
                    }

                    val filteredServers = remember(allServers, selectedCountryFilter, searchQuery) {
                        allServers.filter { server ->
                            val matchesCountry = selectedCountryFilter == "ALL" ||
                                server.countryCode.equals(selectedCountryFilter, ignoreCase = true)
                            val q = searchQuery.trim()
                            val matchesQuery = q.isEmpty() ||
                                server.name.contains(q, ignoreCase = true) ||
                                server.countryName.contains(q, ignoreCase = true) ||
                                server.countryCode.contains(q, ignoreCase = true) ||
                                server.city.contains(q, ignoreCase = true)
                            matchesCountry && matchesQuery
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Terminal Search + Latency Probe Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("country_search_input"),
                                    placeholder = {
                                        Text(
                                            "> grep --country / city / node",
                                            fontFamily = JetBrainsMonoFontFamily
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null, tint = spectrumPrimary)
                                    },
                                    singleLine = true,
                                    shape = CutCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = spectrumPrimary,
                                        unfocusedBorderColor = spectrumSecondary.copy(alpha = 0.45f),
                                        focusedContainerColor = VpnPalette.SlateCard.copy(alpha = 0.85f),
                                        unfocusedContainerColor = VpnPalette.SlateCard.copy(alpha = 0.75f)
                                    )
                                )
                                Surface(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .testTag("probe_latencies_button")
                                        .clickable(enabled = !isProbingLatencies && allServers.isNotEmpty()) {
                                            onProbeLatencies()
                                        },
                                    shape = CutCornerShape(10.dp),
                                    color = VpnPalette.SlateCard.copy(alpha = 0.9f),
                                    border = BorderStroke(1.2.dp, spectrumPrimary.copy(alpha = 0.75f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isProbingLatencies) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp,
                                                color = spectrumPrimary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "Measure Server Latencies",
                                                tint = spectrumPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Auto Server Selection Cyber HUD Card
                        item {
                            CyberHudCard(
                                modifier = Modifier
                                    .testTag("auto_server_selection_card")
                                    .clickable(enabled = allServers.isNotEmpty()) {
                                        onAutoSelectConnect()
                                    },
                                borderColor = spectrumPrimary,
                                secondaryBorderColor = spectrumSecondary,
                                containerColor = VpnPalette.SlateElevated.copy(alpha = 0.9f),
                                contentPadding = 14.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(44.dp),
                                        shape = CutCornerShape(10.dp),
                                        color = spectrumPrimary.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, spectrumPrimary.copy(alpha = 0.65f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = spectrumPrimary
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        ScrambleRevealText(
                                            text = "[ AUTO_SERVER_SELECTION ]",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = spectrumPrimary
                                        )
                                        Text(
                                            text = if (allServers.isEmpty()) {
                                                "Import QR or sync Cloudflare Worker to enable auto-selection"
                                            } else {
                                                "Probes ${allServers.size} node(s) & locks onto lowest ping"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = VpnPalette.TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Color-Shifting Country Filter Chips
                        if (countryBuckets.isNotEmpty()) {
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedCountryFilter == "ALL",
                                            onClick = { onSelectCountryFilter("ALL") },
                                            label = {
                                                Text(
                                                    "ALL (${allServers.size})",
                                                    fontFamily = JetBrainsMonoFontFamily
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Public,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = spectrumPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = spectrumPrimary,
                                                selectedLeadingIconColor = spectrumPrimary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = selectedCountryFilter == "ALL",
                                                selectedBorderColor = spectrumPrimary,
                                                borderColor = spectrumSecondary.copy(alpha = 0.4f)
                                            )
                                        )
                                    }
                                    items(countryBuckets, key = { it.key }) { (code, list) ->
                                        val countryName = list.firstOrNull()?.countryName ?: code
                                        val selected = selectedCountryFilter.equals(code, ignoreCase = true)
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectCountryFilter(code) },
                                            label = {
                                                Text(
                                                    "${countryCodeToFlag(code)} $countryName (${list.size})",
                                                    fontFamily = JetBrainsMonoFontFamily
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = spectrumPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = spectrumPrimary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = selected,
                                                selectedBorderColor = spectrumPrimary,
                                                borderColor = spectrumSecondary.copy(alpha = 0.4f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Empty State or Filtered Server Cards
                        if (allServers.isEmpty()) {
                            item {
                                EmptyServersCard(
                                    primaryGlow = spectrumPrimary,
                                    secondaryGlow = spectrumSecondary,
                                    onOpenQrScanner = onOpenQrScanner,
                                    onOpenWorkerSettings = onOpenWorkerSettings
                                )
                            }
                        } else if (filteredServers.isEmpty()) {
                            item {
                                TypewriterTerminalText(
                                    text = "0_NODES_MATCHED // QUERY: \"$searchQuery\"",
                                    color = spectrumSecondary,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        } else {
                            items(filteredServers, key = { it.id }) { server ->
                                val isActive = selectedServerId == server.id &&
                                    tunnelStatus == TunnelStatus.CONNECTED
                                ServerCountryCard(
                                    server = server,
                                    isSelected = selectedServerId == server.id,
                                    isActive = isActive,
                                    primaryGlow = spectrumPrimary,
                                    secondaryGlow = spectrumSecondary,
                                    onConnect = { onConnectServer(server) },
                                    onToggleFavorite = { onToggleFavorite(server) },
                                    onRotateKey = { onRotateKey(server) },
                                    onDelete = { onDeleteServer(server) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCountryCard(
    server: VpnServerProfile,
    isSelected: Boolean,
    isActive: Boolean,
    primaryGlow: Color,
    secondaryGlow: Color,
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRotateKey: () -> Unit,
    onDelete: () -> Unit
) {
    val latencyColor = when {
        server.latencyMs < 0L -> VpnPalette.TextSecondary
        server.latencyMs < 90L -> VpnPalette.EmeraldVerified
        server.latencyMs < 200L -> VpnPalette.AmberWarn
        else -> VpnPalette.CoralError
    }
    val cardBorder by animateColorAsState(
        targetValue = when {
            isActive -> primaryGlow
            isSelected -> secondaryGlow
            else -> secondaryGlow.copy(alpha = 0.42f)
        },
        animationSpec = tween(350),
        label = "serverCardBorder"
    )

    CyberHudCard(
        modifier = Modifier
            .testTag("server_card_${server.id}")
            .clickable { onConnect() },
        borderColor = cardBorder,
        secondaryBorderColor = primaryGlow.copy(alpha = 0.35f),
        containerColor = if (isActive) {
            VpnPalette.EmeraldDim.copy(alpha = 0.55f)
        } else {
            VpnPalette.SlateCard.copy(alpha = 0.90f)
        },
        cornerCut = 14.dp,
        contentPadding = 14.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = countryCodeToFlag(server.countryCode),
                fontSize = 30.sp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActive) primaryGlow else VpnPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = CutCornerShape(6.dp),
                        color = VpnPalette.SlateElevated,
                        border = BorderStroke(1.dp, secondaryGlow.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (server.isOpenSourceTor()) "OPEN_TOR" else server.source.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (server.isOpenSourceTor()) primaryGlow else secondaryGlow,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = listOfNotNull(
                        server.countryName,
                        server.city.takeIf { it.isNotBlank() }
                    ).joinToString(" // "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VpnPalette.TextSecondary
                )
                Text(
                    text = "> ${server.endpoint}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = primaryGlow.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (server.latencyMs >= 0L) "${server.latencyMs} ms" else "— ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = latencyColor
                )
                if (server.loadPercent > 0L) {
                    Text(
                        text = "LOAD ${server.loadPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = VpnPalette.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (server.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Pin Favorite Server",
                        tint = if (server.favorite) VpnPalette.AmberWarn else VpnPalette.TextSecondary
                    )
                }
                if (!server.isOpenSourceTor()) {
                    IconButton(
                        onClick = onRotateKey,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rotate_key_${server.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VpnKey,
                            contentDescription = "Regenerate WireGuard Client Key",
                            tint = secondaryGlow
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete Server",
                            tint = VpnPalette.CoralError
                        )
                    }
                }
            }

            Button(
                onClick = onConnect,
                modifier = Modifier.minimumInteractiveComponentSize(),
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) VpnPalette.EmeraldVerified else primaryGlow,
                    contentColor = VpnPalette.ObsidianBg
                )
            ) {
                Text(
                    text = if (isActive) "[ ACTIVE ]" else "[ CONNECT ]",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyServersCard(
    primaryGlow: Color,
    secondaryGlow: Color,
    onOpenQrScanner: () -> Unit,
    onOpenWorkerSettings: () -> Unit
) {
    CyberHudCard(
        borderColor = primaryGlow,
        secondaryBorderColor = secondaryGlow,
        contentPadding = 22.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = primaryGlow,
                modifier = Modifier.size(44.dp)
            )
            ScrambleRevealText(
                text = ":: NO_VPN_NODES_CONFIGURED",
                style = MaterialTheme.typography.titleLarge,
                color = primaryGlow
            )
            Text(
                text = "Ultra Multi VPN connects strictly to your own WireGuard servers. Scan a WireGuard QR config or sync via Cloudflare Worker API.",
                style = MaterialTheme.typography.bodyMedium,
                color = VpnPalette.TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenQrScanner,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize(),
                    shape = CutCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGlow,
                        contentColor = VpnPalette.ObsidianBg
                    )
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SCAN_QR", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenWorkerSettings,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize(),
                    shape = CutCornerShape(10.dp),
                    border = BorderStroke(1.dp, secondaryGlow)
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = secondaryGlow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WORKER_API", color = secondaryGlow)
                }
            }
        }
    }
}
