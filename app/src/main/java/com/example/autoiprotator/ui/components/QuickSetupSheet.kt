package com.example.autoiprotator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.autoiprotator.ui.theme.VpnPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSetupSheet(
    initialWorkerUrl: String,
    initialToken: String,
    accentColor: Color,
    secondaryColor: Color,
    onDismiss: () -> Unit,
    onSyncAndAutoConnect: (workerUrl: String, token: String) -> Unit,
    onOpenQrOrConf: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var workerUrl by remember(initialWorkerUrl) { mutableStateOf(initialWorkerUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accentColor,
        unfocusedBorderColor = secondaryColor.copy(alpha = 0.5f),
        focusedLabelColor = accentColor
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VpnPalette.SlateSurface,
        contentColor = VpnPalette.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = accentColor)
                ScrambleRevealText(
                    text = ":: 1-TAP_AUTO_PROVISION",
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor
                )
            }

            Text(
                text = "No manual custom.conf needed: link your Cloudflare Worker Management API once to auto-discover all your WireGuard countries and connect in one tap, or import a QR config.",
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
                    .testTag("quick_setup_worker_url")
            )

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("BEARER_TOKEN (OPTIONAL / IN-MEMORY)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = fieldColors,
                shape = CutCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSyncAndAutoConnect(workerUrl, token) },
                enabled = workerUrl.isNotBlank(),
                shape = CutCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = VpnPalette.ObsidianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("quick_setup_sync_connect_button")
            ) {
                Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("[ SYNC_NODES & AUTO_CONNECT ]", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = VpnPalette.BorderSubtle)

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenQrOrConf()
                },
                shape = CutCornerShape(10.dp),
                border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("[ SCAN_QR / PASTE_CONFIG ]", color = secondaryColor)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
