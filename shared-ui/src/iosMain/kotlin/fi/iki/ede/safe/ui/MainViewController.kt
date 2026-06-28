@file:OptIn(kotlin.time.ExperimentalTime::class)

package fi.iki.ede.safe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.composable.SiteEntryList
import fi.iki.ede.safe.ui.composable.SiteEntryView
import fi.iki.ede.safe.ui.composable.AddOrEditCategory
import fi.iki.ede.safe.ui.composable.getString
import platform.UIKit.UIViewController
import fi.iki.ede.crypto.support.encrypt
import kotlinx.coroutines.launch
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
import platform.darwin.NSObject
import platform.posix.memcpy
import androidx.compose.ui.draw.rotate

@OptIn(ExperimentalMaterial3Api::class)
fun MainViewController(): UIViewController {
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

    // Triggers for re-fetching lists
    var refreshTrigger by remember { mutableStateOf(0) }

    // Dialog state for adding Category
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    // Backup restore states
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var importedBackupXml by remember { mutableStateOf<String?>(null) }
    var activeDelegate by remember { mutableStateOf<Any?>(null) }

    val launchDocumentPicker = remember {
        { onFilePicked: (String) -> Unit ->
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

    val categories = remember(refreshTrigger, isLoggedIn) {
        if (isLoggedIn) db.fetchAllCategoryRows() else emptyList()
    }

    val siteEntries = remember(refreshTrigger, activeCategory, isLoggedIn) {
        if (isLoggedIn && activeCategory != null) {
            db.fetchAllRows(activeCategory!!.id)
        } else {
            emptyList()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFe94560),
            background = Color(0xFF1a1a2e),
            surface = Color(0xFF16213e)
        )
    ) {
        if (!isLoggedIn) {
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
                        } catch (e: Exception) {
                            statusMessage = "Invalid password!"
                        }
                    },
                    onImportBackup = {
                        launchDocumentPicker { content ->
                            importedBackupXml = content
                            showImportPasswordDialog = true
                        }
                    }
                )
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = when {
                                    activeSiteEntry != null -> activeSiteEntry!!.cachedPlainDescription
                                    activeCategory != null -> activeCategory!!.plainName
                                    else -> "Safe 🔐"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            if (activeCategory != null || activeSiteEntry != null) {
                                IconButton(
                                    onClick = {
                                        when {
                                            activeSiteEntry != null -> activeSiteEntry = null
                                            activeCategory != null -> activeCategory = null
                                        }
                                        refreshTrigger++
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (activeSiteEntry == null && activeCategory == null) {
                                IconButton(
                                    onClick = {
                                        showAddCategoryDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Category")
                                }
                                IconButton(
                                    onClick = {
                                        launchDocumentPicker { content ->
                                            importedBackupXml = content
                                            showImportPasswordDialog = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = "Import Backup",
                                        modifier = Modifier.rotate(90f)
                                    )
                                }
                            } else if (activeCategory != null && activeSiteEntry == null) {
                                IconButton(
                                    onClick = {
                                        activeSiteEntry = DecryptableSiteEntry(categoryId = activeCategory!!.id!!).apply {
                                            description = "".encrypt()
                                            username = "".encrypt()
                                            password = "".encrypt()
                                            website = "".encrypt()
                                            note = "".encrypt()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                                }
                            }
                            IconButton(
                                onClick = {
                                    isLoggedIn = false
                                    activeCategory = null
                                    activeSiteEntry = null
                                }
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
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
                            // Read decrypted properties
                            var desc by remember { mutableStateOf(siteEntry.cachedPlainDescription) }
                            var user by remember { mutableStateOf(siteEntry.plainUsername) }
                            var pass by remember { mutableStateOf(siteEntry.plainPassword) }
                            var note by remember { mutableStateOf(siteEntry.plainNote) }
                            var url by remember { mutableStateOf(siteEntry.plainWebsite) }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SiteEntryView(
                                        description = desc,
                                        onDescriptionChange = { desc = it },
                                        website = url,
                                        onWebSiteChange = { url = it },
                                        username = user,
                                        onUsernameChange = { user = it.utf8password.concatToString() },
                                        password = pass,
                                        onPasswordChange = { pass = it.utf8password.concatToString() },
                                        note = note,
                                        onNoteChange = { note = it.utf8password.concatToString() },
                                        onOpenBrowser = { },
                                        onCopyToClipboard = { }
                                    )
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                siteEntry.cachedPlainDescription = desc
                                                siteEntry.plainUsername = user
                                                siteEntry.plainPassword = pass
                                                siteEntry.plainNote = note
                                                siteEntry.plainWebsite = url
                                                
                                                // Encrypt values back before writing
                                                siteEntry.description = desc.encrypt()
                                                siteEntry.username = user.encrypt()
                                                siteEntry.password = pass.encrypt()
                                                siteEntry.note = note.encrypt()
                                                siteEntry.website = url.encrypt()
                                                
                                                if (siteEntry.id == null) {
                                                    db.addSiteEntry(siteEntry)
                                                } else {
                                                    db.updateSiteEntry(siteEntry)
                                                }
                                                activeSiteEntry = null
                                                refreshTrigger++
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFe94560)
                                    )
                                ) {
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        activeCategory != null -> {
                            SiteEntryList(
                                siteEntries = siteEntries,
                                categoriesState = categories,
                                onSiteEntryClick = { activeSiteEntry = it },
                                onDeleteSiteEntry = {
                                    db.hardDeleteSiteEntry(it.id!!)
                                    refreshTrigger++
                                }
                            )
                        }
                        else -> {
                            CategoryList(
                                categories = categories,
                                onCategoryClick = { activeCategory = it },
                                onRenameCategory = { category, newName ->
                                    category.plainName = newName
                                    category.encryptedName = newName.encrypt()
                                    db.updateCategory(category.id!!, category)
                                    refreshTrigger++
                                },
                                onDeleteCategory = {
                                    db.deleteCategory(it.id!!)
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
                                        plainName = name
                                        encryptedName = name.encrypt()
                                    })
                                    refreshTrigger++
                                }
                                showAddCategoryDialog = false
                            }
                        )
                    }

                    if (showImportPasswordDialog && importedBackupXml != null) {
                        var backupPasswordInput by remember { mutableStateOf("") }
                        var importErrorMsg by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = {
                                showImportPasswordDialog = false
                                importedBackupXml = null
                                importErrorMsg = ""
                            },
                            title = { Text("Restore XML Backup") },
                            text = {
                                Column {
                                    Text("Enter the password that was used to encrypt this backup:")
                                    Spacer(Modifier.height(8.dp))
                                    TextField(
                                        value = backupPasswordInput,
                                        onValueChange = { backupPasswordInput = it },
                                        placeholder = { Text("Backup Password") },
                                        singleLine = true
                                    )
                                    if (importErrorMsg.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(importErrorMsg, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val restorer = RestoreDatabase()
                                                restorer.doRestore(
                                                    backupSource = okio.Buffer().writeUtf8(importedBackupXml!!),
                                                    userPassword = Password(backupPasswordInput),
                                                    dbHelper = db,
                                                    lastBackupDone = null,
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
                                                    passwordLogin = { password ->
                                                        val (salt, encryptedKey) = db.fetchSaltAndEncryptedMasterKey()
                                                        val saltedPassword = SaltedPassword(salt, password)
                                                        KeyStoreHelper.importExistingEncryptedMasterKey(saltedPassword, encryptedKey)
                                                        true
                                                    },
                                                    reportProgress = { _, _, msg ->
                                                        if (msg != null) {
                                                            statusMessage = msg
                                                        }
                                                    },
                                                    verifyUserWantForOldBackup = { _, _ -> true }
                                                )
                                                isFirstTimeLogin = false
                                                isLoggedIn = true
                                                showImportPasswordDialog = false
                                                importedBackupXml = null
                                                refreshTrigger++
                                            } catch (e: Exception) {
                                                importErrorMsg = "Failed to restore: ${e.message}"
                                            }
                                        }
                                    }
                                ) {
                                    Text("Restore")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showImportPasswordDialog = false
                                        importedBackupXml = null
                                        importErrorMsg = ""
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    }
    return viewController
}

@OptIn(ExperimentalForeignApi::class)
class XMLDocumentPickerDelegate(
    private val onFilePicked: (String) -> Unit
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
                onFilePicked(bytes.decodeToString())
            }
        } finally {
            if (secured) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}
