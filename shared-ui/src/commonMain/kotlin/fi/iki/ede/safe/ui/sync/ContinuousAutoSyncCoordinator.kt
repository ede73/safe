package fi.iki.ede.safe.ui.sync

import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.notifications.showToast
import fi.iki.ede.preferences.Preferences
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
import kotlin.time.Clock

private const val TAG = "SafeAutoSync"

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
            Logger.e(TAG, "Coroutine exception: ${t.message}", t)
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
        } catch (_: Throwable) {}
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
     * Starts continuous auto-discovery, advertising, and heartbeat sync.
     */
    fun start(
        deviceName: String = DeviceInfoProvider.getFormattedDeviceName(),
        authToken: String,
        heartbeatIntervalMs: Long = 5000L
    ) {
        if (isRunning) return
        if (authToken.isBlank()) return
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
            Logger.e(TAG, "Error starting auto-sync server", e)
        }

        val actualPort = if (networkService.boundPort > 0) networkService.boundPort else SAFE_SYNC_DEFAULT_PORT

        // 2. Start advertising and discovery on local network
        try {
            syncManager.startAdvertising(effectiveName, actualPort)
            syncManager.startDiscovery(
                onDeviceFound = { device ->
                    val isSelf = device.name.equals(effectiveName, ignoreCase = true) ||
                            ((device.hostAddress == "127.0.0.1" || device.hostAddress == "localhost") &&
                                    (device.port == networkService.boundPort || device.port == SAFE_SYNC_DEFAULT_PORT))
                    if (!isSelf) {
                        registerPairedPeer(device)
                    }
                },
                onDeviceLost = { device ->
                    unregisterPairedPeer(device.id)
                }
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error starting auto-sync discovery/advertising", e)
        }

        // 3. Heartbeat loop to keep state synchronized continuously
        backgroundJob = scope.launch {
            while (isActive && isRunning) {
                delay(heartbeatIntervalMs)
                if (Preferences.isAutoSyncWifiEnabled()) {
                    val uniquePeers = getCandidatePeers()
                    for (peer in uniquePeers) {
                        try {
                            syncWithPeer(peer, authToken)
                        } catch (_: Exception) {}
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
            val remotePayload = syncJson.decodeFromString<SyncPayload>(remotePayloadJson)
            val peerDeviceName = remotePayload.deviceName.ifBlank { "Safe Peer" }
            val stableId = "peer-${peerDeviceName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
            Preferences.addPairedSyncDevice(stableId, peerDeviceName)
            Preferences.setLastSyncTime()
            val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val localTree = MerkleTreeHelper.buildMerkleVault(localItems)
            val myName = DeviceInfoProvider.getFormattedDeviceName()
            if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                lastStatusMessage = "In parity with $peerDeviceName ✓"
                return syncJson.encodeToString(SyncPayload(emptyList(), localTree.rootHash, myName))
            }
            val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
            SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)
            if (conflicts.isNotEmpty()) {
                try {
                    onDataMutatedBySync?.invoke()
                } catch (e: Throwable) {
                    Logger.e(TAG, "Error invoking onDataMutatedBySync callback", e)
                }
            }
            val updatedLocalItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val updatedTree = MerkleTreeHelper.buildMerkleVault(updatedLocalItems)
            lastStatusMessage = "Synchronized with $peerDeviceName"
            updateStatus()
            syncJson.encodeToString(SyncPayload(updatedLocalItems, updatedTree.rootHash, myName))
        } catch (t: Throwable) {
            Logger.e(TAG, "Error in handleServerPayload", t)
            throw t
        }
    }

    /**
     * Pauses the background server, discovery, and advertising (e.g. while PIN pairing dialog is active).
     */
    fun pauseServer() {
        try {
            networkService.stopServer()
            syncManager.stopAdvertising()
            syncManager.stopDiscovery()
        } catch (_: Exception) {}
    }

    /**
     * Resumes the background server, discovery, and advertising after PIN pairing dialog closes.
     */
    fun resumeServer() {
        if (!isRunning || !Preferences.isAutoSyncWifiEnabled() || currentAuthToken.isBlank()) return
        try {
            networkService.startServer(
                port = SAFE_SYNC_DEFAULT_PORT,
                pin = currentAuthToken,
                onPayloadReceived = ::handleServerPayload
            )
            val actualPort = if (networkService.boundPort > 0) networkService.boundPort else SAFE_SYNC_DEFAULT_PORT
            syncManager.startAdvertising(currentDeviceName, actualPort)
            syncManager.startDiscovery(
                onDeviceFound = { device ->
                    val isSelf = device.name.equals(currentDeviceName, ignoreCase = true) ||
                            ((device.hostAddress == "127.0.0.1" || device.hostAddress == "localhost") &&
                                    (device.port == networkService.boundPort || device.port == SAFE_SYNC_DEFAULT_PORT))
                    if (!isSelf) {
                        registerPairedPeer(device)
                    }
                },
                onDeviceLost = { device ->
                    unregisterPairedPeer(device.id)
                }
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
        try {
            syncManager.stopAdvertising()
            syncManager.stopDiscovery()
        } catch (_: Exception) {}
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
        if (authToken.isBlank() || !Preferences.isAutoSyncWifiEnabled()) return
        if (!isRunning && Preferences.getPairedSyncDevices().isNotEmpty()) {
            start(authToken = authToken)
        }
        if (!isRunning) return
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
                Logger.e(TAG, "Error triggering onLocalDataChanged sync", t)
            }
        }
    }

    /**
     * Executes single Merkle-based sync step with a discovered peer.
     * Returns true if peer is verified to be in exact parity.
     */
    suspend fun syncWithPeer(peer: DiscoveredDevice, authToken: String): Boolean {
        return try {
            val localItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
            val localTree = MerkleTreeHelper.buildMerkleVault(localItems)

            val myName = DeviceInfoProvider.getFormattedDeviceName()
            val payload = SyncPayload(localItems, localTree.rootHash, myName)
            val json = syncJson.encodeToString(payload)

            val responseJson = networkService.sendPayloadToHost(
                hostAddress = peer.hostAddress,
                port = peer.port,
                pin = authToken,
                localPayloadJson = json
            )

            val remotePayload = syncJson.decodeFromString<SyncPayload>(responseJson)
            val effectivePeerName = remotePayload.deviceName.ifBlank { peer.name }
            val stableId = if (remotePayload.deviceName.isNotBlank()) {
                "peer-${remotePayload.deviceName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
            } else {
                peer.id
            }
            if (remotePayload.merkleRoot.isNotBlank() && remotePayload.merkleRoot == localTree.rootHash) {
                peerRootHashes[peer.id] = localTree.rootHash
                lastSyncTimestamp = Clock.System.now().toEpochMilliseconds()
                lastStatusMessage = "Peer $effectivePeerName synchronized (Merkle Root match) ✓"
                Preferences.addPairedSyncDevice(stableId, effectivePeerName)
                Preferences.setLastSyncTime()
                updateStatus()
                true
            } else {
                val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remotePayload.items)
                SyncConflictResolver.applyResolvedConflictsToLocalDatabase(conflicts)

                val updatedItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
                val updatedTree = MerkleTreeHelper.buildMerkleVault(updatedItems)
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
                        Logger.e(TAG, "Error invoking onDataMutatedBySync after conflict resolution", e)
                    }
                }
                updateStatus()
                true
            }
        } catch (e: Throwable) {
            firebaseRecordException("SafeAutoSync failed: ${e::class.simpleName}", e)
            lastStatusMessage = "Auto-sync with ${peer.name} failed: ${e.message}"
            updateStatus()
            false
        }
    }
}
