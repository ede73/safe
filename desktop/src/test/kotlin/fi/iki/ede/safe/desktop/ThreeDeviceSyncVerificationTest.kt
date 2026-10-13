@file:OptIn(kotlin.time.ExperimentalTime::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package fi.iki.ede.safe.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.cryptoobjects.plainName
import fi.iki.ede.cryptoobjects.plainDescription
import fi.iki.ede.cryptoobjects.plainUsername
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

class ThreeDeviceSyncVerificationTest {

    private val mediaDir = File("/Users/ede/.gemini/antigravity-ide/brain/e64e7243-40dc-4cd4-adee-1b0f55c20211/.tempmediaStorage")

    private fun unlockDb(db: DBHelper) {
        DBHelperFactory.initializeDatabase(db)
        val (salt, key) = db.fetchSaltAndEncryptedMasterKey()
        KeyStoreHelper.importExistingEncryptedMasterKey(SaltedPassword(salt, Password("12345678")), key)
    }

    private fun renderDeviceUI(db: DBHelper, title: String, outputFile: File) {
        unlockDb(db)
        val scene = ImageComposeScene(
            width = 600,
            height = 950,
            density = Density(1.5f)
        )

        scene.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DesktopColors.BackgroundGradientStart,
                                DesktopColors.BackgroundGradientMiddle,
                                DesktopColors.BackgroundGradientEnd
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight(0.96f)
                        .width(480.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DesktopColors.CardBackground.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    val categories = remember { db.fetchAllCategoryRows() }
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔒", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DesktopColors.Success.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "${categories.size} Categories",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = DesktopColors.Success,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(color = DesktopColors.Border, thickness = 1.dp)

                        Text(
                            "Categories & Passwords",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopColors.Secondary
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CategoryList(
                                categories = categories,
                                onCategoryClick = {},
                                onRenameCategory = { _, _ -> },
                                onDeleteCategory = {}
                            )
                        }
                    }
                }
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        if (data != null) {
            outputFile.writeBytes(data.bytes)
            println("Saved screenshot: ${outputFile.name}")
        }
    }

    private fun performP2PSync(dbA: DBHelper, dbB: DBHelper) {
        // Step A -> Read A items
        unlockDb(dbA)
        val itemsA = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val jsonA = Json.encodeToString(SyncPayload(itemsA))

        // Step B -> Read B items and resolve with A
        unlockDb(dbB)
        val itemsB = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val remotePayloadA = Json.decodeFromString<SyncPayload>(jsonA)
        val conflictsOnB = SyncConflictResolver.analyzeConflicts(itemsB, remotePayloadA.items)
        val autoResolvedOnB = conflictsOnB
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(autoResolvedOnB)

        // Updated B items back to A
        val updatedItemsB = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val jsonB = Json.encodeToString(SyncPayload(updatedItemsB))

        // Step A -> apply B's payload
        unlockDb(dbA)
        val freshItemsA = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        val remotePayloadB = Json.decodeFromString<SyncPayload>(jsonB)
        val conflictsOnA = SyncConflictResolver.analyzeConflicts(freshItemsA, remotePayloadB.items)
        val autoResolvedOnA = conflictsOnA
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(autoResolvedOnA)
    }

    @Test
    fun testThreeWayPeerSyncConvergence() {
        val rootDir = File("/Users/ede/src/safe")
        mediaDir.mkdirs()

        val seedDir = File(rootDir, "desktop/build/seed_dbs")
        
        // Copy seed databases into working test databases and clean stale WAL/SHM
        for (targetName in listOf("test_sync_android", "test_sync_ios", "test_sync_desktop")) {
            val srcPrefix = targetName.replace("test_sync_", "seed_")
            val srcFile = File(seedDir, "$srcPrefix.db")
            for (dir in listOf(rootDir, File(rootDir, "desktop")).filter { it.exists() }) {
                File(dir, "$targetName.db-wal").delete()
                File(dir, "$targetName.db-shm").delete()
                File(dir, "$targetName.db.lck").delete()
                srcFile.copyTo(File(dir, "$targetName.db"), overwrite = true)
            }
        }

        val dbAndroid = DBHelper(databaseName = "test_sync_android")
        val dbIos = DBHelper(databaseName = "test_sync_ios")
        val dbDesktop = DBHelper(databaseName = "test_sync_desktop")

        // --- STEP 1: Android <-> iOS Sync ---
        println("\n=== STEP 1: SYNCING ANDROID <-> IOS ===")
        performP2PSync(dbAndroid, dbIos)

        renderDeviceUI(dbAndroid, "Android (After iOS Sync)", File(mediaDir, "04_android_after_ios_sync.png"))
        renderDeviceUI(dbIos, "iOS (After Android Sync)", File(mediaDir, "05_ios_after_android_sync.png"))

        // --- STEP 2: Android <-> Desktop Sync ---
        println("\n=== STEP 2: SYNCING ANDROID <-> DESKTOP ===")
        performP2PSync(dbAndroid, dbDesktop)

        renderDeviceUI(dbAndroid, "Android (After Desktop Sync)", File(mediaDir, "06_android_after_desktop_sync.png"))
        renderDeviceUI(dbDesktop, "Desktop (After Android Sync)", File(mediaDir, "07_desktop_after_android_sync.png"))

        // --- STEP 3: Desktop <-> iOS Sync ---
        println("\n=== STEP 3: SYNCING DESKTOP <-> IOS ===")
        performP2PSync(dbDesktop, dbIos)

        // Propagate merged desktop entries back to Android
        performP2PSync(dbAndroid, dbDesktop)

        renderDeviceUI(dbIos, "iOS Final Converged", File(mediaDir, "08_ios_final_converged.png"))
        renderDeviceUI(dbDesktop, "Desktop Final Converged", File(mediaDir, "09_desktop_final_converged.png"))
        renderDeviceUI(dbAndroid, "Android Final Converged", File(mediaDir, "10_android_final_converged.png"))

        // --- VERIFY CONVERGENCE ON ALL 3 DATABASES ---
        unlockDb(dbAndroid)
        val androidCats = dbAndroid.fetchAllCategoryRows().map { it.plainName }.sorted()
        val androidEntries = dbAndroid.fetchAllRows(null).map { "${it.plainDescription}:${it.plainUsername}" }.sorted()
        val androidSoftDeleted = dbAndroid.fetchAllRows(null, softDeletedOnly = true).map { "${it.plainDescription}:${it.plainUsername}" }.sorted()

        unlockDb(dbIos)
        val iosCats = dbIos.fetchAllCategoryRows().map { it.plainName }.sorted()
        val iosEntries = dbIos.fetchAllRows(null).map { "${it.plainDescription}:${it.plainUsername}" }.sorted()
        val iosSoftDeleted = dbIos.fetchAllRows(null, softDeletedOnly = true).map { "${it.plainDescription}:${it.plainUsername}" }.sorted()

        unlockDb(dbDesktop)
        val desktopCats = dbDesktop.fetchAllCategoryRows().map { it.plainName }.sorted()
        val desktopEntries = dbDesktop.fetchAllRows(null).map { "${it.plainDescription}:${it.plainUsername}" }.sorted()
        val desktopSoftDeleted = dbDesktop.fetchAllRows(null, softDeletedOnly = true).map { "${it.plainDescription}:${it.plainUsername}" }.sorted()

        println("\n=== FINAL CONVERGENCE VERIFICATION ===")
        println("Android Categories (${androidCats.size}): $androidCats")
        println("iOS Categories (${iosCats.size}): $iosCats")
        println("Desktop Categories (${desktopCats.size}): $desktopCats")

        println("Android Active Entries Count: ${androidEntries.size}, Soft-Deleted: ${androidSoftDeleted.size}")
        println("iOS Active Entries Count: ${iosEntries.size}, Soft-Deleted: ${iosSoftDeleted.size}")
        println("Desktop Active Entries Count: ${desktopEntries.size}, Soft-Deleted: ${desktopSoftDeleted.size}")

        assertEquals(4, androidCats.size, "All 4 standard categories must be present")
        assertEquals(listOf("Finance", "Personal", "Social", "Work"), androidCats)
        assertEquals(androidCats, iosCats, "Android and iOS must have identical categories")
        assertEquals(androidCats, desktopCats, "Android and Desktop must have identical categories")

        assertEquals(13, androidEntries.size, "13 active reconciled entries")
        assertEquals(1, androidSoftDeleted.size, "1 soft-deleted tombstone in trash")
        assertEquals(14, androidEntries.size + androidSoftDeleted.size, "All 14 total records preserved across active + trash")

        assertEquals(androidEntries.size, iosEntries.size, "Android and iOS must have identical active entry count")
        assertEquals(androidEntries.size, desktopEntries.size, "Android and Desktop must have identical active entry count")
        assertEquals(androidEntries, iosEntries, "Android and iOS active entry datasets must match exactly")
        assertEquals(androidEntries, desktopEntries, "Android and Desktop active entry datasets must match exactly")

        assertEquals(androidSoftDeleted, iosSoftDeleted, "Android and iOS soft-deleted datasets must match exactly")
        assertEquals(androidSoftDeleted, desktopSoftDeleted, "Android and Desktop soft-deleted datasets must match exactly")

        // Cleanup test db connections
        dbAndroid.database.close()
        dbIos.database.close()
        dbDesktop.database.close()
    }
}
