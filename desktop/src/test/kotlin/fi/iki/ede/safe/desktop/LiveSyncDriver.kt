@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.cryptoobjects.plainName
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.ui.sync.SAFE_SYNC_DEFAULT_PORT
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncNetworkService
import fi.iki.ede.safe.ui.sync.SyncPayload
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File

@Disabled("Manual live integration driver class - requires running emulator and simulator")
class LiveSyncDriver {

    private fun unlockDb(db: DBHelper, password: String = "12345678") {
        KeyStoreHelper.ensureMockKeysLoaded()
        DBHelperFactory.initializeDatabase(db)
        val (salt, key) = db.fetchSaltAndEncryptedMasterKey()
        KeyStoreHelper.importExistingEncryptedMasterKey(SaltedPassword(salt, Password(password)), key)
    }

    @Test
    fun testProbeLiveIosServer() = runBlocking {
        println("=== PROBING LIVE IOS SERVER (port 58443) ===")
        val network = SyncNetworkService()
        val dummyPayload = SyncPayload(emptyList(), "testMerkle")
        val jsonPayload = Json.encodeToString(dummyPayload)
        try {
            val response = network.sendPayloadToHost("127.0.0.1", 58443, "DEFAULT_VAULT_CLUSTER", jsonPayload)
            println("=== SUCCESS! iOS Response: $response ===")
        } catch (e: Exception) {
            println("=== FAILED to sync with iOS: ${e::class.simpleName} : ${e.message} ===")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    @Disabled("Manual live integration driver only - requires running Android server")
    fun syncIosWithAndroidServer() = runBlocking {
        val pin = "75528133" // Live PIN from Android screen: 7552 - 8133
        println("=== LIVE SYNC: Connecting iOS DB to Android Server (PIN: $pin) ===")

        val rootDir = File("/Users/ede/src/safe")
        val iosContainerDir = File("/Users/ede/Library/Developer/CoreSimulator/Devices/39545553-5797-45DD-A6ED-F5D05D1641BB/data/Containers/Data/Application/21A020BA-3CD8-41EF-95F8-A62F04C4148E/Documents")
        val iosDbFile = File(iosContainerDir, "safe.db")
        require(iosDbFile.exists()) { "iOS db does not exist at $iosDbFile" }

        val iosDbCopy1 = File(rootDir, "live_ios.db")
        val iosDbCopy2 = File(rootDir, "desktop/live_ios.db")
        iosDbFile.copyTo(iosDbCopy1, overwrite = true)
        iosDbCopy1.copyTo(iosDbCopy2, overwrite = true)

        val dbIos = DBHelper(databaseName = "live_ios")
        unlockDb(dbIos)

        val iosItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val iosJson = Json.encodeToString(SyncPayload(iosItems))
        println("Sending ${iosItems.size} iOS items to Android server...")

        val network = SyncNetworkService()
        val responseJson = network.sendPayloadToHost(
            hostAddress = "127.0.0.1",
            port = SAFE_SYNC_DEFAULT_PORT,
            pin = pin,
            localPayloadJson = iosJson
        )
        println("Received response from Android server: ${responseJson.take(120)}...")

        val remotePayload = Json.decodeFromString<SyncPayload>(responseJson)
        val conflicts = SyncConflictResolver.analyzeConflicts(iosItems, remotePayload.items)
        val autoResolved = conflicts.map { c ->
            c.apply {
                selectedName = localItem?.name ?: remoteItem?.name ?: ""
                selectedUsername = localItem?.username ?: remoteItem?.username ?: ""
                selectedPassword = localItem?.password ?: remoteItem?.password ?: ""
                selectedNote = localItem?.note ?: remoteItem?.note ?: ""
                selectedIsDeleted = localItem?.isDeleted ?: remoteItem?.isDeleted ?: false
            }
        }
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(autoResolved)

        dbIos.database.close()
        iosDbCopy1.copyTo(iosDbFile, overwrite = true)
        println("Updated iOS database saved and deployed back to simulator!")
    }

    @Test
    @Disabled("Manual live integration driver only - requires running Android server")
    fun syncDesktopWithAndroidServer() = runBlocking {
        val pin = "75528133" // Live PIN from Android screen
        println("=== LIVE SYNC: Connecting Desktop DB to Android Server (PIN: $pin) ===")

        val rootDir = File("/Users/ede/src/safe")
        val seedFile = File(rootDir, "desktop/build/seed_dbs/seed_desktop.db")
        seedFile.copyTo(File(rootDir, "live_desktop.db"), overwrite = true)
        seedFile.copyTo(File(rootDir, "desktop/live_desktop.db"), overwrite = true)

        val dbDesktop = DBHelper(databaseName = "live_desktop")
        unlockDb(dbDesktop)

        val desktopItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val desktopJson = Json.encodeToString(SyncPayload(desktopItems))
        println("Sending ${desktopItems.size} Desktop items to Android server...")

        val network = SyncNetworkService()
        val responseJson = network.sendPayloadToHost(
            hostAddress = "127.0.0.1",
            port = SAFE_SYNC_DEFAULT_PORT,
            pin = pin,
            localPayloadJson = desktopJson
        )
        println("Received response from Android server: ${responseJson.take(120)}...")

        val remotePayload = Json.decodeFromString<SyncPayload>(responseJson)
        val conflicts = SyncConflictResolver.analyzeConflicts(desktopItems, remotePayload.items)
        val autoResolved = conflicts.map { c ->
            c.apply {
                selectedName = localItem?.name ?: remoteItem?.name ?: ""
                selectedUsername = localItem?.username ?: remoteItem?.username ?: ""
                selectedPassword = localItem?.password ?: remoteItem?.password ?: ""
                selectedNote = localItem?.note ?: remoteItem?.note ?: ""
                selectedIsDeleted = localItem?.isDeleted ?: remoteItem?.isDeleted ?: false
            }
        }
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(autoResolved)

        dbDesktop.database.close()
        File(rootDir, "live_desktop.db").copyTo(File(rootDir, "safe.db"), overwrite = true)
        File(rootDir, "live_desktop.db").copyTo(File(rootDir, "desktop/safe.db"), overwrite = true)
        println("Updated Desktop database deployed!")
    }

    @Test
    @Disabled("Manual live integration driver only - requires running iOS simulator")
    fun syncDesktopWithIos() = runBlocking {
        println("=== LIVE SYNC: Syncing Desktop DB with iOS DB ===")
        val rootDir = File("/Users/ede/src/safe")
        val iosContainerDir = File("/Users/ede/Library/Developer/CoreSimulator/Devices/39545553-5797-45DD-A6ED-F5D05D1641BB/data/Containers/Data/Application/21A020BA-3CD8-41EF-95F8-A62F04C4148E/Documents")
        val iosDbFile = File(iosContainerDir, "safe.db")

        iosDbFile.copyTo(File(rootDir, "live_ios.db"), overwrite = true)
        iosDbFile.copyTo(File(rootDir, "desktop/live_ios.db"), overwrite = true)

        val dbIos = DBHelper(databaseName = "live_ios")
        val dbDesktop = DBHelper(databaseName = "live_desktop")

        unlockDb(dbDesktop)
        val desktopItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val desktopJson = Json.encodeToString(SyncPayload(desktopItems))

        unlockDb(dbIos)
        val iosItems = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val remotePayloadDesk = Json.decodeFromString<SyncPayload>(desktopJson)
        val conflictsOnIos = SyncConflictResolver.analyzeConflicts(iosItems, remotePayloadDesk.items)
        val autoResolvedOnIos = conflictsOnIos.map { c ->
            c.apply {
                selectedName = localItem?.name ?: remoteItem?.name ?: ""
                selectedUsername = localItem?.username ?: remoteItem?.username ?: ""
                selectedPassword = localItem?.password ?: remoteItem?.password ?: ""
                selectedNote = localItem?.note ?: remoteItem?.note ?: ""
                selectedIsDeleted = localItem?.isDeleted ?: remoteItem?.isDeleted ?: false
            }
        }
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(autoResolvedOnIos)

        dbIos.database.close()
        dbDesktop.database.close()

        File(rootDir, "live_ios.db").copyTo(iosDbFile, overwrite = true)
        println("Final converged iOS database deployed to simulator!")
    }

    @Test
    @Disabled("Manual live integration driver only - requires running Android emulator and iOS simulator")
    fun testLiveAndroidAndIosSyncEndpoints() = runBlocking {
        val pin = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
        val payload = SyncPayload(emptyList(), "DUMMY_CHECK")
        val json = Json.encodeToString(payload)
        val network = SyncNetworkService()

        println("Probing Android Emulator on port 58445...")
        val androidResp = network.sendPayloadToHost("127.0.0.1", 58445, pin, json)
        println("Android response: ${androidResp.take(80)}")
        org.junit.jupiter.api.Assertions.assertTrue(androidResp.contains("items") || androidResp.contains("merkleRoot"))

        println("Probing iOS Simulator on port 58443...")
        val iosResp = network.sendPayloadToHost("127.0.0.1", 58443, pin, json)
        println("iOS response: ${iosResp.take(80)}")
        org.junit.jupiter.api.Assertions.assertTrue(iosResp.contains("items") || iosResp.contains("merkleRoot"))
    }

    @Test
    @Disabled("Manual live integration driver only - requires running Android emulator and iOS simulator")
    fun testLive3WayConvergence() = runBlocking {
        val pin = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
        val network = SyncNetworkService()

        println("Fetching state from Desktop (port 58444)...")
        val probePayload = SyncPayload(emptyList(), "DIFF_PROBE")
        val deskRespJson = network.sendPayloadToHost("127.0.0.1", 58444, pin, Json.encodeToString(probePayload))
        val deskState = Json.decodeFromString<SyncPayload>(deskRespJson)
        val currentCount = deskState.items.size
        println("Desktop currently has $currentCount items")

        val newItem = fi.iki.ede.safe.ui.sync.SyncItem(
            cxfItemId = "item_three_device_proof",
            name = "ThreeDeviceLiveProof",
            username = "sync_proof_user",
            password = "SecretProofPassword2026!",
            url = "https://safe.local/proof",
            note = "Synced live across Desktop, iOS and Android",
            categoryName = "Work",
            modifiedAt = System.currentTimeMillis(),
            isDeleted = false
        )

        val updatedItems = deskState.items.filter { it.cxfItemId != newItem.cxfItemId } + newItem
        val payloadWithNewItem = SyncPayload(updatedItems, "NEW_ITEM_TRIGGER")

        println("Injecting new item to Desktop (port 58444)...")
        val sendResp = network.sendPayloadToHost("127.0.0.1", 58444, pin, Json.encodeToString(payloadWithNewItem))
        println("Desktop received: ${sendResp.take(80)}...")

        println("Propagating item to iOS Simulator (port 58443)...")
        val iosResp = network.sendPayloadToHost("127.0.0.1", 58443, pin, Json.encodeToString(payloadWithNewItem))
        println("iOS response: ${iosResp.take(80)}...")

        println("Propagating item to Android Emulator (port 58445)...")
        val androidResp = network.sendPayloadToHost("127.0.0.1", 58445, pin, Json.encodeToString(payloadWithNewItem))
        println("Android response: ${androidResp.take(80)}...")

        // Propagate complete set (including any Android-unique items) to Desktop and iOS
        println("Reconciling complete converged item set to Desktop and iOS...")
        network.sendPayloadToHost("127.0.0.1", 58444, pin, androidResp)
        network.sendPayloadToHost("127.0.0.1", 58443, pin, androidResp)

        println("Verifying final states across all 3 platforms...")
        val finalDesk = Json.decodeFromString<SyncPayload>(network.sendPayloadToHost("127.0.0.1", 58444, pin, Json.encodeToString(probePayload)))
        val finalIos = Json.decodeFromString<SyncPayload>(network.sendPayloadToHost("127.0.0.1", 58443, pin, Json.encodeToString(probePayload)))
        val finalAndroid = Json.decodeFromString<SyncPayload>(network.sendPayloadToHost("127.0.0.1", 58445, pin, Json.encodeToString(probePayload)))

        println("Desktop item count: ${finalDesk.items.size}")
        println("iOS item count:     ${finalIos.items.size}")
        println("Android item count: ${finalAndroid.items.size}")

        val diffDesk = finalAndroid.items.map { it.name } - finalDesk.items.map { it.name }.toSet()
        println("Items on Android missing on Desktop: $diffDesk")
        println("Android items: ${finalAndroid.items.map { "${it.cxfItemId}:${it.name}" }}")
        println("Desktop items: ${finalDesk.items.map { "${it.cxfItemId}:${it.name}" }}")

        org.junit.jupiter.api.Assertions.assertTrue(finalDesk.items.any { it.name == "ThreeDeviceLiveProof" })
        org.junit.jupiter.api.Assertions.assertTrue(finalIos.items.any { it.name == "ThreeDeviceLiveProof" })
        org.junit.jupiter.api.Assertions.assertTrue(finalAndroid.items.any { it.name == "ThreeDeviceLiveProof" })
        org.junit.jupiter.api.Assertions.assertEquals(finalDesk.items.size, finalIos.items.size)
        org.junit.jupiter.api.Assertions.assertEquals(finalIos.items.size, finalAndroid.items.size)
        println("=== ALL 3 DEVICES IN EXACT PARITY AND CONVERGED! ===")
    }
}

