package fi.iki.ede.safe.ui.sync

import fi.iki.ede.notifications.showToast
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class AutoSyncStatus(
    val isRunning: Boolean,
    val localRootHash: String,
    val connectedPeersCount: Int,
    val lastSyncTimestamp: Long,
    val lastStatusMessage: String
)

/**
 * Continuous Auto-Sync Coordinator.
 * Periodically monitors peer Merkle roots over local network and triggers
 * silent fast-forward or reconciliation when data drift is detected.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class ContinuousAutoSyncCoordinator(
    private val networkService: SyncNetworkService = SyncNetworkService(),
    private val syncManager: LocalDeviceSyncManager = LocalDeviceSyncManager(),
    private val scope: CoroutineScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, t ->
            println("ContinuousAutoSyncCoordinator coroutine exception: ${t.message}")
            t.printStackTrace()
        }
    )
) {
    companion object {
        val instance by lazy { ContinuousAutoSyncCoordinator() }
    }

    private var backgroundJob: Job? = null
    @kotlin.concurrent.Volatile
    var isRunning = false
        private set

    private var currentDeviceName: String = "Safe Device"
    private var currentAuthToken: String = ""

    private val discoveredPeers = mutableMapOf<String, DiscoveredDevice>()
    private val peerRootHashes = mutableMapOf<String, String>()

    var lastSyncTimestamp: Long = 0L
        private set
    var lastStatusMessage: String = "Idle"
        private set
    var onDataMutatedBySync: (() -> Unit)? = null

    private val _statusFlow = MutableStateFlow(
        AutoSyncStatus(
            isRunning = false,
            localRootHash = "",
            connectedPeersCount = 0,
            lastSyncTimestamp = 0L,
            lastStatusMessage = "Idle"
        )
    )
    val statusFlow: StateFlow<AutoSyncStatus> = _statusFlow.asStateFlow()

    private fun updateStatus() {
        try {
            val currentItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val currentTree = MerkleTreeHelper.buildMerkleVault(currentItems)
            val newStatus = AutoSyncStatus(
                isRunning = isRunning,
                localRootHash = currentTree.rootHash,
                connectedPeersCount = discoveredPeers.size,
                lastSyncTimestamp = lastSyncTimestamp,
                lastStatusMessage = lastStatusMessage
            )
            _statusFlow.value = newStatus
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun getStatus(): AutoSyncStatus {
        return try {
            val currentItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val currentTree = MerkleTreeHelper.buildMerkleVault(currentItems)
            AutoSyncStatus(
                isRunning = isRunning,
                localRootHash = currentTree.rootHash,
                connectedPeersCount = discoveredPeers.size,
                lastSyncTimestamp = lastSyncTimestamp,
                lastStatusMessage = lastStatusMessage
            )
        } catch (_: Throwable) {
            _statusFlow.value
        }
    }

    fun registerPairedPeer(device: DiscoveredDevice) {
        discoveredPeers[device.id] = device
        updateStatus()
    }

    fun unregisterPairedPeer(deviceId: String) {
        discoveredPeers.remove(deviceId)
        peerRootHashes.remove(deviceId)
        updateStatus()
    }

    /**
     * Starts continuous auto-discovery and heartbeat sync.
     */
    /**
     * Starts continuous auto-discovery and heartbeat sync.
     */
    fun start(deviceName: String = DeviceInfoProvider.getFormattedDeviceName(), authToken: String, heartbeatIntervalMs: Long = 5000L) {
        if (isRunning) return
        if (!Preferences.isAutoSyncWifiEnabled()) return
        if (Preferences.getPairedSyncDevices().isEmpty()) return
        isRunning = true

        val effectiveName = if (deviceName.isNotBlank() &&
            deviceName != "Android Device" &&
            deviceName != "iOS Device" &&
            deviceName != "Desktop Client"
        ) {
            deviceName
        } else {
            DeviceInfoProvider.getFormattedDeviceName()
        }
        currentDeviceName = effectiveName
        currentAuthToken = authToken

        // 1. Start server listener for incoming peer sync requests
        try {
            networkService.startServer(
                port = SAFE_SYNC_DEFAULT_PORT,
                pin = authToken,
                onPayloadReceived = ::handleServerPayload
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Continuous auto-sync NEVER runs mDNS discovery on its own.
        // Discovery is strictly user-consented and only initiated when the user explicitly
        // opens the Device Sync Dialog from the menu.

        // 3. Heartbeat loop to keep state synchronized continuously
        backgroundJob = scope.launch {
            while (isActive && isRunning) {
                delay(heartbeatIntervalMs)
                if (Preferences.isAutoSyncWifiEnabled()) {
                    val uniquePeers = getCandidatePeers()
                    for (peer in uniquePeers) {
                        try {
                            syncWithPeer(peer, authToken)
                        } catch (_: Exception) {
                            // Ignored: peer not reachable on this endpoint
                        }
                    }
                }
            }
        }
        updateStatus()
    }

    private fun getCandidatePeers(): List<DiscoveredDevice> {
        val candidatePeers = mutableListOf<DiscoveredDevice>()
        candidatePeers.addAll(discoveredPeers.values)

        // Only add local emulator bridging if no peers were discovered over Wi-Fi
        if (candidatePeers.isEmpty() && networkService.isAndroidGuest) {
            for (port in listOf(58443, 58444)) {
                val candidateName = if (port == 58443) "iOS Safe" else "Desktop Safe"
                candidatePeers.add(
                    DiscoveredDevice(
                        id = "gateway-$port",
                        name = candidateName,
                        hostAddress = "10.0.2.2",
                        port = port
                    )
                )
            }
        }

        val myPort = networkService.boundPort
        return candidatePeers.distinctBy { "${it.hostAddress}:${it.port}" }.filter { peer ->
            !(peer.port == myPort && (peer.hostAddress == "127.0.0.1" || peer.hostAddress == "localhost"))
        }
    }

    private fun handleServerPayload(remotePayloadJson: String): String {
        return try {
            val remotePayload = Json.decodeFromString<SyncPayload>(remotePayloadJson)
            val peerDeviceName = remotePayload.deviceName.ifBlank { "Safe Peer" }
            val stableId = remotePayload.deviceId.ifBlank { "peer-${peerDeviceName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}" }
            Preferences.addPairedSyncDevice(stableId, peerDeviceName)
            Preferences.setLastSyncTime()
            val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
            val myName = DeviceInfoProvider.getFormattedDeviceName()
            if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                lastStatusMessage = "In parity with $peerDeviceName ✓"
                return Json.encodeToString(SyncPayload(emptyList(), localTree.rootHash, myName, Preferences.getLocalDeviceId()))
            }
            val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
            SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)
            if (conflicts.isNotEmpty()) {
                try {
                    onDataMutatedBySync?.invoke()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            val updatedLocalItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val updatedTree = MerkleTreeHelper.buildMerkleVault(updatedLocalItems)
            lastStatusMessage = "Synchronized with $peerDeviceName"
            updateStatus()
            Json.encodeToString(SyncPayload(updatedLocalItems, updatedTree.rootHash, myName, Preferences.getLocalDeviceId()))
        } catch (t: Throwable) {
            t.printStackTrace()
            val myName = DeviceInfoProvider.getFormattedDeviceName()
            val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
            Json.encodeToString(SyncPayload(localItems, localTree.rootHash, myName, Preferences.getLocalDeviceId()))
        }
    }

    /**
     * Pauses the background server and advertising (e.g. while PIN pairing dialog is active).
     */
    fun pauseServer() {
        try {
            networkService.stopServer()
            syncManager.stopAdvertising()
        } catch (_: Exception) {}
    }

    /**
     * Resumes the background server and advertising after PIN pairing dialog closes.
     */
    fun resumeServer() {
        if (!isRunning || !Preferences.isAutoSyncWifiEnabled()) return
        try {
            networkService.startServer(
                port = SAFE_SYNC_DEFAULT_PORT,
                pin = currentAuthToken,
                onPayloadReceived = ::handleServerPayload
            )
        } catch (_: Exception) {}
    }

    /**
     * Stops continuous auto-sync.
     */
    fun stop() {
        isRunning = false
        backgroundJob?.cancel()
        backgroundJob = null
        try {
            networkService.stopServer()
        } catch (_: Exception) {}
        syncManager.stopAdvertising()
        syncManager.stopDiscovery()
        discoveredPeers.clear()
        peerRootHashes.clear()
        lastStatusMessage = "Stopped"
        updateStatus()
    }

    /**
     * Called whenever a local database mutation occurs (add, edit, delete).
     * Immediately triggers sync with all currently connected peers.
     */
    fun onLocalDataChanged(authToken: String) {
        if (!isRunning || !Preferences.isAutoSyncWifiEnabled()) return
        scope.launch {
            try {
                val uniquePeers = getCandidatePeers()
                for (peer in uniquePeers) {
                    try {
                        syncWithPeer(peer, authToken)
                    } catch (_: Throwable) {}
                }
                updateStatus()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * Executes single Merkle-based sync step with a discovered peer.
     * Returns true if peer is verified to be in exact parity.
     */
    suspend fun syncWithPeer(peer: DiscoveredDevice, authToken: String): Boolean {
        return try {
            firebaseLog("SafeAutoSync", "Attempting syncWithPeer: ${peer.name} at ${peer.hostAddress}:${peer.port}")
            val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val localTree = MerkleTreeHelper.buildMerkleVault(localItems)

            // Fast-path check: If we already know peer matches our local root, skip network roundtrip
            if (peerRootHashes[peer.id] == localTree.rootHash) {
                firebaseLog("SafeAutoSync", "Peer ${peer.name} cached root matches local root (${localTree.rootHash.take(8)}). Skipping roundtrip.")
                lastStatusMessage = "Peer ${peer.name} already in parity ✓"
                updateStatus()
                return true
            }

            val myName = DeviceInfoProvider.getFormattedDeviceName()
            val payload = SyncPayload(localItems, localTree.rootHash, myName, Preferences.getLocalDeviceId())
            val json = Json.encodeToString(payload)

            val responseJson = networkService.sendPayloadToHost(
                hostAddress = peer.hostAddress,
                port = peer.port,
                pin = authToken,
                localPayloadJson = json
            )

            val remotePayload = Json.decodeFromString<SyncPayload>(responseJson)
            val effectivePeerName = remotePayload.deviceName.ifBlank { peer.name }
            val stableId = remotePayload.deviceId.ifBlank {
                if (remotePayload.deviceName.isNotBlank()) {
                    "peer-${remotePayload.deviceName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
                } else {
                    peer.id
                }
            }
            if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                // Peer confirms roots are identical!
                firebaseLog("SafeAutoSync", "Peer $effectivePeerName confirmed roots match (${localTree.rootHash.take(8)})")
                peerRootHashes[peer.id] = localTree.rootHash
                lastSyncTimestamp = Clock.System.now().toEpochMilliseconds()
                lastStatusMessage = "Peer $effectivePeerName synchronized (Merkle Root match) ✓"
                Preferences.addPairedSyncDevice(stableId, effectivePeerName)
                Preferences.setLastSyncTime()
                updateStatus()
                true
            } else {
                // Roots differ -> apply incoming items / reconciliation
                val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
                firebaseLog("SafeAutoSync", "Peer $effectivePeerName roots differ. Applying ${conflicts.size} conflicts...")
                SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)

                val updatedItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                val updatedTree = MerkleTreeHelper.buildMerkleVault(updatedItems)
                firebaseLog("SafeAutoSync", "Peer $effectivePeerName converged to root ${updatedTree.rootHash.take(8)}")
                peerRootHashes[peer.id] = updatedTree.rootHash
                lastSyncTimestamp = Clock.System.now().toEpochMilliseconds()
                lastStatusMessage = "Peer $effectivePeerName reconciled & converged to ${updatedTree.rootHash.take(8)} ✓"
                Preferences.addPairedSyncDevice(stableId, effectivePeerName)
                Preferences.setLastSyncTime()
                if (conflicts.isNotEmpty()) {
                    showToast("Vault synchronized with $effectivePeerName")
                    try {
                        onDataMutatedBySync?.invoke()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                updateStatus()
                true
            }
        } catch (e: Throwable) {
            firebaseRecordException("SafeAutoSync failed with peer ${peer.name}: ${e::class.simpleName}", e)
            lastStatusMessage = "Auto-sync with ${peer.name} failed: ${e.message}"
            updateStatus()
            false
        }
    }
}
