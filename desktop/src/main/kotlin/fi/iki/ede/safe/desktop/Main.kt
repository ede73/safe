@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.io.encoding.ExperimentalEncodingApi::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.composable.DesktopNavigation
import fi.iki.ede.safe.ui.composable.DesktopSiteEntryNavigation
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.composable.SiteEntryList
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.io.path.deleteIfExists
import kotlin.io.encoding.Base64
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.awt.FileDialog
import java.awt.Frame
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.db.DATABASE_NAME

object DesktopSettings {
    private val settingsPath = Path(System.getProperty("user.home")) / ".safe_desktop_settings"

    fun isBiometricsRegistered(): Boolean {
        if (!settingsPath.exists()) return false
        return runCatching {
            val lines = settingsPath.readLines()
            lines.isNotEmpty() && lines[0] == "true" && lines.size >= 2
        }.getOrDefault(false)
    }

    fun getEncryptedMasterKey(): IVCipherText? {
        if (!settingsPath.exists()) return null
        return runCatching {
            val lines = settingsPath.readLines()
            if (lines.size >= 2 && lines[0] == "true") {
                val combined = Base64.decode(lines[1])
                IVCipherText(16, combined)
            } else {
                null
            }
        }.getOrNull()
    }

    fun registerBiometrics(encryptedMasterKey: IVCipherText) {
        runCatching {
            val combined = encryptedMasterKey.combineIVAndCipherText()
            val base64 = Base64.encode(combined)
            settingsPath.writeText("true\n$base64")
        }
    }

    fun clearBiometrics() {
        runCatching {
            settingsPath.deleteIfExists()
        }
    }
}

private fun loadTpmKeys(db: fi.iki.ede.db.DBHelper) {
    val tpmKeys = db.fetchTpmKeys() ?: return
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKeyBytes = Base64.decode(tpmKeys.first)
    val publicKeyBytes = Base64.decode(tpmKeys.second)
    val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
    KeyStoreHelper.setLoadedKeys(privateKey, publicKey)
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Safe - Password Manager",
        state = rememberWindowState(width = 480.dp, height = 640.dp)
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            LoginScreen()
        }
    }
}

