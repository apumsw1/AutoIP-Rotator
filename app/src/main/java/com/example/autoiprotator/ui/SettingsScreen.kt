package com.example.autoiprotator.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.autoiprotator.model.ConnectionAuditLog
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.model.UiState
import com.example.autoiprotator.model.UserPreferences
import com.example.autoiprotator.model.countryCodeToFlag
import com.example.autoiprotator.ui.components.CyberHudCard
import com.example.autoiprotator.ui.components.ScrambleRevealText
import com.example.autoiprotator.ui.components.TypewriterTerminalText
import com.example.autoiprotator.ui.components.rememberHackerSpectrumColor
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.VpnPalette

@Composable
fun SettingsScreen(
    userDisplayName: String,
    userEmail: String?,
    preferencesState: UiState<UserPreferences?>,
    logsState: UiState<List<ConnectionAuditLog>>,
    workerBearerToken: String,
    isSyncingWorker: Boolean,
    onBearerTokenChange: (String) -> Unit,
    onSavePreferences: (workerUrl: String, autoSelect: Boolean, preferredCountry: String) -> Unit,
    onSyncWorker: (workerUrl: String, token: String) -> Unit,
    onSignOutClick: () -> Unit
) {
    val prefs = (preferencesState as? UiState.Success)?.data
    var workerUrl by remember(prefs?.workerBaseUrl) {
        mutableStateOf(prefs?.workerBaseUrl.orEmpty())
    }
    var autoSelectEnabled by remember(prefs?.autoSelectEnabled) {
        mutableStateOf(prefs?.autoSelectEnabled ?: true)
    }
    var preferredCountry by remember(prefs?.preferredCountryCode) {
        mutableStateOf(prefs?.preferredCountryCode ?: "AUTO")
    }

    LaunchedEffect(prefs) {
        if (prefs != null) {
            workerUrl = prefs.workerBaseUrl
            autoSelectEnabled = prefs.autoSelectEnabled
            preferredCountry = prefs.preferredCountryCode
        }
    }

    val primaryGlow = rememberHackerSpectrumColor(TunnelStatus.DISCONNECTED, phaseOffset = 0.15f)
    val secondaryGlow = rememberHackerSpectrumColor(TunnelStatus.DISCONNECTED, phaseOffset = 0.5f)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primaryGlow,
        unfocusedBorderColor = secondaryGlow.copy(alpha = 0.45f),
        focusedLabelColor = primaryGlow
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Operator Session HUD Card
            CyberHudCard(
                borderColor = primaryGlow,
                secondaryBorderColor = secondaryGlow
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CutCornerShape(10.dp),
                        color = primaryGlow.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, primaryGlow.copy(alpha = 0.65f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = primaryGlow
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ScrambleRevealText(
                            text = userDisplayName.ifBlank { "OPERATOR_SESSION" },
                            style = MaterialTheme.typography.titleMedium,
                            color = VpnPalette.TextPrimary
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = "> $userEmail",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = secondaryGlow
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOutClick,
                        shape = CutCornerShape(10.dp),
                        border = BorderStroke(1.dp, VpnPalette.CoralError.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = VpnPalette.CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("LOGOUT", color = VpnPalette.CoralError)
                    }
                }
            }

            // Cloudflare Worker Management API Console Card
            CyberHudCard(
                borderColor = secondaryGlow,
                secondaryBorderColor = primaryGlow
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = secondaryGlow)
                        ScrambleRevealText(
                            text = ":: CLOUDFLARE_WORKER_API",
                            style = MaterialTheme.typography.titleMedium,
                            color = secondaryGlow
                        )
                    }
                    Text(
                        text = "Used strictly for node discovery, peer public-key registration, and key rotation—VPN traffic runs directly over your WireGuard VPS nodes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VpnPalette.TextSecondary
                    )

                    OutlinedTextField(
                        value = workerUrl,
                        onValueChange = { workerUrl = it },
                        label = { Text("WORKER_HTTPS_URL") },
                        placeholder = { Text("https://ultra-vpn-api.yourname.workers.dev") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = CutCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("worker_url_input")
                    )

                    OutlinedTextField(
                        value = workerBearerToken,
                        onValueChange = onBearerTokenChange,
                        label = { Text("BEARER_TOKEN (IN-MEMORY)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = fieldColors,
                        shape = CutCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("worker_token_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AUTO_SERVER_SELECTION",
                                style = MaterialTheme.typography.labelLarge,
                                color = primaryGlow
                            )
                            Text(
                                text = "Automatically pick lowest-latency node on connect",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VpnPalette.TextSecondary
                            )
                        }
                        Switch(
                            checked = autoSelectEnabled,
                            onCheckedChange = { autoSelectEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VpnPalette.ObsidianBg,
                                checkedTrackColor = primaryGlow
                            ),
                            modifier = Modifier.testTag("auto_select_switch")
                        )
                    }

                    OutlinedTextField(
                        value = preferredCountry,
                        onValueChange = { preferredCountry = it.uppercase().take(4) },
                        label = { Text("PREFERRED_COUNTRY (AUTO, SG, US, DE)") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = CutCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSavePreferences(workerUrl, autoSelectEnabled, preferredCountry)
                            },
                            shape = CutCornerShape(10.dp),
                            border = BorderStroke(1.dp, secondaryGlow),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_settings_button")
                        ) {
                            Text("[ SAVE_CFG ]", color = secondaryGlow)
                        }
                        Button(
                            onClick = { onSyncWorker(workerUrl, workerBearerToken) },
                            enabled = !isSyncingWorker && workerUrl.isNotBlank(),
                            shape = CutCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_worker_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryGlow,
                                contentColor = VpnPalette.ObsidianBg
                            )
                        ) {
                            if (isSyncingWorker) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = VpnPalette.ObsidianBg
                                )
                            } else {
                                Text("[ SYNC_NODES ]", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Key Hygiene & Security Audit Card
            CyberHudCard(
                borderColor = primaryGlow,
                secondaryBorderColor = secondaryGlow,
                containerColor = VpnPalette.EmeraldDim.copy(alpha = 0.45f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = primaryGlow)
                        ScrambleRevealText(
                            text = ":: KEY_HYGIENE_ACTIVE",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryGlow
                        )
                    }
                    Text(
                        text = "Exposed static keys from config/*.conf were purged. Run config/rotate-keys.sh on your VPS to revoke old peers, and tap the Key icon on any node card to rotate on-device Curve25519 keys.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VpnPalette.TextPrimary
                    )
                }
            }

            // Terminal Audit Logs Feed
            CyberHudCard(
                borderColor = secondaryGlow,
                secondaryBorderColor = primaryGlow
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = primaryGlow)
                        ScrambleRevealText(
                            text = ":: AUDIT_EVENT_STREAM",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryGlow
                        )
                    }
                    when (logsState) {
                        is UiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = primaryGlow
                            )
                        }
                        is UiState.Error -> {
                            Text(logsState.message, color = VpnPalette.CoralError)
                        }
                        is UiState.Success -> {
                            if (logsState.data.isEmpty()) {
                                TypewriterTerminalText(
                                    text = "LOG_BUFFER_EMPTY // AWAITING_EVENTS",
                                    color = VpnPalette.TextSecondary
                                )
                            } else {
                                logsState.data.take(8).forEachIndexed { idx, log ->
                                    if (idx > 0) HorizontalDivider(color = VpnPalette.BorderSubtle)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${countryCodeToFlag(log.countryCode)} ${log.serverName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = VpnPalette.TextPrimary
                                            )
                                            if (log.verifiedIp.isNotBlank()) {
                                                Text(
                                                    text = "> ${log.verifiedIp} // ${log.verifiedCountry}",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = secondaryGlow
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = CutCornerShape(6.dp),
                                            color = VpnPalette.SlateElevated,
                                            border = BorderStroke(1.dp, primaryGlow.copy(alpha = 0.55f))
                                        ) {
                                            Text(
                                                text = log.eventType.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = primaryGlow,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
