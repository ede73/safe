package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.safe.ui.sync.DiscoveredDevice
import fi.iki.ede.safe.ui.sync.LocalDeviceSyncManager
import fi.iki.ede.safe.ui.sync.SyncPairingSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncDialog(
    onDismissRequest: () -> Unit,
    onStartSyncWithPin: (pin: String, isInitiator: Boolean) -> Unit
) {
    var isInitiator by remember { mutableStateOf(true) }
    var initiatorPin by remember { mutableStateOf(SyncPairingSession.generate8DigitPin()) }
    var inputPin by remember { mutableStateOf("") }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }

    val syncManager = remember { LocalDeviceSyncManager() }

    DisposableEffect(isInitiator) {
        if (isInitiator) {
            syncManager.startAdvertising("Safe-Device", 8443)
        } else {
            syncManager.startDiscovery(
                onDeviceFound = { device ->
                    if (discoveredDevices.none { it.id == device.id }) {
                        discoveredDevices.add(device)
                    }
                },
                onDeviceLost = { device ->
                    discoveredDevices.removeAll { it.id == device.id }
                }
            )
        }
        onDispose {
            syncManager.stopAdvertising()
            syncManager.stopDiscovery()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Local Device Sync",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "mDNS Local Device Sync",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode selector tabs
                SecondaryTabRow(
                    selectedTabIndex = if (isInitiator) 0 else 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = isInitiator,
                        onClick = {
                            isInitiator = true
                            initiatorPin = SyncPairingSession.generate8DigitPin()
                            syncStatusMessage = null
                        },
                        text = { Text("Display PIN") }
                    )
                    Tab(
                        selected = !isInitiator,
                        onClick = {
                            isInitiator = false
                            syncStatusMessage = null
                        },
                        text = { Text("Enter PIN") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isInitiator) {
                    Text(
                        text = "Broadcasting via mDNS (_safe-sync._tcp).\nEnter this 8-digit PIN on the target device:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = SyncPairingSession.formatPinForDisplay(initiatorPin),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    IconButton(onClick = {
                        initiatorPin = SyncPairingSession.generate8DigitPin()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate PIN")
                    }
                } else {
                    Text(
                        text = "Discovering nearby devices via mDNS...\nEnter the 8-digit PIN shown on the target device:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (discoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Discovered Devices (${discoveredDevices.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 80.dp)
                        ) {
                            items(discoveredDevices) { device ->
                                Text(
                                    text = "• ${device.name} (${device.hostAddress}:${device.port})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { inputPin = SyncPairingSession.normalizePinInput(it) },
                        label = { Text("8-Digit Sync PIN") },
                        placeholder = { Text("e.g. 49208173") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }

                syncStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetPin = if (isInitiator) initiatorPin else inputPin
                    if (targetPin.length == 8) {
                        syncStatusMessage = "Pairing via mDNS local Wi-Fi..."
                        onStartSyncWithPin(targetPin, isInitiator)
                    } else {
                        syncStatusMessage = "Please enter a valid 8-digit PIN."
                    }
                }
            ) {
                Text(if (isInitiator) "Broadcasting..." else "Connect & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