@Preview
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LoginScreen() {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var registerFingerprint by remember { mutableStateOf(false) }

    var isFirstTimeLogin by remember { mutableStateOf(!Path("$DATABASE_NAME.db").exists()) }
    var isBiometricsEnabled by remember { mutableStateOf(DesktopSettings.isBiometricsRegistered()) }
    var showBiometricsScanning by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf(if (isFirstTimeLogin) DesktopStrings.get("login_password_tip") else DesktopStrings.get("login_password_tip")) }
    var isLoggedIn by remember { mutableStateOf(false) }

    // Shared DBHelper instance for this screen
    val db = remember { fi.iki.ede.db.DBHelperFactory.getDBHelper() }

    // Navigation and state variables
    val activeCategory = DesktopNavigation.activeCategory
    val dbRefreshTrigger = DesktopNavigation.dbRefreshTrigger
    
    // Add Category Dialog state
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Password visibility toggle state map (site entry ID -> isVisible)
    val passwordVisibilityMap = remember { mutableStateMapOf<Long, Boolean>() }

    // Import Backup Dialog state
    var showImportDialog by remember { mutableStateOf(false) }
    var importFilePath by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf("") }

    // Export Backup Dialog state
    var exportStatusMessage by remember { mutableStateOf("") }

    if (showBiometricsScanning) {
        LaunchedEffect(Unit) {
            statusMessage = DesktopStrings.get("biometrics_verifying")
            kotlinx.coroutines.delay(1000)
            try {
                loadTpmKeys(db)

                val encryptedKey = DesktopSettings.getEncryptedMasterKey()
                if (encryptedKey != null) {
                    val success = KeyStoreHelper.loginWithBiometricKey(encryptedKey)
                    if (success) {
                        isLoggedIn = true
                        statusMessage = DesktopStrings.get("biometrics_unlock_success")
                    } else {
                        statusMessage = DesktopStrings.get("biometrics_authentication_failed")
                    }
                } else {
                    statusMessage = DesktopStrings.get("biometrics_authentication_failed")
                }
            } catch (ex: Exception) {
                statusMessage = "Biometric error: ${ex.message ?: ex.toString()}"
            }
            showBiometricsScanning = false
        }
    }

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
        if (isLoggedIn) {
            val currentCategory = activeCategory

            Card(
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .width(440.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DesktopColors.CardBackground.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                if (currentCategory == null) {
                    // --- CATEGORY LIST SCREEN ---
                    val categories = remember(dbRefreshTrigger) { db.fetchAllCategoryRows() }

                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Category Toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔓", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(DesktopStrings.get("vault"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        showAddCategoryDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.ActionButton
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("➕ " + DesktopStrings.get("generic_add"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showImportDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.Success
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("📥 " + DesktopStrings.get("action_bar_restore"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val dialog = FileDialog(null as Frame?, DesktopStrings.get("export_backup"), FileDialog.SAVE).apply {
                                                file = "safe_backup.xml"
                                                isVisible = true
                                            }
                                            val file = dialog.file
                                            if (file != null) {
                                                val targetFile = Path(dialog.directory) / file
                                                val backupContent = BackupExporter.exportToXml(db)
                                                targetFile.toFile().writeText(backupContent)
                                                exportStatusMessage = "Backup successfully exported to:\n${targetFile.toFile().name}"
                                            }
                                        } catch (ex: Exception) {
                                            exportStatusMessage = "Export failed:\n${ex.message ?: ex.toString()}"
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.Info
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("📤 " + DesktopStrings.get("action_bar_backup"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        isLoggedIn = false
                                        password = ""
                                        confirmPassword = ""
                                        DesktopNavigation.activeCategory = null
                                        statusMessage = DesktopStrings.get("login_password_tip")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.Primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.get("action_bar_lock"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        HorizontalDivider(color = DesktopColors.Border, thickness = 1.dp)

                        Text(
                            DesktopStrings.get("categories"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopColors.Secondary
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CategoryList(categories)
                        }
                    }
                } else {
                    // --- SITE ENTRY LIST SCREEN (Category Selected) ---
                    val siteEntries = remember(currentCategory, dbRefreshTrigger) {
                        db.fetchAllRows(currentCategory.id)
                    }

                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SiteEntryList Toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { DesktopNavigation.activeCategory = null },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.Border
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("⬅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    currentCategory.plainName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        DesktopSiteEntryNavigation.activeSiteEntry = DecryptableSiteEntry(currentCategory.id!!)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.ActionButton
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("➕ " + DesktopStrings.get("generic_add"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        db.deleteCategory(currentCategory.id!!)
                                        DesktopNavigation.dbRefreshTrigger++
                                        DesktopNavigation.activeCategory = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.Primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("🗑️ " + DesktopStrings.get("delete"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        HorizontalDivider(color = DesktopColors.Border, thickness = 1.dp)

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SiteEntryList(siteEntries)
                        }
                    }
                }
            }
        } else {
            if (showBiometricsScanning) {
                Card(
                    modifier = Modifier
                        .width(320.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DesktopColors.CardBackground
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("👆", fontSize = 48.sp)
                        Text(
                            DesktopStrings.get("biometrics_unlock"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            DesktopStrings.get("biometrics_scanning"),
                            fontSize = 14.sp,
                            color = DesktopColors.Secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(color = DesktopColors.Success)
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DesktopColors.CardBackground.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "🔐",
                            fontSize = 48.sp
                        )

                        Text(
                            "Safe",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = DesktopColors.Primary
                        )

                        Text(
                            DesktopStrings.get("application_name"),
                            fontSize = 14.sp,
                            color = DesktopColors.Secondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isFirstTimeLogin) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(DesktopStrings.get("login_password_tip")) },
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Text(if (passwordVisible) "🙈" else "👁️", fontSize = 14.sp)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesktopColors.Primary,
                                    focusedLabelColor = DesktopColors.Primary,
                                    unfocusedBorderColor = DesktopColors.Border,
                                    unfocusedLabelColor = DesktopColors.Secondary,
                                    cursorColor = DesktopColors.Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text(DesktopStrings.get("login_verify_password_tip")) },
                                visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Text(if (confirmPasswordVisible) "🙈" else "👁️", fontSize = 14.sp)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesktopColors.Primary,
                                    focusedLabelColor = DesktopColors.Primary,
                                    unfocusedBorderColor = DesktopColors.Border,
                                    unfocusedLabelColor = DesktopColors.Secondary,
                                    cursorColor = DesktopColors.Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = registerFingerprint,
                                    onCheckedChange = { registerFingerprint = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DesktopColors.Primary,
                                        uncheckedColor = DesktopColors.Border,
                                        checkmarkColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(DesktopStrings.get("biometrics_register"), color = DesktopColors.Secondary, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    statusMessage = "Creating vault..."
                                    try {
                                        val (salt, cipheredKey) = KeyStoreHelper.createNewKey(fi.iki.ede.crypto.Password(password))
                                        db.storeSaltAndEncryptedMasterKey(salt, cipheredKey)
                                        
                                        val privKey = KeyStoreHelper.getLoadedPrivateKey()
                                        val pubKey = KeyStoreHelper.getLoadedPublicKey()
                                        if (privKey != null && pubKey != null) {
                                            val privateKeyBase64 = Base64.encode(privKey.encoded)
                                            val publicKeyBase64 = Base64.encode(pubKey.encoded)
                                            db.storeTpmKeys(privateKeyBase64, publicKeyBase64)
                                        }

                                        if (registerFingerprint) {
                                            val encryptedKey = KeyStoreHelper.getBiometricEncryptedMasterKey()
                                            if (encryptedKey != null) {
                                                DesktopSettings.registerBiometrics(encryptedKey)
                                                isBiometricsEnabled = true
                                            }
                                        }

                                        isLoggedIn = true
                                        statusMessage = "Unlock successful! Logged in."
                                        isFirstTimeLogin = false
                                    } catch (ex: Exception) {
                                        statusMessage = "Failed to create vault: ${ex.message}"
                                    }
                                },
                                enabled = password.isNotEmpty() && password == confirmPassword && password.length >= 6,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopColors.Primary
                                )
                            ) {
                                Text(DesktopStrings.get("create_vault"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            if (isBiometricsEnabled) {
                                Button(
                                    onClick = {
                                        showBiometricsScanning = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopColors.Success
                                    )
                                ) {
                                    Text("👆 " + DesktopStrings.get("login_with_biometrics"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Text("OR", color = DesktopColors.Secondary, fontSize = 12.sp)
                            }

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(DesktopStrings.get("login_password_tip")) },
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Text(if (passwordVisible) "🙈" else "👁️", fontSize = 14.sp)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesktopColors.Primary,
                                    focusedLabelColor = DesktopColors.Primary,
                                    unfocusedBorderColor = DesktopColors.Border,
                                    unfocusedLabelColor = DesktopColors.Secondary,
                                    cursorColor = DesktopColors.Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            if (!isBiometricsEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = registerFingerprint,
                                        onCheckedChange = { registerFingerprint = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = DesktopColors.Primary,
                                            uncheckedColor = DesktopColors.Border,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(DesktopStrings.get("biometrics_register"), color = DesktopColors.Secondary, fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    statusMessage = "Authenticating..."
                                    try {
                                        // Existing login - fetch and decrypt master key
                                        val (salt, cipheredKey) = db.fetchSaltAndEncryptedMasterKey()
                                        
                                        // Make sure TPM keys are loaded from DB into KeyStoreHelper
                                        loadTpmKeys(db)

                                        KeyStoreHelper.importExistingEncryptedMasterKey(
                                            fi.iki.ede.crypto.SaltedPassword(salt, fi.iki.ede.crypto.Password(password)),
                                            cipheredKey
                                        )

                                        if (registerFingerprint) {
                                            val encryptedKey = KeyStoreHelper.getBiometricEncryptedMasterKey()
                                            if (encryptedKey != null) {
                                                DesktopSettings.registerBiometrics(encryptedKey)
                                                isBiometricsEnabled = true
                                            }
                                        }

                                        isLoggedIn = true
                                        statusMessage = "Unlock successful! Logged in."
                                    } catch (ex: Exception) {
                                        statusMessage = DesktopStrings.get("login_invalid_password")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopColors.Primary
                                )
                            ) {
                                Text(DesktopStrings.get("unlock"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Text(
                            statusMessage,
                            fontSize = 12.sp,
                            color = DesktopColors.Secondary
                        )
                    }
                }
            }
        }

        // --- Add Category Dialog Overlay ---
        if (showAddCategoryDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true, onClick = { showAddCategoryDialog = false }),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(340.dp)
                        .padding(16.dp)
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DesktopColors.CardBackground
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(DesktopStrings.get("add_category"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text(DesktopStrings.get("category_name")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DesktopColors.Primary,
                                focusedLabelColor = DesktopColors.Primary,
                                unfocusedBorderColor = DesktopColors.Border,
                                unfocusedLabelColor = DesktopColors.Secondary,
                                cursorColor = DesktopColors.Primary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddCategoryDialog = false }) {
                                Text(DesktopStrings.get("generic_cancel"), color = DesktopColors.Secondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newCategoryName.isNotBlank()) {
                                        db.addCategory(DecryptableCategoryEntry().apply {
                                            this.encryptedName = newCategoryName.encrypt()
                                        })
                                        DesktopNavigation.dbRefreshTrigger++
                                        newCategoryName = ""
                                        showAddCategoryDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Primary)
                            ) {
                                Text(DesktopStrings.get("save"))
                            }
                        }
                    }
                }
            }
        }



        // --- Edit/View Site Entry Dialog Overlay ---
        val activeSiteEntry = DesktopSiteEntryNavigation.activeSiteEntry
        if (activeSiteEntry != null) {
            var editDesc by remember(activeSiteEntry) { mutableStateOf(activeSiteEntry.cachedPlainDescription) }
            var editWeb by remember(activeSiteEntry) { mutableStateOf(activeSiteEntry.plainWebsite) }
            var editUser by remember(activeSiteEntry) { mutableStateOf(activeSiteEntry.plainUsername) }
            var editPass by remember(activeSiteEntry) { mutableStateOf(activeSiteEntry.plainPassword) }
            var editNote by remember(activeSiteEntry) { mutableStateOf(activeSiteEntry.plainNote) }
            var editPassVisible by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true, onClick = { DesktopSiteEntryNavigation.activeSiteEntry = null }),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(400.dp)
                        .padding(16.dp)
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DesktopColors.CardBackground
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (activeSiteEntry.id == null) DesktopStrings.get("add_password_entry") else DesktopStrings.get("password_entry_details"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text(DesktopStrings.get("description")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DesktopColors.Primary,
                                focusedLabelColor = DesktopColors.Primary,
                                unfocusedBorderColor = DesktopColors.Border,
                                unfocusedLabelColor = DesktopColors.Secondary,
                                cursorColor = DesktopColors.Primary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editWeb,
                                onValueChange = { editWeb = it },
                                label = { Text(DesktopStrings.get("website")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesktopColors.Primary,
                                    focusedLabelColor = DesktopColors.Primary,
                                    unfocusedBorderColor = DesktopColors.Border,
                                    unfocusedLabelColor = DesktopColors.Secondary,
                                    cursorColor = DesktopColors.Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            if (editWeb.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        openBrowser(editWeb)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.ActionButton),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.get("password_entry_visit"), fontSize = 12.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editUser,
                                onValueChange = { editUser = it },
                                label = { Text(DesktopStrings.get("username")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesktopColors.Primary,
                                    focusedLabelColor = DesktopColors.Primary,
                                    unfocusedBorderColor = DesktopColors.Border,
                                    unfocusedLabelColor = DesktopColors.Secondary,
                                    cursorColor = DesktopColors.Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            if (editUser.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        copyToClipboard(editUser)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.ActionButton),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.get("password_entry_username_label"), fontSize = 12.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = if (editPassVisible) editPass else "••••••••",
                                onValueChange = { if (editPassVisible) editPass = it },
                                label = { Text(DesktopStrings.get("password")) },
                                singleLine = true,
                                readOnly = !editPassVisible,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DesktopColors.Primary,
                                    focusedLabelColor = DesktopColors.Primary,
                                    unfocusedBorderColor = DesktopColors.Border,
                                    unfocusedLabelColor = DesktopColors.Secondary,
                                    cursorColor = DesktopColors.Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { editPassVisible = !editPassVisible }) {
                                Text(if (editPassVisible) "🙈" else "👁️", fontSize = 14.sp)
                            }
                            if (editPass.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = {
                                        copyToClipboard(editPass)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.ActionButton),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(DesktopStrings.get("password_entry_password_label"), fontSize = 12.sp)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val newSecurePass = (1..16).map {
                                    (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '@', '#', '$', '%', '&')).random()
                                }.joinToString("")
                                editPass = newSecurePass
                                editPassVisible = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Success),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(DesktopStrings.get("action_bar_generate_password"), fontSize = 12.sp)
                        }

                        OutlinedTextField(
                            value = editNote,
                            onValueChange = { editNote = it },
                            label = { Text(DesktopStrings.get("notes")) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DesktopColors.Primary,
                                focusedLabelColor = DesktopColors.Primary,
                                unfocusedBorderColor = DesktopColors.Border,
                                unfocusedLabelColor = DesktopColors.Secondary,
                                cursorColor = DesktopColors.Primary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeSiteEntry.id != null) {
                                Button(
                                    onClick = {
                                        db.hardDeleteSiteEntry(activeSiteEntry.id!!)
                                        DesktopSiteEntryNavigation.activeSiteEntry = null
                                        DesktopNavigation.dbRefreshTrigger++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Primary)
                                ) {
                                    Text(DesktopStrings.get("delete"))
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row {
                                TextButton(onClick = { DesktopSiteEntryNavigation.activeSiteEntry = null }) {
                                    Text(DesktopStrings.get("generic_cancel"), color = DesktopColors.Secondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (editDesc.isNotBlank()) {
                                            val updated = DecryptableSiteEntry(activeSiteEntry.categoryId ?: 0L).apply {
                                                this.id = activeSiteEntry.id
                                                this.description = editDesc.encrypt()
                                                this.username = editUser.encrypt()
                                                this.password = editPass.encrypt()
                                                this.website = editWeb.encrypt()
                                                this.note = editNote.encrypt()
                                                this.deleted = activeSiteEntry.deleted
                                                this.passwordChangedDate = activeSiteEntry.passwordChangedDate
                                            }
                                            if (updated.id == null) {
                                                db.addSiteEntry(updated)
                                            } else {
                                                db.updateSiteEntry(updated)
                                            }
                                            DesktopSiteEntryNavigation.activeSiteEntry = null
                                            DesktopNavigation.dbRefreshTrigger++
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.ActionButton)
                                ) {
                                    Text(DesktopStrings.get("save"))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Import Backup Dialog Overlay ---
        if (showImportDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true, onClick = { showImportDialog = false }),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(16.dp)
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1e1e2e)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Import Backup XML", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = importFilePath,
                                onValueChange = { importFilePath = it },
                                label = { Text("Backup XML File") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFe94560),
                                    focusedLabelColor = Color(0xFFe94560),
                                    unfocusedBorderColor = Color(0xFF444466),
                                    unfocusedLabelColor = Color(0xFF8899aa),
                                    cursorColor = Color(0xFFe94560),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Backup XML", java.awt.FileDialog.LOAD).apply {
                                            file = "*.xml"
                                            isVisible = true
                                        }
                                        val file = dialog.file
                                        if (file != null) {
                                            importFilePath = java.io.File(dialog.directory, file).absolutePath
                                        }
                                    } catch (e: Exception) {
                                        importStatusMessage = "Failed to open file dialog"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4e54c8)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Browse", fontSize = 12.sp)
                            }
                        }

                        OutlinedTextField(
                            value = importPassword,
                            onValueChange = { importPassword = it },
                            label = { Text("Backup Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFe94560),
                                focusedLabelColor = Color(0xFFe94560),
                                unfocusedBorderColor = Color(0xFF444466),
                                unfocusedLabelColor = Color(0xFF8899aa),
                                cursorColor = Color(0xFFe94560),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        
                        if (importStatusMessage.isNotBlank()) {
                            Text(importStatusMessage, color = Color(0xFFe94560), fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showImportDialog = false }) {
                                Text("Cancel", color = Color(0xFF8899aa))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (importFilePath.isBlank()) {
                                        importStatusMessage = "Please select a backup file"
                                        return@Button
                                    }
                                    if (importPassword.isBlank()) {
                                        importStatusMessage = "Please enter backup password"
                                        return@Button
                                    }
                                    try {
                                        val file = java.io.File(importFilePath)
                                        if (!file.exists()) {
                                            importStatusMessage = "File does not exist"
                                            return@Button
                                        }
                                        val content = file.readText()
                                        val count = BackupImporter.importFromXml(content, importPassword, db)
                                        DesktopNavigation.dbRefreshTrigger++
                                        showImportDialog = false
                                        importFilePath = ""
                                        importPassword = ""
                                        importStatusMessage = ""
                                    } catch (ex: Exception) {
                                        importStatusMessage = "Import failed: ${ex.message ?: ex.toString()}"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe94560))
                            ) {
                                Text("Import")
                            }
                        }
                    }
                }
            }
        }

        // --- Export Backup Status Dialog Overlay ---
        if (exportStatusMessage.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true, onClick = { exportStatusMessage = "" }),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(320.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1e1e2e)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Export Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(exportStatusMessage, color = Color.White, fontSize = 14.sp)
                        Button(
                            onClick = { exportStatusMessage = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4e54c8))
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

private fun openBrowser(url: String) {
    try {
        val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        val os = System.getProperty("os.name").lowercase()
        val rt = Runtime.getRuntime()
        if (os.indexOf("win") >= 0) {
            rt.exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", cleanUrl))
        } else if (os.indexOf("mac") >= 0) {
            rt.exec(arrayOf("open", cleanUrl))
        } else {
            rt.exec(arrayOf("xdg-open", cleanUrl))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun copyToClipboard(text: String) {
    try {
        val selection = java.awt.datatransfer.StringSelection(text)
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(selection, selection)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
