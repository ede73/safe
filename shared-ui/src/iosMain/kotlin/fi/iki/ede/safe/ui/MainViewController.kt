@file:OptIn(
    kotlin.time.ExperimentalTime::class,
    kotlin.experimental.ExperimentalNativeApi::class
)

package fi.iki.ede.safe.ui

import androidx.compose.foundation.background
import kotlin.time.Clock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import fi.iki.ede.backup.RestorationProgress
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.composable.SiteEntryList
import fi.iki.ede.safe.ui.composable.getString
import fi.iki.ede.safe.ui.composable.SiteEntryView
import fi.iki.ede.safe.ui.composable.AddOrEditCategory
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.composable.BottomActionBarForSiteEntryView
import fi.iki.ede.safe.ui.composable.PopCustomPasswordDialog
import fi.iki.ede.safe.ui.composable.ConflictResolutionDialog
import fi.iki.ede.safe.ui.composable.DeviceSyncDialog
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncConflict
import platform.UIKit.UIViewController
import fi.iki.ede.crypto.support.encrypt
import kotlinx.coroutines.launch
import fi.iki.ede.safe.ui.utils.streamSortedSiteEntries
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import fi.iki.ede.safe.ui.composable.AskBackupPasswordAndCommence
import fi.iki.ede.safe.ui.composable.RestoreDatabaseComponent
import fi.iki.ede.safe.ui.composable.SharedBottomActionBar
import fi.iki.ede.safe.ui.composable.SettingsScreen
import fi.iki.ede.safe.ui.composable.ExtensionsEditor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import fi.iki.ede.safe.ui.sync.ContinuousAutoSyncCoordinator
import fi.iki.ede.safe.ui.sync.DeviceInfoProvider
import fi.iki.ede.backup.RestoreDatabase
import fi.iki.ede.db.SiteEntryGPMJoin
import kotlinx.coroutines.runBlocking
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.Foundation.NSURL
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.valueForKey
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy
import androidx.compose.ui.draw.rotate
import okio.Path.Companion.toPath

