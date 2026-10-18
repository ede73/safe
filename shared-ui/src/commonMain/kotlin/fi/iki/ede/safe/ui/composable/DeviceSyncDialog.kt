@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import fi.iki.ede.safe.ui.sync.DevicePlatformInfo
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseLogEvent
import fi.iki.ede.logger.firebasePublishFlowEvent
import fi.iki.ede.logger.firebaseRecordException
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import fi.iki.ede.safe.ui.sync.syncJson
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
    var isSyncCompleted by remember { mutableStateOf(false) }

    var currentDeviceName by remember { mutableStateOf(DeviceInfoProvider.getFormattedDeviceName()) }
    var showNamePrompt by remember { mutableStateOf(Preferences.getSyncDeviceCustomName().isBlank()) }
    var customNameInput by remember { mutableStateOf(Preferences.getSyncDeviceCustomName().ifBlank { DevicePlatformInfo.modelName }) }

    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

    val syncManager = remember { LocalDeviceSyncManager() }
    val networkService = remember { SyncNetworkService() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(isInitiator, initiatorPin, currentDeviceName, showNamePrompt) {
        if (showNamePrompt) return@DisposableEffect onDispose {}
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
                        isSyncing = true
                        isSyncCompleted = false
                        syncStatusMessage = "Incoming connection: Receiving vault data..."
                        firebaseLog("SafeSync", "Server processing incoming payload (bytes=${remotePayloadJson.length})...")
                        val remotePayload = syncJson.decodeFromString<SyncPayload>(remotePayloadJson)
                        val peerName = remotePayload.deviceName.ifBlank { "Safe Peer" }
                        syncStatusMessage = "Connected to $peerName. Comparing vault versions..."
                        val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                        val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
                        val stableId = "peer-${peerName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
                        Preferences.addPairedSyncDevice(stableId, peerName)
                        Preferences.setLastSyncTime()
                        firebaseLog("SafeSync", "Server paired device registered: id=$stableId, name=$peerName, remoteItems=${remotePayload.items.size}, localItems=${localItems.size}")
                        firebasePublishFlowEvent("SafeSync", "ServerPayloadReceived", mapOf(
                            "peer" to peerName,
                            "remoteItems" to remotePayload.items.size.toString(),
                            "localItems" to localItems.size.toString()
                        ))

                        if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                            firebaseLog("SafeSync", "Server: Merkle roots match (${localTree.rootHash.take(8)}). Already synchronized.")
                            firebasePublishFlowEvent("SafeSync", "ServerAlreadySynced", mapOf("root" to localTree.rootHash.take(8)))
                            syncStatusMessage = "Sync complete: All ${localItems.size} items are in sync with $peerName ✓"
                            isSyncCompleted = true
                            isSyncing = false
                            onSyncCompleted()
                            return@startServer syncJson.encodeToString(SyncPayload(emptyList(), localTree.rootHash, currentDeviceName))
                        }

                        syncStatusMessage = "Analyzing vault differences with $peerName..."
                        val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
                        firebaseLog("SafeSync", "Server: Analyzing conflicts, detected=${conflicts.size}")
                        if (conflicts.isNotEmpty()) {
                            syncStatusMessage = "Applying ${conflicts.size} change(s) from $peerName..."
                            SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)
                        }

                        val updatedLocalItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                        val updatedTree = MerkleTreeHelper.buildMerkleVault(updatedLocalItems)
                        firebaseLog("SafeSync", "Server: Conflicts applied, new local items count=${updatedLocalItems.size}")
                        firebasePublishFlowEvent("SafeSync", "ServerConflictsApplied", mapOf(
                            "conflicts" to conflicts.size.toString(),
                            "updatedItems" to updatedLocalItems.size.toString()
                        ))
                        syncStatusMessage = if (conflicts.isNotEmpty()) {
                            "Sync complete: Synchronized ${conflicts.size} change(s) with $peerName ✓"
                        } else {
                            "Sync complete: All ${updatedLocalItems.size} items are in sync with $peerName ✓"
                        }
                        isSyncCompleted = true
                        isSyncing = false
                        onSyncCompleted()
                        ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER")
                        syncJson.encodeToString(SyncPayload(updatedLocalItems, updatedTree.rootHash, currentDeviceName))
                    } catch (e: Exception) {
                        isSyncing = false
                        syncStatusMessage = "Sync error: ${e.message ?: "Failed"}"
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

    if (showNamePrompt) {
        AlertDialog(
            onDismissRequest = {
                // Allow user not to set it
                showNamePrompt = false
            },
            title = {
                Text(
                    text = "Name This Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set a custom name for this device so other devices can easily identify it when syncing:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customNameInput,
                        onValueChange = { customNameInput = it },
                        label = { Text("Device Name") },
                        placeholder = { Text(DevicePlatformInfo.modelName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            Preferences.setSyncDeviceCustomName(trimmed)
                            currentDeviceName = DeviceInfoProvider.getFormattedDeviceName()
                        }
                        showNamePrompt = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Allow user not to set it
                        showNamePrompt = false
                    }
                ) {
                    Text("Use Default (${DevicePlatformInfo.modelName})")
                }
            }
        )
    } else {
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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Continuous Auto-Sync info & toggle banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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

                // Device Name indicator & edit button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            customNameInput = Preferences.getSyncDeviceCustomName().ifBlank { DevicePlatformInfo.modelName }
                            showNamePrompt = true
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "This Device: $currentDeviceName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Mode selector tabs
                SecondaryTabRow(
                    selectedTabIndex = if (isInitiator) 0 else 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = isInitiator,
                        enabled = !isSyncing,
                        onClick = {
                            isInitiator = true
                            isSyncCompleted = false
                            initiatorPin = SyncPairingSession.generate8DigitPin()
                            syncStatusMessage = null
                        },
                        text = { Text("Display PIN") }
                    )
                    Tab(
                        selected = !isInitiator,
                        enabled = !isSyncing,
                        onClick = {
                            isInitiator = false
                            isSyncCompleted = false
                            syncStatusMessage = null
                        },
                        text = { Text("Enter PIN") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSyncing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Synchronizing Vault...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = syncStatusMessage ?: "Connecting...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Please keep both devices nearby and Safe open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (isSyncCompleted) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Synchronization Complete",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = syncStatusMessage ?: "All vault items are synchronized ✓",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (isInitiator) {
                    Text(
                        text = "Broadcasting on local Wi-Fi.\nEnter this 8-digit PIN on the target device to sync:",
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

                    syncStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.contains("Error") || msg.contains("rejected")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    if (selectedDevice != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Target Device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = selectedDevice!!.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (discoveredDevices.size > 1) {
                                    TextButton(onClick = { selectedDevice = null }) {
                                        Text("Change", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Enter the 8-digit PIN shown on ${selectedDevice!!.name}:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else if (discoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Select a nearby device to sync with:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            items(discoveredDevices) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedDevice = device }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${device.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Searching for nearby Safe devices...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Make sure the target device has the Safe sync dialog open on 'Display PIN'.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { inputPin = SyncPairingSession.normalizePinInput(it) },
                        label = { Text("8-Digit Sync PIN") },
                        placeholder = { Text("e.g. 49208173") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )

                    syncStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.contains("Error") || msg.contains("rejected")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isSyncing) {
                // In progress, no confirm button
            } else if (isSyncCompleted) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Done")
                }
            } else if (!isInitiator) {
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
                val isReadyToSync = inputPin.length == 8 && (selectedDevice != null || candidateHosts.isNotEmpty())

                Button(
                    enabled = isReadyToSync,
                    onClick = {
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
                        val targetDisplayName = selectedDevice?.name ?: candidateHosts.first()
                        isSyncing = true
                        isSyncCompleted = false
                        syncStatusMessage = "Reading local vault items..."
                        firebaseLog("SafeSync", "Client initiating sync to candidate hosts: $candidateHosts on ports $candidatePorts")
                        firebasePublishFlowEvent("SafeSync", "ClientSyncStarted", mapOf("targetHosts" to candidateHosts.joinToString(",")))
                        coroutineScope.launch {
                            try {
                                val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                                syncStatusMessage = "Analyzing vault checksums (${localItems.size} items)..."
                                val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
                                syncStatusMessage = "Preparing sync payload..."
                                val localJson = syncJson.encodeToString(SyncPayload(localItems, localTree.rootHash, currentDeviceName))
                                firebaseLog("SafeSync", "Client prepared payload: localItems=${localItems.size}, localRoot=${localTree.rootHash.take(8)}, payloadBytes=${localJson.length}")
                                firebasePublishFlowEvent("SafeSync", "ClientPreparedPayload", mapOf("localItems" to localItems.size.toString(), "bytes" to localJson.length.toString()))

                                var remoteJson: String? = null
                                var lastError: Exception? = null

                                outer@ for (port in candidatePorts) {
                                    for (host in candidateHosts) {
                                        try {
                                            remoteJson = networkService.sendPayloadToHost(
                                                hostAddress = host,
                                                port = port,
                                                pin = inputPin,
                                                localPayloadJson = localJson,
                                                onProgress = { step -> syncStatusMessage = step }
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

                                syncStatusMessage = "Reading remote vault response..."
                                val remotePayload = syncJson.decodeFromString<SyncPayload>(remoteJson)
                                val peerName = if (remotePayload.deviceName.isNotBlank()) {
                                    remotePayload.deviceName
                                } else if (selectedDevice != null && selectedDevice!!.name.isNotBlank()) {
                                    selectedDevice!!.name
                                } else {
                                    targetDisplayName
                                }
                                val stableId = "peer-${peerName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
                                Preferences.addPairedSyncDevice(stableId, peerName)
                                Preferences.setLastSyncTime()
                                firebaseLog("SafeSync", "Client registered paired peer: id=$stableId, name=$peerName, remoteItems=${remotePayload.items.size}")
                                firebasePublishFlowEvent("SafeSync", "ClientPeerPaired", mapOf("peerName" to peerName, "remoteItems" to remotePayload.items.size.toString()))

                                if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                                    firebaseLog("SafeSync", "Client: Merkle roots match (${localTree.rootHash.take(8)}). Already synchronized.")
                                    firebasePublishFlowEvent("SafeSync", "ClientAlreadySynced", mapOf("peer" to peerName, "root" to localTree.rootHash.take(8)))
                                    syncStatusMessage = "Sync complete: All ${localItems.size} items are in sync with $peerName ✓"
                                    isSyncCompleted = true
                                    onSyncCompleted()
                                } else {
                                    syncStatusMessage = "Analyzing differences with $peerName..."
                                    val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
                                    firebaseLog("SafeSync", "Client: Detected ${conflicts.size} conflicts, resolving...")
                                    if (conflicts.isNotEmpty()) {
                                        syncStatusMessage = "Applying ${conflicts.size} change(s) to vault..."
                                        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)
                                        val updatedLocalItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                                        syncStatusMessage = "Sync complete: Synchronized ${conflicts.size} change(s) with $peerName (${updatedLocalItems.size} items total) ✓"
                                    } else {
                                        syncStatusMessage = "Sync complete: All ${localItems.size} items are in sync with $peerName ✓"
                                    }
                                    isSyncCompleted = true
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
                    Text("Connect & Sync")
                }
            }
        },
        dismissButton = {
            if (isSyncing) {
                TextButton(
                    onClick = {},
                    enabled = false
                ) {
                    Text("Syncing...")
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text(if (isSyncCompleted) "Close" else "Cancel")
                }
            }
        }
    )
    }
}
