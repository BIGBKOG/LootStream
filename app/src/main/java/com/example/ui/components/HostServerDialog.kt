package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.HostServerConfig
import com.example.data.remote.DiscoveredHost
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostServerDialog(
    servers: List<HostServerConfig>,
    discoveredHosts: List<DiscoveredHost>,
    isDiscoveringHosts: Boolean,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onDiscoverHosts: () -> Unit,
    onConnectQuickCode: (code: String, preferredUrl: String?, onDone: (Boolean, String?) -> Unit) -> Unit,
    onConnectQrPayload: (rawQr: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onConnect: (url: String, pin: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onSyncServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val s = LocalAppStrings.current
    var quickCodeInput by remember { mutableStateOf("") }
    var selectedDiscoveredUrl by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("http://192.168.1.") }
    var pinInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showManualForm by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onDiscoverHosts()
    }

    LaunchedEffect(discoveredHosts) {
        if (selectedDiscoveredUrl == null && discoveredHosts.isNotEmpty()) {
            selectedDiscoveredUrl = discoveredHosts.first().primaryUrl
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .heightIn(max = 720.dp)
                .testTag("host_server_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonPurple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = NeonPurple
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.hostDialogTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Wi-Fi QR Code, 6-Digit Easy Login & LAN Sync",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = s.cancelAction)
                    }
                }

                // 1. Primary Easy Wi-Fi Login Card (QR + Auto-Discovery + 6-Digit Code)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.32f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                localError = null
                                showQrScanner = true
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_host_qr_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.hostScanQrBtn,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = s.hostNearbyTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDiscoverHosts,
                                enabled = !isDiscoveringHosts,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("discover_hosts_refresh_btn")
                            ) {
                                if (isDiscoveringHosts) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = s.hostNearbyTitle,
                                        tint = NeonCyan
                                    )
                                }
                            }
                        }

                        if (isDiscoveringHosts && discoveredHosts.isEmpty()) {
                            Text(
                                text = s.hostScanningWifi,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (discoveredHosts.isEmpty()) {
                            Text(
                                text = s.hostNoNearbyFound,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                discoveredHosts.forEach { host ->
                                    val isSelected = selectedDiscoveredUrl == host.primaryUrl
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedDiscoveredUrl = host.primaryUrl
                                            urlInput = host.primaryUrl
                                            localError = null
                                        },
                                        label = {
                                            Text(
                                                text = "${host.serverName} (${host.hostPlatform})",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Computer,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonGreen
                                        )
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = quickCodeInput,
                            onValueChange = {
                                quickCodeInput = it.filter { ch -> ch.isDigit() }.take(6)
                                localError = null
                            },
                            label = { Text(s.hostQuickCodeLabel) },
                            placeholder = { Text(s.hostQuickCodeHint) },
                            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_code_input")
                        )

                        Button(
                            onClick = {
                                val cleaned = quickCodeInput.filter { it.isDigit() }
                                if (cleaned.length != 6) {
                                    localError = s.hostQuickCodeHint
                                    return@Button
                                }
                                onConnectQuickCode(cleaned, selectedDiscoveredUrl) { ok, err ->
                                    if (ok) {
                                        localError = null
                                        quickCodeInput = ""
                                    } else {
                                        localError = err
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("quick_code_submit_btn")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(s.hostQuickCodeBtn, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                localError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 2. Collapsible Manual IP / Static PIN fallback
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showManualForm = !showManualForm }
                        .testTag("manual_host_toggle_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = s.hostManualToggle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showManualForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = s.hostManualToggle,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showManualForm) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                localError = null
                            },
                            label = { Text(s.hostUrlLabel) },
                            placeholder = { Text(s.hostUrlHint) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_url_input")
                        )

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it.filter { ch -> ch.isDigit() }.take(8)
                                localError = null
                            },
                            label = { Text(s.hostPinLabel) },
                            placeholder = { Text(s.hostPinHint) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_pin_input")
                        )

                        OutlinedButton(
                            onClick = {
                                val trimmed = urlInput.trim()
                                if (trimmed.isBlank() || trimmed.endsWith(".")) {
                                    localError = s.hostUrlHint
                                    return@OutlinedButton
                                }
                                onConnect(trimmed, pinInput.trim()) { ok, err ->
                                    if (ok) {
                                        localError = null
                                        pinInput = ""
                                    } else {
                                        localError = err
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("host_connect_submit_btn")
                        ) {
                            Text(s.hostConnectBtn, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = s.hostConnectedServers,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (servers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = s.hostNoServers,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        servers.forEach { srv ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(NeonGreen)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = srv.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${srv.hostPlatform} • ${srv.serverUrl} • ${srv.itemCount} items",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onSyncServer(srv.id) },
                                        enabled = !isSyncing,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = s.hostSyncNow,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveServer(srv.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = s.hostDisconnect,
                                            tint = MaterialTheme.colorScheme.error
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

    if (showQrScanner) {
        QrScannerDialog(
            onQrScanned = { rawQr ->
                showQrScanner = false
                onConnectQrPayload(rawQr) { ok, err ->
                    if (ok) {
                        localError = null
                    } else {
                        localError = err
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }
}