@OptIn(ExperimentalMaterial3Api::class)
fun MainViewController(): UIViewController {
    kotlin.native.setUnhandledExceptionHook { throwable ->
        println("UNCAUGHT EXCEPTION ON IOS: ${throwable.message}")
        throwable.printStackTrace()
    }

    lateinit var viewController: UIViewController
    viewController = ComposeUIViewController {
    if (!Preferences.isDataStoreInitialized()) {
        Preferences.initialize()
    }

    val db = remember { DBHelperFactory.getDBHelper() }
    val coroutineScope = rememberCoroutineScope()

    // State checking if vault is created in DB
    var isFirstTimeLogin by remember {
        val (salt, cipheredMasterKey) = try {
            db.fetchSaltAndEncryptedMasterKey()
        } catch (e: Exception) {
            Salt.getEmpty() to IVCipherText.getEmpty()
        }
        mutableStateOf(salt.isEmpty() || cipheredMasterKey.isEmpty())
    }

    var isLoggedIn by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf(if (isFirstTimeLogin) "Create your new vault" else "Enter master password to unlock") }

    // Main App Navigation States
    var activeCategory by remember { mutableStateOf<DecryptableCategoryEntry?>(null) }
    var activeSiteEntry by remember { mutableStateOf<DecryptableSiteEntry?>(null) }
    var editingSiteDesc by remember { mutableStateOf("") }
    var editingSiteUser by remember { mutableStateOf("") }
    var editingSitePass by remember { mutableStateOf("") }
    var editingSiteNote by remember { mutableStateOf("") }
    var editingSiteUrl by remember { mutableStateOf("") }

    LaunchedEffect(activeSiteEntry) {
        val entry = activeSiteEntry
        if (entry != null) {
            editingSiteDesc = entry.plainDescription
            editingSiteUser = entry.plainUsername
            editingSitePass = entry.plainPassword
            editingSiteNote = entry.plainNote
            editingSiteUrl = entry.plainWebsite
        }
    }

    var refreshTrigger by remember { mutableStateOf(0) }

    val saveActiveSiteEntry: () -> Unit = {
        val siteEntry = activeSiteEntry
        if (siteEntry != null) {
            try {
                siteEntry.description = editingSiteDesc.encrypt()
                siteEntry.username = editingSiteUser.encrypt()
                siteEntry.password = editingSitePass.encrypt()
                siteEntry.note = editingSiteNote.encrypt()
                siteEntry.website = editingSiteUrl.encrypt()
                siteEntry.clearCachedDescription()
                if (siteEntry.id == null) {
                    val newId = db.addSiteEntry(siteEntry)
                    siteEntry.id = newId
                } else {
                    db.updateSiteEntry(siteEntry)
                }
                val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(clusterSecret)
                activeSiteEntry = null
                refreshTrigger++
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    // Dialog state for adding Category
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showImportExportChoiceDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showDeviceSyncDialog by remember { mutableStateOf(false) }
    var activeConflicts by remember { mutableStateOf<List<SyncConflict>>(emptyList()) }
    val syncStatus by ContinuousAutoSyncCoordinator.instance.statusFlow.collectAsState()

    // Settings Screen states
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showExtensionsEditor by remember { mutableStateOf(false) }

    // Backup restore states
    var importedBackupXml by remember { mutableStateOf<ByteArray?>(null) }
    var activeDelegate by remember { mutableStateOf<Any?>(null) }

    val launchDocumentPicker = remember {
        { onFilePicked: (ByteArray) -> Unit ->
            val delegate = XMLDocumentPickerDelegate { content ->
                activeDelegate = null
                onFilePicked(content)
            }
            activeDelegate = delegate
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("public.xml", "public.text", "public.data"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
            )
            picker.delegate = delegate
            viewController.presentViewController(picker, animated = true, completion = null)
        }
    }

    val shareBackupFile = remember {
        { bytes: ByteArray, filename: String ->
            val tempPath = (platform.Foundation.NSTemporaryDirectory() + "/" + filename).toPath()
            okio.FileSystem.SYSTEM.write(tempPath) {
                write(bytes)
            }
            val fileURL = platform.Foundation.NSURL.fileURLWithPath(tempPath.toString())
            val activityVC = platform.UIKit.UIActivityViewController(
                activityItems = listOf(fileURL),
                applicationActivities = null
            )
            val popover = activityVC.valueForKey("popoverPresentationController") as? platform.darwin.NSObject
            popover?.setValue(viewController.view, forKey = "sourceView")
            viewController.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    val categories by remember(refreshTrigger, isLoggedIn) {
        flow {
            if (isLoggedIn) {
                val cats = withContext(Dispatchers.Default) {
                    db.fetchAllCategoryRows()
                }
                emit(cats)
            } else {
                emit(emptyList())
            }
        }
    }.collectAsState(initial = emptyList())

    val siteEntries by remember(refreshTrigger, activeCategory, isLoggedIn) {
        flow {
            if (isLoggedIn && activeCategory != null) {
                val catId = activeCategory!!.id
                val rows = withContext(Dispatchers.Default) {
                    db.fetchAllRows(catId)
                }
                emit(rows)
            } else {
                emit(emptyList())
            }
        }.streamSortedSiteEntries(activeCategory?.id)
    }.collectAsState(initial = emptyList())

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFe94560),
            background = Color(0xFF1a1a2e),
            surface = Color(0xFF16213e)
        )
    ) {
        if (showSettingsScreen) {
            SettingsScreen(
                onBack = { showSettingsScreen = false },
                onEditExtensions = { showExtensionsEditor = true },
                onManualSyncTriggered = {
                    showDeviceSyncDialog = true
                }
            )
        } else if (importedBackupXml != null) {
            var currentRestoreScreenState by remember { mutableStateOf("askBackupPassword") }
            var backupPasswordInput by remember { mutableStateOf(Password.getEmpty()) }
            val processedPasswords = remember { mutableIntStateOf(0) }
            val processedCategories = remember { mutableIntStateOf(0) }
            val processedMessage = remember { mutableStateOf<RestorationProgress?>(null) }

            when (currentRestoreScreenState) {
                "askBackupPassword" -> {
                    AskBackupPasswordAndCommence(
                        processedPasswords = processedPasswords,
                        processedCategories = processedCategories,
                        processedMessage = processedMessage,
                        selectedDocName = "Backup XML File",
                    ) { password ->
                        backupPasswordInput = password
                        currentRestoreScreenState = "restoration"
                    }
                }
                "restoration" -> {
                    RestoreDatabaseComponent(
                        processedPasswords = processedPasswords,
                        processedCategories = processedCategories,
                        processedMessage = processedMessage,
                        backupPassword = backupPasswordInput,
                        backupSource = okio.Buffer().write(importedBackupXml!!),
                        passwordLogin = { password ->
                            val (salt, encryptedKey) = db.fetchSaltAndEncryptedMasterKey()
                            val saltedPassword = SaltedPassword(salt, password)
                            KeyStoreHelper.importExistingEncryptedMasterKey(saltedPassword, encryptedKey)
                            true
                        },
                        linkSaveGPMAndSiteEntry = { siteId, gpmId ->
                            runBlocking {
                                db.database.siteEntryGPMJoinDao().insert(SiteEntryGPMJoin(siteId, gpmId))
                            }
                        },
                        addSavedGPM = { savedGpm ->
                            runBlocking {
                                db.database.gpmDao().insert(savedGpm)
                            }
                        },
                        onFinished = { count, ex ->
                            backupPasswordInput = Password.getEmpty()
                            if (ex == null) {
                                importedBackupXml = null
                                isFirstTimeLogin = false
                                isLoggedIn = true
                                refreshTrigger++
                            } else {
                                currentRestoreScreenState = "askBackupPassword"
                            }
                        },
                        verifyUserWantForOldBackup = { _, _ -> true }
                    )
                }
            }
        } else if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1a1a2e),
                                Color(0xFF16213e),
                                Color(0xFF0f172a)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                SharedLoginScreen(
                    isFirstTimeLogin = isFirstTimeLogin,
                    isBiometricsEnabled = false,
                    statusMessage = statusMessage,
                    onCreateVault = { pwd, _ ->
                        try {
                            val (salt, cipheredKey) = KeyStoreHelper.createNewKey(Password(pwd))
                            db.storeSaltAndEncryptedMasterKey(salt, cipheredKey)
                            isFirstTimeLogin = false
                            isLoggedIn = true
                            refreshTrigger++
                            val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                            ContinuousAutoSyncCoordinator.instance.onDataMutatedBySync = { refreshTrigger++ }
                            ContinuousAutoSyncCoordinator.instance.start(authToken = clusterSecret)
                        } catch (e: Exception) {
                            statusMessage = "Failed to create vault: ${e.message}"
                        }
                    },
                    onUnlock = { pwd, _ ->
                        try {
                            val (salt, cipheredKey) = db.fetchSaltAndEncryptedMasterKey()
                            KeyStoreHelper.importExistingEncryptedMasterKey(
                                SaltedPassword(salt, Password(pwd)),
                                cipheredKey
                            )
                            isLoggedIn = true
                            refreshTrigger++
                            val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                            ContinuousAutoSyncCoordinator.instance.onDataMutatedBySync = { refreshTrigger++ }
                            ContinuousAutoSyncCoordinator.instance.start(authToken = clusterSecret)
                        } catch (e: Exception) {
                            statusMessage = "Invalid password!"
                        }
                    },
                    onImportBackup = {
                        launchDocumentPicker { content ->
                            importedBackupXml = content
                        }
                    }
                )
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when {
                                        activeSiteEntry != null -> editingSiteDesc.ifBlank { "New Password" }
                                        activeCategory != null -> activeCategory!!.plainName
                                        else -> "Safe 🔐"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        navigationIcon = {
                            NavigationBackIcon(
                                hasBackNavigation = (activeCategory != null || activeSiteEntry != null),
                                onBack = {
                                    when {
                                        activeSiteEntry != null -> {
                                            val siteEntry = activeSiteEntry!!
                                            if (editingSiteDesc.isNotBlank() && (siteEntry.id == null ||
                                                    editingSiteDesc != siteEntry.plainDescription ||
                                                    editingSiteUser != siteEntry.plainUsername ||
                                                    editingSitePass != siteEntry.plainPassword)) {
                                                saveActiveSiteEntry()
                                            } else {
                                                activeSiteEntry = null
                                            }
                                        }
                                        activeCategory != null -> activeCategory = null
                                    }
                                    refreshTrigger++
                                }
                            )
                        },
                        actions = {
                            if (activeSiteEntry != null) {
                                Button(
                                    onClick = { saveActiveSiteEntry() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe94560)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            if (syncStatus.connectedPeersCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2e7d32).copy(alpha = 0.35f),
                                    modifier = Modifier.clickable { showSettingsScreen = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF4CAF50), shape = RoundedCornerShape(3.dp))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "${syncStatus.connectedPeersCount} linked",
                                            fontSize = 11.sp,
                                            color = Color(0xFF81C784),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (activeCategory == null && activeSiteEntry == null) {
                                IconButton(onClick = { showSettingsScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF16213e),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                },
                bottomBar = {
                    if (activeSiteEntry == null) {
                        SharedBottomActionBar(
                            onAddRequested = {
                                if (activeCategory == null) {
                                    showAddCategoryDialog = true
                                } else {
                                    val defaultUser = Preferences.getDefaultUserName()
                                    val genPass = PasswordGenerator.genPassword(
                                        passUpper = true,
                                        passLower = true,
                                        passNum = true,
                                        passSymbols = true,
                                        length = PasswordGenerator.PASSWORD_DEFAULT_LENGTH
                                    )
                                    editingSiteDesc = ""
                                    editingSiteUser = defaultUser
                                    editingSitePass = genPass
                                    editingSiteUrl = ""
                                    editingSiteNote = ""
                                    activeSiteEntry = DecryptableSiteEntry(categoryId = activeCategory!!.id!!).apply {
                                        description = "".encrypt()
                                        username = defaultUser.encrypt()
                                        password = genPass.encrypt()
                                        website = "".encrypt()
                                        note = "".encrypt()
                                    }
                                }
                            },
                            onLockRequested = {
                                ContinuousAutoSyncCoordinator.instance.stop()
                                isLoggedIn = false
                                activeCategory = null
                                activeSiteEntry = null
                            },
                             onSettingsRequested = {
                                 showSettingsScreen = true
                             },
                             onImportExportRequested = {
                                 showImportExportChoiceDialog = true
                             },
                             onPeerSyncRequested = {
                                 showDeviceSyncDialog = true
                             }
                        )
                    }
                },
                containerColor = Color(0xFF1a1a2e)
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when {
                        activeSiteEntry != null -> {
                            val siteEntry = activeSiteEntry!!
                            val showCustomPasswordGenerator = remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                SiteEntryView(
                                    description = editingSiteDesc,
                                    onDescriptionChange = { editingSiteDesc = it },
                                    website = editingSiteUrl,
                                    onWebSiteChange = { editingSiteUrl = it },
                                    username = editingSiteUser,
                                    onUsernameChange = { editingSiteUser = it.utf8password.concatToString() },
                                    password = editingSitePass,
                                    onPasswordChange = { editingSitePass = it.utf8password.concatToString() },
                                    note = editingSiteNote,
                                    onNoteChange = { editingSiteNote = it.utf8password.concatToString() },
                                    onOpenBrowser = { browserUrl ->
                                        val nsUrl = platform.Foundation.NSURL.URLWithString(browserUrl)
                                        if (nsUrl != null) {
                                            platform.UIKit.UIApplication.sharedApplication.openURL(
                                                nsUrl,
                                                options = emptyMap<Any?, Any?>(),
                                                completionHandler = null
                                            )
                                        }
                                    },
                                    onCopyToClipboard = { text ->
                                        platform.UIKit.UIPasteboard.generalPasteboard.string = text
                                    },
                                    customPasswordGeneratorContent = {
                                        if (showCustomPasswordGenerator.value) {
                                            PopCustomPasswordDialog { customPassword ->
                                                if (customPassword != null) {
                                                    editingSitePass = customPassword.utf8password.concatToString()
                                                }
                                                showCustomPasswordGenerator.value = false
                                            }
                                        }
                                    },
                                    bottomBarContent = {
                                        val hasChanges = siteEntry.id == null ||
                                                editingSiteDesc != siteEntry.plainDescription ||
                                                editingSiteUser != siteEntry.plainUsername ||
                                                editingSitePass != siteEntry.plainPassword ||
                                                editingSiteNote != siteEntry.plainNote ||
                                                editingSiteUrl != siteEntry.plainWebsite

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (hasChanges) {
                                                Button(
                                                    onClick = { saveActiveSiteEntry() },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFe94560)
                                                    )
                                                ) {
                                                    Text(getString("password_entry_save"), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            BottomActionBarForSiteEntryView(
                                                onLock = {},
                                                onGeneratePassword = { custom ->
                                                    if (custom) {
                                                        showCustomPasswordGenerator.value = true
                                                    } else {
                                                        editingSitePass = PasswordGenerator.genPassword(
                                                            passUpper = true,
                                                            passLower = true,
                                                            passNum = true,
                                                            passSymbols = true,
                                                            length = PasswordGenerator.PASSWORD_DEFAULT_LENGTH
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        activeCategory != null -> {
                            SiteEntryList(
                                siteEntries = siteEntries,
                                categoriesState = categories,
                                onSiteEntryClick = {
                                    editingSiteDesc = it.plainDescription
                                    editingSiteUser = it.plainUsername
                                    editingSitePass = it.plainPassword
                                    editingSiteNote = it.plainNote
                                    editingSiteUrl = it.plainWebsite
                                    activeSiteEntry = it
                                },
                                onDeleteSiteEntry = {
                                    val deletedTime = Clock.System.now().epochSeconds
                                    db.markSiteEntryDeleted(it.id!!, deletedTime)
                                    val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                                    ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(clusterSecret)
                                    refreshTrigger++
                                }
                            )
                        }
                        else -> {
                            CategoryList(
                                categories = categories,
                                onCategoryClick = { activeCategory = it },
                                onRenameCategory = { category, newName ->
                                     category.encryptedName = newName.encrypt()
                                     db.updateCategory(category.id!!, category)
                                     val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                                     ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(clusterSecret)
                                     refreshTrigger++
                                },
                                onDeleteCategory = {
                                     db.deleteCategory(it.id!!)
                                     val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                                     ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(clusterSecret)
                                     refreshTrigger++
                                }
                            )
                        }
                    }

                    // Dialogs
                    if (showAddCategoryDialog) {
                        AddOrEditCategory(
                            titleText = getString("category_list_add_category"),
                            categoryName = "",
                            onSubmit = { name ->
                                if (name.isNotBlank()) {
                                    db.addCategory(DecryptableCategoryEntry().apply {
                                         encryptedName = name.encrypt()
                                    })
                                    val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
                                    ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(clusterSecret)
                                    refreshTrigger++
                                }
                                showAddCategoryDialog = false
                            }
                        )
                    }  }

                    if (showImportExportChoiceDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportExportChoiceDialog = false },
                            title = { Text(getString("action_bar_import_export")) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Select operation:")
                                    Button(
                                        onClick = {
                                            showImportExportChoiceDialog = false
                                            showDeviceSyncDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("⚡ Device Sync (8-Digit PIN)")
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showImportExportChoiceDialog = false
                                        coroutineScope.launch {
                                            try {
                                                val categoriesList = db.fetchAllCategoryRows()
                                                val softDeletedEntries = db.database.siteEntryDao().getAllSoftDeleted().toSet()
                                                val getSiteEntriesOfCategory = { categoryId: Long -> db.fetchAllRows(categoryId) }
                                                val siteEntryGPMMappings = db.database.siteEntryGPMJoinDao().getAll()
                                                    .groupBy({ it.passwordId }, { it.gpmId })
                                                    .mapValues { (_, v) -> v.toSet() }
                                                val allSavedGPMs = db.database.gpmDao().getAll().toSet()

                                                val buffer = okio.Buffer()
                                                fi.iki.ede.backup.BackupDatabase.backup(
                                                    categoriesList = categoriesList,
                                                    softDeletedEntries = softDeletedEntries,
                                                    getSiteEntriesOfCategory = getSiteEntriesOfCategory,
                                                    siteEntryGPMMappings = siteEntryGPMMappings,
                                                    allSavedGPMs = allSavedGPMs,
                                                    finalSink = buffer
                                                )
                                                val bytes = buffer.readByteArray()
                                                shareBackupFile(bytes, "safe_backup.xml")
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                ) {
                                    Text(getString("action_bar_backup"))
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = {
                                        showImportExportChoiceDialog = false
                                        launchDocumentPicker { content ->
                                            importedBackupXml = content
                                        }
                                    }
                                ) {
                                    Text(getString("action_bar_restore"))
                                }
                            }
                        )
                    }

                    if (showDeviceSyncDialog) {
                        DeviceSyncDialog(
                            onDismissRequest = { showDeviceSyncDialog = false },
                            onSyncCompleted = {
                                refreshTrigger++
                            }
                        )
                    }

                    if (showConflictDialog) {
                        ConflictResolutionDialog(
                            conflicts = activeConflicts,
                            onDismissRequest = { showConflictDialog = false },
                            onResolveCompleted = { resolved ->
                                SyncConflictResolver.applyResolvedConflictsToLocalDatabase(resolved)
                                showConflictDialog = false
                                refreshTrigger++
                            }
                        )
                    }

                    if (showExtensionsEditor) {
                        val allUsedExtensionsAndOnesInPreferences = remember(refreshTrigger) {
                            (db.fetchAllRows(null, false).flatMap {
                                it.plainExtensions.keys
                            }.toSet() + Preferences.getAllExtensions()).toList()
                        }
                        ExtensionsEditor(
                            extensions = allUsedExtensionsAndOnesInPreferences,
                            onDismiss = { showExtensionsEditor = false },
                            done = { editedList ->
                                val newList = mutableListOf<String>()
                                val editedEntries = mutableSetOf<DecryptableSiteEntry>()

                                val maxSize = maxOf(allUsedExtensionsAndOnesInPreferences.size, editedList.size)
                                val extendedList1 = allUsedExtensionsAndOnesInPreferences + List(maxSize - allUsedExtensionsAndOnesInPreferences.size) { null }
                                val extendedList2 = editedList + List(maxSize - editedList.size) { null }
                                val combined = extendedList1.zip(editedList)

                                combined.forEach { (old, new) ->
                                    if (old != null && new != null && old != new) {
                                        newList.add(new)
                                        val list = db.fetchAllRows(null, false)
                                        list.forEach { siteEntry ->
                                            val map = siteEntry.plainExtensions.toMutableMap()
                                            if (map.containsKey(old)) {
                                                val values = map[old] ?: emptySet()
                                                map[new] = values
                                                map.remove(old)
                                                siteEntry.extensions = siteEntry.encryptExtension(map.toMap())
                                                editedEntries.add(siteEntry)
                                            }
                                        }
                                    } else if (new == null) {
                                        val list = db.fetchAllRows(null, false)
                                        list.forEach { siteEntry ->
                                            val map = siteEntry.plainExtensions.toMutableMap()
                                            if (map.containsKey(old)) {
                                                map.remove(old)
                                                siteEntry.extensions = siteEntry.encryptExtension(map.toMap())
                                                editedEntries.add(siteEntry)
                                            }
                                        }
                                    } else if (old == null) {
                                        newList.add(new)
                                    }
                                }
                                Preferences.storeAllExtensions(newList.toSet())
                                editedEntries.forEach { siteEntry ->
                                    db.updateSiteEntry(siteEntry)
                                }
                                refreshTrigger++
                            }
                        )
                    }
                }
            }
        }
    }
    return viewController
}

@OptIn(ExperimentalForeignApi::class)
class XMLDocumentPickerDelegate(
    private val onFilePicked: (ByteArray) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val secured = url.startAccessingSecurityScopedResource()
        try {
            val nsData = NSData.dataWithContentsOfURL(url)
            if (nsData != null) {
                val bytes = ByteArray(nsData.length.toInt())
                if (nsData.length > 0u) {
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                    }
                }
                onFilePicked(bytes)
            }
        } finally {
            if (secured) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}

@Composable
private fun NavigationBackIcon(
    hasBackNavigation: Boolean,
    onBack: () -> Unit
) {
    if (hasBackNavigation) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}
