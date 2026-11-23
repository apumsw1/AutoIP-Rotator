package com.example.autoiprotator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autoiprotator.TunnelTrafficStats
import com.example.autoiprotator.model.IpVerificationInfo
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.model.VpnServerProfile
import com.example.autoiprotator.model.countryCodeToFlag
import com.example.autoiprotator.ui.components.CyberHudCard
import com.example.autoiprotator.ui.components.EncryptedPacketWaveform
import com.example.autoiprotator.ui.components.ScrambleRevealText
import com.example.autoiprotator.ui.components.TypewriterTerminalText
import com.example.autoiprotator.ui.components.rememberHackerSpectrumColor
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.VpnPalette

@Composable
fun DashboardScreen(
    tunnelStatus: TunnelStatus,
    activeServer: VpnServerProfile?,
    servers: List<VpnServerProfile>,
    autoSelectEnabled: Boolean,
    sessionSeconds: Long,
    ipInfo: IpVerificationInfo?,
    baselineIp: String?,
    isVerifyingIp: Boolean,
    trafficStats: TunnelTrafficStats,
    onConnectClick: () -> Unit,
    onAutoConnectClick: () -> Unit,
    onQuickCountryConnect: (String) -> Unit,
    onRotateNodeClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onVerifyIpClick: () -> Unit,
    onOpenCountries: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenQuickSetup: () -> Unit
) {
    val totalServers = servers.size
    val isConnected = tunnelStatus == TunnelStatus.CONNECTED
    val isBusy = tunnelStatus == TunnelStatus.CONNECTING ||
        tunnelStatus == TunnelStatus.AUTO_SELECTING ||
        tunnelStatus == TunnelStatus.DISCONNECTING

    val spectrumPrimary = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0f)
    val spectrumSecondary = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0.35f)
    val spectrumAlt = rememberHackerSpectrumColor(tunnelStatus, phaseOffset = 0.65f)

    val statusColor by animateColorAsState(
        targetValue = when (tunnelStatus) {
            TunnelStatus.CONNECTED -> spectrumPrimary
            TunnelStatus.CONNECTING, TunnelStatus.AUTO_SELECTING, TunnelStatus.DISCONNECTING -> VpnPalette.AmberWarn
            TunnelStatus.ERROR -> VpnPalette.CoralError
            TunnelStatus.DISCONNECTED -> spectrumPrimary
        },
        animationSpec = tween(400),
        label = "statusColor"
    )

    val countryBuckets = remember(servers) {
        servers
            .filterNot { it.countryCode.equals("AUTO", ignoreCase = true) }
            .groupBy { it.countryCode.uppercase() }
            .entries
            .map { (code, list) ->
                val minPing = list.mapNotNull { it.latencyMs.takeIf { ms -> ms >= 0L } }.minOrNull()
                Triple(code, list.firstOrNull()?.countryName ?: code, minPing)
            }
            .sortedBy { it.third ?: Long.MAX_VALUE }
    }

    // Post-connection shockwave ring + Thunder lock-in spring animation
    val lockBurstProgress = remember { Animatable(0f) }
    val lockCoreScale = remember { Animatable(1f) }
    LaunchedEffect(tunnelStatus) {
        if (tunnelStatus == TunnelStatus.CONNECTED) {
            lockBurstProgress.snapTo(0f)
            lockCoreScale.snapTo(0.84f)
            lockCoreScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            lockBurstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )
        } else {
            lockBurstProgress.snapTo(0f)
            lockCoreScale.snapTo(1f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orbOrbit")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBusy) 1800 else 7200, easing = LinearEasing)
        ),
        label = "outerAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected || isBusy) 1.055f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thunder-Style 1-Tap Country & Auto-Connect Strip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = isConnected && autoSelectEnabled,
                        onClick = {
                            if (totalServers > 0) onAutoConnectClick() else onOpenQuickSetup()
                        },
                        enabled = !isBusy,
                        label = {
                            Text(
                                text = "AUTO_FASTEST",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = spectrumPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = CutCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = VpnPalette.SlateCard.copy(alpha = 0.85f),
                            labelColor = spectrumPrimary,
                            selectedContainerColor = spectrumPrimary.copy(alpha = 0.22f),
                            selectedLabelColor = spectrumPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isConnected && autoSelectEnabled,
                            borderColor = spectrumPrimary.copy(alpha = 0.7f),
                            selectedBorderColor = spectrumPrimary
                        ),
                        modifier = Modifier.testTag("quick_auto_chip")
                    )
                }

                if (countryBuckets.isEmpty()) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onOpenQuickSetup,
                            label = {
                                Text(
                                    text = "+ LINK WORKER / SCAN QR",
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            },
                            shape = CutCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = VpnPalette.SlateCard.copy(alpha = 0.85f),
                                labelColor = spectrumSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = spectrumSecondary.copy(alpha = 0.6f)
                            )
                        )
                    }
                } else {
                    items(countryBuckets, key = { it.first }) { (code, _, minPing) ->
                        val isActiveCountry = isConnected &&
                            activeServer?.countryCode.equals(code, ignoreCase = true)
                        FilterChip(
                            selected = isActiveCountry,
                            onClick = { onQuickCountryConnect(code) },
                            enabled = !isBusy,
                            label = {
                                val pingLabel = if (minPing != null) " • ${minPing}ms" else ""
                                Text(
                                    text = "${countryCodeToFlag(code)} $code$pingLabel",
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            },
                            shape = CutCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = VpnPalette.SlateCard.copy(alpha = 0.85f),
                                labelColor = VpnPalette.TextPrimary,
                                selectedContainerColor = spectrumPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = spectrumPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isActiveCountry,
                                borderColor = spectrumSecondary.copy(alpha = 0.45f),
                                selectedBorderColor = spectrumPrimary
                            ),
                            modifier = Modifier.testTag("quick_country_${code.lowercase()}")
                        )
                    }
                }
            }

            // Hero Thunder-Hacker Tunnel HUD
            CyberHudCard(
                borderColor = statusColor,
                secondaryBorderColor = spectrumSecondary,
                cornerCut = 20.dp,
                contentPadding = 18.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status Badge + Session Uptime Clock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CutCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.65f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Icons.Filled.GppGood else Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                ScrambleRevealText(
                                    text = when (tunnelStatus) {
                                        TunnelStatus.CONNECTED -> "TUNNEL_LOCKED"
                                        TunnelStatus.CONNECTING -> "HANDSHAKE..."
                                        TunnelStatus.AUTO_SELECTING -> "AUTO_PROBING..."
                                        TunnelStatus.DISCONNECTING -> "CLOSING..."
                                        TunnelStatus.ERROR -> "TUNNEL_FAULT"
                                        TunnelStatus.DISCONNECTED -> "1-TAP_READY"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }

                        Surface(
                            shape = CutCornerShape(8.dp),
                            color = VpnPalette.SlateElevated.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, spectrumSecondary.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = if (isConnected) formatDuration(sessionSeconds) else "00:00:00",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) statusColor else VpnPalette.TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Giant Thunder-Hacker Power Core + Shockwave Rings
                    Box(
                        modifier = Modifier
                            .size(164.dp)
                            .scale(pulseScale * lockCoreScale.value)
                            .testTag("hero_connect_orb")
                            .clickable(enabled = !isBusy) {
                                if (isConnected) onDisconnectClick() else onConnectClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (isConnected && lockBurstProgress.value in 0.01f..0.99f) {
                                val p = lockBurstProgress.value
                                drawCircle(
                                    color = statusColor.copy(alpha = (1f - p) * 0.78f),
                                    radius = size.minDimension * (0.28f + 0.28f * p),
                                    style = Stroke(width = (5.dp.toPx() * (1f - p)).coerceAtLeast(1f))
                                )
                                drawCircle(
                                    color = spectrumSecondary.copy(alpha = (1f - p) * 0.5f),
                                    radius = size.minDimension * (0.20f + 0.34f * p),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }

                            rotate(outerAngle) {
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.55f),
                                    radius = size.minDimension * 0.46f,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                                    )
                                )
                            }

                            rotate(-outerAngle * 1.6f) {
                                val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                                drawArc(
                                    color = statusColor,
                                    startAngle = 15f,
                                    sweepAngle = 75f,
                                    useCenter = false,
                                    style = stroke
                                )
                                drawArc(
                                    color = spectrumSecondary,
                                    startAngle = 135f,
                                    sweepAngle = 75f,
                                    useCenter = false,
                                    style = stroke
                                )
                                drawArc(
                                    color = statusColor,
                                    startAngle = 255f,
                                    sweepAngle = 75f,
                                    useCenter = false,
                                    style = stroke
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.size(104.dp),
                            shape = CircleShape,
                            color = VpnPalette.SlateSurface,
                            border = BorderStroke(2.5.dp, statusColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        color = statusColor,
                                        strokeWidth = 3.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isConnected) Icons.Filled.PowerSettingsNew else Icons.Filled.Bolt,
                                        contentDescription = if (isConnected) "Disconnect VPN" else "One-Click Auto Connect VPN",
                                        tint = statusColor,
                                        modifier = Modifier.size(50.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Post-Connection Encrypted Stream & Waveform
                    AnimatedVisibility(
                        visible = isConnected || isBusy,
                        enter = fadeIn(tween(320)) + expandVertically(),
                        exit = fadeOut(tween(220)) + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isTor = activeServer?.isOpenSourceTor() != false
                            TypewriterTerminalText(
                                text = when (tunnelStatus) {
                                    TunnelStatus.CONNECTED -> if (isTor) {
                                        "ONION_LINK_LOCKED // TOR_TUN2SOCKS_ACTIVE"
                                    } else {
                                        "THUNDER_LINK_LOCKED // CHACHA20_STREAM_ACTIVE"
                                    }
                                    TunnelStatus.AUTO_SELECTING -> "SCANNING_NODES // SELECTING_LOWEST_PING"
                                    TunnelStatus.CONNECTING -> if (isTor) {
                                        "TOR_CIRCUIT_HANDSHAKE // ROUTING_TUN0"
                                    } else {
                                        "CURVE25519_HANDSHAKE // ROUTING_WG0"
                                    }
                                    else -> "FLUSHING_INTERFACE // DISCONNECTING"
                                },
                                color = statusColor
                            )
                            EncryptedPacketWaveform(
                                accentColor = statusColor,
                                secondaryColor = spectrumSecondary,
                                isActive = isConnected || isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                            )
                        }
                    }

                    // Active Server Selector Pill
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_server_selector_pill")
                            .clickable { onOpenCountries() },
                        shape = CutCornerShape(10.dp),
                        color = VpnPalette.SlateElevated.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, spectrumSecondary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeServer?.let { countryCodeToFlag(it.countryCode) } ?: "\uD83C\uDF10",
                                fontSize = 26.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                ScrambleRevealText(
                                    text = activeServer?.name ?: "SMART_AUTO_NODE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VpnPalette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (activeServer != null) {
                                        val lat = if (activeServer.latencyMs >= 0) "${activeServer.latencyMs} ms" else "AUTO_PING"
                                        val modeTag = if (activeServer.isOpenSourceTor()) "OPEN_TOR (NO-VPS)" else "WIREGUARD"
                                        "${activeServer.countryName} // $lat // $modeTag"
                                    } else {
                                        "Tap 1-TAP CONNECT for free Open-Source Multi-Country VPN"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = spectrumSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Open Country Selection",
                                tint = spectrumPrimary
                            )
                        }
                    }

                    // Primary 1-Click Connect + Rotate / Auto Best Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { if (isConnected) onDisconnectClick() else onConnectClick() },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("primary_connect_button"),
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) VpnPalette.CoralError else spectrumPrimary,
                                contentColor = VpnPalette.ObsidianBg
                            )
                        ) {
                            Text(
                                text = if (isConnected) "[ DISCONNECT ]" else "[ 1-TAP CONNECT ]",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (isConnected) onRotateNodeClick() else onAutoConnectClick()
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .height(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("auto_select_connect_button"),
                            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp),
                            border = BorderStroke(1.2.dp, spectrumSecondary.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = if (isConnected) Icons.Filled.SwapHoriz else Icons.Filled.AutoAwesome,
                                contentDescription = if (isConnected) "Rotate Node & IP" else "Auto Select Best Server",
                                tint = spectrumSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "ROTATE_IP" else "AUTO_BEST",
                                style = MaterialTheme.typography.labelLarge,
                                color = spectrumSecondary
                            )
                        }
                    }
                }
            }

            // Thunder Real-Time Speedometer & Data Usage Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrafficStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ArrowDownward,
                    label = "DOWNLINK // RX",
                    speed = "${formatBytes(trafficStats.rxBytesPerSec)}/s",
                    total = "Total ${formatBytes(trafficStats.rxBytes)}",
                    accent = spectrumPrimary,
                    secondaryAccent = spectrumSecondary
                )
                TrafficStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ArrowUpward,
                    label = "UPLINK // TX",
                    speed = "${formatBytes(trafficStats.txBytesPerSec)}/s",
                    total = "Total ${formatBytes(trafficStats.txBytes)}",
                    accent = spectrumSecondary,
                    secondaryAccent = spectrumAlt
                )
            }

            // Real IP & Country Verification HUD Card
            CyberHudCard(
                borderColor = spectrumSecondary,
                secondaryBorderColor = spectrumAlt
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = spectrumSecondary
                            )
                            ScrambleRevealText(
                                text = "IP & GEO_TRACE",
                                style = MaterialTheme.typography.titleMedium,
                                color = spectrumSecondary
                            )
                        }
                        TextButton(
                            onClick = onVerifyIpClick,
                            enabled = !isVerifyingIp,
                            modifier = Modifier.testTag("verify_ip_button")
                        ) {
                            if (isVerifyingIp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = spectrumPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Verify Public IP",
                                    tint = spectrumPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("[ TRACE ]", style = MaterialTheme.typography.labelLarge, color = spectrumPrimary)
                        }
                    }

                    val ipChanged = isConnected && baselineIp != null && ipInfo != null && baselineIp != ipInfo.ip
                    AnimatedVisibility(visible = ipChanged) {
                        Surface(
                            shape = CutCornerShape(8.dp),
                            color = VpnPalette.EmeraldDim,
                            border = BorderStroke(1.dp, spectrumPrimary.copy(alpha = 0.75f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = spectrumPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                ScrambleRevealText(
                                    text = "IP_ROTATED: $baselineIp -> ${ipInfo?.ip}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = spectrumPrimary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "> EXIT_IP",
                                style = MaterialTheme.typography.labelSmall,
                                color = spectrumSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            ScrambleRevealText(
                                text = ipInfo?.ip ?: if (isVerifyingIp) "TRACING..." else "UNAVAILABLE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = VpnPalette.TextPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "> GEO_NODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = spectrumSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            ScrambleRevealText(
                                text = ipInfo?.let {
                                    "${countryCodeToFlag(it.countryCode)} ${it.countryName}"
                                } ?: if (isVerifyingIp) "TRACING..." else "UNAVAILABLE",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = VpnPalette.TextPrimary
                            )
                        }
                    }
                }
            }

            if (totalServers == 0) {
                CyberHudCard(
                    borderColor = spectrumPrimary,
                    secondaryBorderColor = spectrumAlt,
                    containerColor = VpnPalette.SlateElevated.copy(alpha = 0.92f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScrambleRevealText(
                            text = ":: ZERO_CONFIG_AUTO_OR_QR",
                            style = MaterialTheme.typography.titleMedium,
                            color = spectrumPrimary
                        )
                        Text(
                            text = "For 1-tap auto-connect without .conf or QR (like Thunder VPN), link your Cloudflare Worker fleet once or bake DEFAULT_WORKER_URL into the build. Custom .conf & QR scan stay available anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VpnPalette.TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenQuickSetup,
                                shape = CutCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = spectrumPrimary,
                                    contentColor = VpnPalette.ObsidianBg
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text("[ WORKER_SYNC ]", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenQrScanner,
                                shape = CutCornerShape(10.dp),
                                border = BorderStroke(1.dp, spectrumSecondary),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_quick_scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    tint = spectrumSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("[ QR ]", color = spectrumSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    speed: String,
    total: String,
    accent: Color,
    secondaryAccent: Color
) {
    CyberHudCard(
        modifier = modifier,
        borderColor = accent,
        secondaryBorderColor = secondaryAccent,
        cornerCut = 12.dp,
        contentPadding = 14.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = speed,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = VpnPalette.TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = total,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = VpnPalette.TextSecondary
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600L
    val m = (totalSeconds % 3600L) / 60L
    val s = totalSeconds % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
