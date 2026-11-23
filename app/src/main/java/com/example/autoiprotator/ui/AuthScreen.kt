package com.example.autoiprotator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.autoiprotator.auth.attemptAutoSignIn
import com.example.autoiprotator.auth.onGoogleSignInClicked
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.ui.components.CyberHudCard
import com.example.autoiprotator.ui.components.HackerBackdrop
import com.example.autoiprotator.ui.components.ScrambleRevealText
import com.example.autoiprotator.ui.components.TypewriterTerminalText
import com.example.autoiprotator.ui.components.rememberHackerSpectrumColor
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.VpnPalette

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingAuto by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAuto = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingAuto = false
            },
            scope = scope
        )
    }

    val primaryGlow = rememberHackerSpectrumColor(TunnelStatus.DISCONNECTED, phaseOffset = 0f)
    val secondaryGlow = rememberHackerSpectrumColor(TunnelStatus.DISCONNECTED, phaseOffset = 0.35f)

    val ringTransition = rememberInfiniteTransition(label = "authShieldOrbit")
    val orbitDeg by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbitDeg"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HackerBackdrop(
            accentColor = primaryGlow,
            secondaryColor = secondaryGlow,
            isConnected = false
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Animated Cyber Shield Core
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(orbitDeg) {
                            drawArc(
                                color = primaryGlow.copy(alpha = 0.85f),
                                startAngle = 0f,
                                sweepAngle = 110f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = secondaryGlow.copy(alpha = 0.75f),
                                startAngle = 180f,
                                sweepAngle = 110f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        rotate(-orbitDeg * 1.4f) {
                            drawCircle(
                                color = primaryGlow.copy(alpha = 0.25f),
                                radius = size.minDimension * 0.39f,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CutCornerShape(16.dp),
                        color = VpnPalette.SlateCard,
                        border = BorderStroke(1.5.dp, primaryGlow.copy(alpha = 0.8f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Ultra Multi VPN Shield",
                                tint = primaryGlow,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScrambleRevealText(
                        text = "[ ULTRA MULTI VPN ]",
                        style = MaterialTheme.typography.displaySmall,
                        color = VpnPalette.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    TypewriterTerminalText(
                        text = "WIREGUARD_ENGINE // ZERO_3RD_PARTY_TUNNELS",
                        color = primaryGlow,
                        textAlign = TextAlign.Center
                    )
                }

                CyberHudCard(
                    borderColor = primaryGlow,
                    secondaryBorderColor = secondaryGlow
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ScrambleRevealText(
                            text = ":: AUTHENTICATED_ACCESS_GATE",
                            style = MaterialTheme.typography.labelLarge,
                            color = primaryGlow
                        )
                        SecurityFeatureRow(
                            icon = Icons.Filled.VerifiedUser,
                            accentColor = primaryGlow,
                            title = "[01] ZERO-TRUST PROFILE VAULT",
                            subtitle = "Each operator session isolates WireGuard nodes & encrypted audit logs."
                        )
                        SecurityFeatureRow(
                            icon = Icons.Filled.VpnKey,
                            accentColor = secondaryGlow,
                            title = "[02] ON-DEVICE CURVE25519 KEYS",
                            subtitle = "Generate & rotate WireGuard client keypairs locally without exposing secrets."
                        )
                        SecurityFeatureRow(
                            icon = Icons.Filled.QrCodeScanner,
                            accentColor = primaryGlow,
                            title = "[03] WORKER API & OPTIC QR SCAN",
                            subtitle = "Sync private multi-country nodes or scan WireGuard QR configs live."
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = VpnPalette.CoralDim,
                        border = BorderStroke(1.dp, VpnPalette.CoralError.copy(alpha = 0.75f))
                    ) {
                        Text(
                            text = "[ERR] ${errorMessage!!}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading && !isCheckingAuto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("google_sign_in_button"),
                    shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp, topEnd = 4.dp, bottomStart = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGlow,
                        contentColor = VpnPalette.ObsidianBg
                    )
                ) {
                    if (isLoading || isCheckingAuto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = VpnPalette.ObsidianBg,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isCheckingAuto) "VERIFYING_SESSION..." else "AUTHENTICATING...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "INIT_AUTH // SIGN IN WITH GOOGLE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityFeatureRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CutCornerShape(8.dp),
            color = VpnPalette.SlateElevated,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VpnPalette.TextSecondary
            )
        }
    }
}
