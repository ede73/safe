@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import fi.iki.ede.safe.ui.sync.MerkleTreeHelper
import fi.iki.ede.safe.ui.sync.SAFE_SYNC_DEFAULT_PORT
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncNetworkService
import fi.iki.ede.safe.ui.sync.SyncPairingSession
import fi.iki.ede.safe.ui.sync.SyncPayload
import fi.iki.ede.safe.ui.sync.ContinuousAutoSyncCoordinator
import fi.iki.ede.safe.ui.sync.DeviceInfoProvider
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseLogEvent
import fi.iki.ede.logger.firebasePublishFlowEvent
import fi.iki.ede.logger.firebaseRecordException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncDialog(
    onDismissRequest: () -> Unit,
    onSyncCompleted: () -> Unit = {}
) {
    var autoSyncEnabled by remember { mutableStateOf(Preferences.isAutoSyncWifiEnabled()) }
    var isInitiator by remember { mutableStateOf(true) }
    var initiatorPin by remember { mutableStateOf(SyncPairingSession.generate8DigitPin()) }
    var inputPin by remember { mutableStateOf("") }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    val currentDeviceName = remember { DeviceInfoProvider.getFormattedDeviceName() }
    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

    val syncManager = remember { LocalDeviceSyncManager() }
    val networkService = remember { SyncNetworkService() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(isInitiator, initiatorPin) {
        firebaseLog("SafeSync", "DeviceSyncDialog effect active: role=${if (isInitiator) "SERVER" else "CLIENT"}, device=$currentDeviceName")
        firebasePublishFlowEvent("SafeSync", "DialogOpened", mapOf("role" to if (isInitiator) "server" else "client", "device" to currentDeviceName))
        if (isInitiator) {
            ContinuousAutoSyncCoordinator.instance.pauseServer()
            firebaseLog("SafeSync", "Server advertising started: name=$currentDeviceName, port=$SAFE_SYNC_DEFAULT_PORT")
            firebasePublishFlowEvent("SafeSync", "ServerStarted", mapOf("port" to SAFE_SYNC_DEFAULT_PORT.toString(), "device" to currentDeviceName))
            syncManager.startAdvertising(currentDeviceName, SAFE_SYNC_DEFAULT_PORT)
            networkService.startServer(
                port = SAFE_SYNC_DEFAULT_PORT,
                pin = initiatorPin,
                onPayloadReceived = { remotePayloadJson ->
                    try {
                        firebaseLog("SafeSync", "Server processing incoming payload (bytes=${remotePayloadJson.length})...")
                        val remotePayload = Json.decodeFromString<SyncPayload>(remotePayloadJson)
                        val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                        val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
                        val peerName = remotePayload.deviceName.ifBlank { "Safe Peer" }
                        val stableId = "peer-${peerName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
                        Preferences.addPairedSyncDevice(stableId, peerName)
                        firebaseLog("SafeSync", "Server paired device registered: id=$stableId, name=$peerName, remoteItems=${remotePayload.items.size}, localItems=${localItems.size}")
                        firebasePublishFlowEvent("SafeSync", "ServerPayloadReceived", mapOf(
                            "peer" to peerName,
                            "remoteItems" to remotePayload.items.size.toString(),
                            "localItems" to localItems.size.toString()
                        ))

                        if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                            firebaseLog("SafeSync", "Server: Merkle roots match (${localTree.rootHash.take(8)}). Already synchronized.")
                            firebasePublishFlowEvent("SafeSync", "ServerAlreadySynced", mapOf("root" to localTree.rootHash.take(8)))
                            syncStatusMessage = "Already synchronized ✓"
                            onSyncCompleted()
                            return@startServer Json.encodeToString(SyncPayload(emptyList(), localTree.rootHash, currentDeviceName))
                        }

                        val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
                        firebaseLog("SafeSync", "Server: Analyzing conflicts, detected=${conflicts.size}")
                        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)

                        val updatedLocalItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                        val updatedTree = MerkleTreeHelper.buildMerkleVault(updatedLocalItems)
                        firebaseLog("SafeSync", "Server: Conflicts applied, new local items count=${updatedLocalItems.size}")
                        firebasePublishFlowEvent("SafeSync", "ServerConflictsApplied", mapOf(
                            "conflicts" to conflicts.size.toString(),
                            "updatedItems" to updatedLocalItems.size.toString()
                        ))
                        syncStatusMessage = "Device paired & synchronized successfully!"
                        onSyncCompleted()
                        ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER")
                        Json.encodeToString(SyncPayload(updatedLocalItems, updatedTree.rootHash, currentDeviceName))
                    } catch (e: Exception) {
                        firebaseRecordException("SafeSync server error in onPayloadReceived: ${e::class.simpleName}", e)
                        firebasePublishFlowEvent("SafeSync", "ServerError", mapOf("error" to (e::class.simpleName ?: "unknown"), "message" to (e.message ?: "")))
                        e.printStackTrace()
                        throw e
                    }
                }
            )
        } else {
            firebaseLog("SafeSync", "Client starting mDNS discovery...")
            firebasePublishFlowEvent("SafeSync", "ClientDiscoveryStarted", emptyMap())
            syncManager.startDiscovery(
                onDeviceFound = { device ->
                    val isSelf = device.name.equals(currentDeviceName, ignoreCase = true) ||
                                 ((device.hostAddress == "127.0.0.1" || device.hostAddress == "localhost") &&
                                  (device.port == networkService.boundPort || device.port == SAFE_SYNC_DEFAULT_PORT))
                    if (!isSelf && discoveredDevices.none { it.id == device.id }) {
                        firebaseLog("SafeSync", "Client discovered peer: ${device.name} at ${device.hostAddress}:${device.port}")
                        firebasePublishFlowEvent("SafeSync", "ClientPeerDiscovered", mapOf("peer" to device.name, "host" to device.hostAddress, "port" to device.port.toString()))
                        discoveredDevices.add(device)
                        if (selectedDevice == null) {
                            selectedDevice = device
                        }
                    }
                },
                onDeviceLost = { device ->
                    firebaseLog("SafeSync", "Client lost peer: ${device.name} (${device.id})")
                    discoveredDevices.removeAll { it.id == device.id }
                    if (selectedDevice?.id == device.id) {
                        selectedDevice = discoveredDevices.firstOrNull()
                    }
                }
            )
        }
        onDispose {
            firebaseLog("SafeSync", "DeviceSyncDialog effect disposed (stopping discovery/advertising/server)")
            syncManager.stopAdvertising()
            syncManager.stopDiscovery()
            networkService.stopServer()
            ContinuousAutoSyncCoordinator.instance.resumeServer()
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
                // Continuous Auto-Sync info & toggle banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Continuous Auto-Sync",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (autoSyncEnabled) "Enabled on local Wi-Fi" else "Disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (autoSyncEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { checked ->
                                autoSyncEnabled = checked
                                Preferences.setAutoSyncWifiEnabled(checked)
                            }
                        )
                    }
                }

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
                                .heightIn(max = 140.dp)
                        ) {
                            items(discoveredDevices) { device ->
                                val isSelected = selectedDevice?.id == device.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedDevice = device }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${if (isSelected) "✓ " else "• "}${device.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 3,
                                        softWrap = true
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Searching for nearby Safe devices...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                        color = if (msg.contains("Error") || msg.contains("rejected")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (isInitiator) {
                TextButton(onClick = onDismissRequest) {
                    Text("Broadcasting...")
                }
            } else {
                Button(
                    enabled = !isSyncing && inputPin.length == 8,
                    onClick = {
                        val rawCandidateHosts = if (selectedDevice != null) {
                            listOf(selectedDevice!!.hostAddress).filter { it.isNotBlank() }
                        } else {
                            discoveredDevices.map { it.hostAddress }.filter { it.isNotBlank() }.distinct()
                        }
                        val candidateHosts = when {
                            rawCandidateHosts.isNotEmpty() -> rawCandidateHosts
                            networkService.isAndroidGuest -> listOf("10.0.2.2")
                            else -> emptyList()
                        }
                        if (candidateHosts.isEmpty()) {
                            firebaseLog("SafeSync", "Client: No candidate hosts found on local network")
                            firebaseLogEvent("safe_sync_client_no_hosts")
                            syncStatusMessage = "No devices found on local network. Please ensure both devices are connected to the same Wi-Fi."
                            return@Button
                        }
                        val candidatePorts = if (selectedDevice != null && selectedDevice!!.port > 0) {
                            listOf(selectedDevice!!.port, SAFE_SYNC_DEFAULT_PORT).distinct()
                        } else {
                            listOf(SAFE_SYNC_DEFAULT_PORT)
                        }
                        isSyncing = true
                        syncStatusMessage = "Connecting to ${candidateHosts.first()}:${candidatePorts.first()}..."
                        firebaseLog("SafeSync", "Client initiating sync to candidate hosts: $candidateHosts on ports $candidatePorts")
                        firebasePublishFlowEvent("SafeSync", "ClientSyncStarted", mapOf("targetHosts" to candidateHosts.joinToString(",")))
                        coroutineScope.launch {
                            try {
                                val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                                val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
                                val localJson = Json.encodeToString(SyncPayload(localItems, localTree.rootHash, currentDeviceName))
                                firebaseLog("SafeSync", "Client prepared payload: localItems=${localItems.size}, localRoot=${localTree.rootHash.take(8)}, payloadBytes=${localJson.length}")
                                firebasePublishFlowEvent("SafeSync", "ClientPreparedPayload", mapOf("localItems" to localItems.size.toString(), "bytes" to localJson.length.toString()))

                                var remoteJson: String? = null
                                var lastError: Exception? = null

                                outer@ for (port in candidatePorts) {
                                    for (host in candidateHosts) {
                                        try {
                                            syncStatusMessage = "Connecting to $host:$port..."
                                            firebaseLog("SafeSync", "Client attempting payload send to $host:$port...")
                                            remoteJson = networkService.sendPayloadToHost(
                                                hostAddress = host,
                                                port = port,
                                                pin = inputPin,
                                                localPayloadJson = localJson
                                            )
                                            if (remoteJson != null) {
                                                firebaseLog("SafeSync", "Client successfully received response from $host:$port (bytes=${remoteJson.length})")
                                                firebasePublishFlowEvent("SafeSync", "ClientReceivedResponse", mapOf("host" to host, "port" to port.toString(), "bytes" to remoteJson.length.toString()))
                                                break@outer
                                            }
                                        } catch (e: Exception) {
                                            firebaseLog("SafeSync", "Client attempt to $host:$port failed: ${e::class.simpleName} - ${e.message}")
                                            lastError = e
                                        }
                                    }
                                }

                                if (remoteJson == null) {
                                    throw lastError ?: IllegalStateException("Failed to connect to target device")
                                }

                                val remotePayload = Json.decodeFromString<SyncPayload>(remoteJson)
                                val peerName = if (remotePayload.deviceName.isNotBlank()) {
                                    remotePayload.deviceName
                                } else if (selectedDevice != null && selectedDevice!!.name.isNotBlank()) {
                                    selectedDevice!!.name
                                } else {
                                    "Safe Peer (${candidateHosts.first()})"
                                }
                                val stableId = "peer-${peerName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
                                Preferences.addPairedSyncDevice(stableId, peerName)
                                firebaseLog("SafeSync", "Client registered paired peer: id=$stableId, name=$peerName, remoteItems=${remotePayload.items.size}")
                                firebasePublishFlowEvent("SafeSync", "ClientPeerPaired", mapOf("peerName" to peerName, "remoteItems" to remotePayload.items.size.toString()))

                                if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                                    firebaseLog("SafeSync", "Client: Merkle roots match (${localTree.rootHash.take(8)}). Already synchronized.")
                                    firebasePublishFlowEvent("SafeSync", "ClientAlreadySynced", mapOf("peer" to peerName, "root" to localTree.rootHash.take(8)))
                                    syncStatusMessage = "Already synchronized ✓"
                                    onSyncCompleted()
                                } else {
                                    val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
                                    firebaseLog("SafeSync", "Client: Detected ${conflicts.size} conflicts, resolving...")
                                    SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)
                                    firebaseLog("SafeSync", "Client: Conflicts applied successfully")
                                    firebasePublishFlowEvent("SafeSync", "ClientConflictsApplied", mapOf(
                                        "peer" to peerName,
                                        "conflicts" to conflicts.size.toString()
                                    ))
                                    syncStatusMessage = "Connected & synchronized successfully!"
                                    onSyncCompleted()
                                    ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER")
                                }
                                if (selectedDevice != null) {
                                    ContinuousAutoSyncCoordinator.instance.registerPairedPeer(selectedDevice!!)
                                }
                            } catch (e: Exception) {
                                firebaseRecordException("SafeSync client sync failed: ${e::class.simpleName}", e)
                                firebasePublishFlowEvent("SafeSync", "ClientSyncFailed", mapOf("error" to (e::class.simpleName ?: "unknown"), "message" to (e.message ?: "")))
                                e.printStackTrace()
                                syncStatusMessage = "Error: ${e.message ?: "Failed to sync"}"
                            } finally {
                                isSyncing = false
                            }
                        }
                    }
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Connect & Sync")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}
