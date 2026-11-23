package com.example.autoiprotator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.autoiprotator.model.ServerSource
import com.example.autoiprotator.model.TunnelStatus
import com.example.autoiprotator.ui.components.CyberHudCard
import com.example.autoiprotator.ui.components.ScrambleRevealText
import com.example.autoiprotator.ui.components.TypewriterTerminalText
import com.example.autoiprotator.ui.components.rememberHackerSpectrumColor
import com.example.autoiprotator.ui.theme.JetBrainsMonoFontFamily
import com.example.autoiprotator.ui.theme.VpnPalette
import com.example.autoiprotator.vpn.WireGuardConfigParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun QrScannerScreen(
    onImportConfig: (
        rawConf: String,
        customName: String,
        countryCode: String,
        city: String,
        source: ServerSource
    ) -> Unit
) {
    val context = LocalContext.current
    var modeIndex by remember { mutableIntStateOf(0) } // 0 = Camera QR, 1 = Paste Config

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var rawConfigText by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("SG") }
    var cityName by remember { mutableStateOf("") }
    var detectedFromQr by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val primaryGlow = rememberHackerSpectrumColor(TunnelStatus.DISCONNECTED, phaseOffset = 0.2f)
    val secondaryGlow = rememberHackerSpectrumColor(TunnelStatus.DISCONNECTED, phaseOffset = 0.55f)

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = modeIndex == 0,
                    onClick = { modeIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = primaryGlow.copy(alpha = 0.22f),
                        activeContentColor = primaryGlow,
                        activeBorderColor = primaryGlow
                    ),
                    modifier = Modifier.testTag("mode_camera_qr")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("[ SCAN_QR ]", fontFamily = JetBrainsMonoFontFamily)
                }
                SegmentedButton(
                    selected = modeIndex == 1,
                    onClick = { modeIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = primaryGlow.copy(alpha = 0.22f),
                        activeContentColor = primaryGlow,
                        activeBorderColor = primaryGlow
                    ),
                    modifier = Modifier.testTag("mode_paste_conf")
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("[ PASTE_.CONF ]", fontFamily = JetBrainsMonoFontFamily)
                }
            }

            if (modeIndex == 0) {
                if (!hasCameraPermission) {
                    CyberHudCard(
                        borderColor = primaryGlow,
                        secondaryBorderColor = secondaryGlow,
                        contentPadding = 22.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = primaryGlow,
                                modifier = Modifier.size(44.dp)
                            )
                            ScrambleRevealText(
                                text = ":: OPTIC_SCANNER_LOCKED",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryGlow
                            )
                            Text(
                                text = "Grant camera permission to scan WireGuard QR codes directly from your server console, or switch to PASTE_.CONF.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VpnPalette.TextSecondary
                            )
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("grant_camera_permission_button"),
                                shape = CutCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryGlow,
                                    contentColor = VpnPalette.ObsidianBg
                                )
                            ) {
                                Text("[ ENABLE_CAMERA_SCANNER ]", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    CameraQrPreviewBox(
                        laserColor = primaryGlow,
                        bracketColor = secondaryGlow,
                        onQrDetected = { rawQr ->
                            val parsed = WireGuardConfigParser.parse(rawQr)
                            if (parsed.isSuccess) {
                                rawConfigText = rawQr
                                detectedFromQr = true
                                scanError = null
                            } else {
                                scanError = "QR detected, but payload is not a valid WireGuard configuration."
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(visible = detectedFromQr) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(8.dp),
                    color = VpnPalette.EmeraldDim,
                    border = BorderStroke(1.dp, primaryGlow)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = primaryGlow
                        )
                        ScrambleRevealText(
                            text = "PAYLOAD_CAPTURED // REVIEW & SAVE NODE",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = primaryGlow
                        )
                    }
                }
            }

            if (!scanError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CutCornerShape(8.dp),
                    color = VpnPalette.CoralDim,
                    border = BorderStroke(1.dp, VpnPalette.CoralError)
                ) {
                    Text(
                        text = "[ERR] ${scanError!!}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            CyberHudCard(
                borderColor = secondaryGlow,
                secondaryBorderColor = primaryGlow
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScrambleRevealText(
                        text = ":: WIREGUARD_NODE_CONFIG",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryGlow
                    )

                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("NODE_LABEL (e.g. Singapore VPS 01)") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = CutCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_server_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it.uppercase().take(2) },
                            label = { Text("COUNTRY (US, SG)") },
                            singleLine = true,
                            colors = fieldColors,
                            shape = CutCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("qr_country_code_input")
                        )
                        OutlinedTextField(
                            value = cityName,
                            onValueChange = { cityName = it },
                            label = { Text("CITY (OPTIONAL)") },
                            singleLine = true,
                            colors = fieldColors,
                            shape = CutCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    OutlinedTextField(
                        value = rawConfigText,
                        onValueChange = {
                            rawConfigText = it
                            detectedFromQr = false
                        },
                        label = { Text("WIREGUARD .CONF ([Interface] & [Peer])") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        colors = fieldColors,
                        shape = CutCornerShape(10.dp),
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_raw_conf_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                rawConfigText = ""
                                detectedFromQr = false
                                scanError = null
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            shape = CutCornerShape(10.dp),
                            border = BorderStroke(1.dp, secondaryGlow.copy(alpha = 0.7f))
                        ) {
                            Text("CLR", color = secondaryGlow)
                        }

                        Button(
                            onClick = {
                                onImportConfig(
                                    rawConfigText,
                                    serverName,
                                    countryCode,
                                    cityName,
                                    if (detectedFromQr) ServerSource.QR else ServerSource.MANUAL
                                )
                            },
                            enabled = rawConfigText.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("save_imported_config_button"),
                            shape = CutCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryGlow,
                                contentColor = VpnPalette.ObsidianBg
                            )
                        ) {
                            Text("[ VALIDATE & SAVE_NODE ]", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraQrPreviewBox(
    laserColor: Color,
    bracketColor: Color,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(
                    listOf(barcodeScanner),
                    CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    mainExecutor
                ) { result ->
                    val codes = result.getValue(barcodeScanner)
                    val firstText = codes?.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (!firstText.isNullOrBlank()) {
                        onQrDetected(firstText)
                    }
                }
            )
        }
    }

    DisposableEffect(lifecycleOwner, cameraController) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
            barcodeScanner.close()
        }
    }

    val laserTransition = rememberInfiniteTransition(label = "qrLaser")
    val scanProgress by laserTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp),
        shape = CutCornerShape(16.dp),
        border = BorderStroke(1.5.dp, laserColor.copy(alpha = 0.85f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        controller = cameraController
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CutCornerShape(16.dp))
            )

            // Reticle Corner Brackets + Sweeping Cyber Laser Line
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frame = size.minDimension * 0.64f
                val left = (size.width - frame) / 2f
                val top = (size.height - frame) / 2f
                val right = left + frame
                val bottom = top + frame
                val corner = 36f
                val stroke = 6f

                drawLine(bracketColor, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, top), Offset(right - corner, top), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, top), Offset(right, top + corner), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, bottom), Offset(left + corner, bottom), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - corner), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, bottom), Offset(right - corner, bottom), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - corner), stroke, StrokeCap.Round)

                val laserY = top + frame * scanProgress
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, laserColor, bracketColor, laserColor, Color.Transparent),
                        startX = left,
                        endX = right
                    ),
                    start = Offset(left + 8f, laserY),
                    end = Offset(right - 8f, laserY),
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp),
                shape = CutCornerShape(8.dp),
                color = VpnPalette.ObsidianBg.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, laserColor.copy(alpha = 0.55f))
            ) {
                TypewriterTerminalText(
                    text = "TARGET_LOCK // POINT_AT_WIREGUARD_QR",
                    color = laserColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
