package fi.iki.ede.safe.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun LoginScreen() {
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Enter master password") }
    var isLoggedIn by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoggedIn) {
            Card(
                modifier = Modifier
                    .width(360.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1e1e2e).copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "🔓",
                        fontSize = 48.sp
                    )

                    Text(
                        "Vault Unlocked",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4e9f3d)
                    )

                    Text(
                        "Crypto key derived successfully!\nMock DB initialized.",
                        fontSize = 14.sp,
                        color = Color(0xFF8899aa),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            isLoggedIn = false
                            password = ""
                            statusMessage = "Enter master password"
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFe94560)
                        )
                    ) {
                        Text("Lock Vault", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .width(360.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1e1e2e).copy(alpha = 0.9f)
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
                        color = Color(0xFFe94560)
                    )

                    Text(
                        "Password Manager",
                        fontSize = 14.sp,
                        color = Color(0xFF8899aa)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
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

                    Button(
                        onClick = {
                            statusMessage = "Authenticating..."
                            try {
                                val db = fi.iki.ede.db.DBHelperFactory.getDBHelper()
                                val dbFile = java.io.File("safe_db.json")
                                if (!dbFile.exists()) {
                                    // First time login - setup password and generate keys
                                    statusMessage = "First run: Creating master and TPM keys..."
                                    val (salt, cipheredKey) = fi.iki.ede.crypto.keystore.KeyStoreHelper.createNewKey(fi.iki.ede.crypto.Password(password))
                                    db.storeSaltAndEncryptedMasterKey(salt, cipheredKey)
                                    
                                    val privKey = fi.iki.ede.crypto.keystore.KeyStoreHelper.getLoadedPrivateKey()
                                    val pubKey = fi.iki.ede.crypto.keystore.KeyStoreHelper.getLoadedPublicKey()
                                    if (privKey != null && pubKey != null) {
                                        val privateKeyBase64 = java.util.Base64.getEncoder().encodeToString(privKey.encoded)
                                        val publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(pubKey.encoded)
                                        db.storeTpmKeys(privateKeyBase64, publicKeyBase64)
                                    }
                                    isLoggedIn = true
                                    statusMessage = "Unlock successful! Logged in."
                                } else {
                                    // Existing login - fetch and decrypt master key
                                    val (salt, cipheredKey) = db.fetchSaltAndEncryptedMasterKey()
                                    
                                    // Make sure TPM keys are loaded from DB into KeyStoreHelper
                                    val tpmKeys = db.fetchTpmKeys()
                                    if (tpmKeys != null) {
                                        val keyFactory = java.security.KeyFactory.getInstance("RSA")
                                        val privateKeyBytes = java.util.Base64.getDecoder().decode(tpmKeys.first)
                                        val publicKeyBytes = java.util.Base64.getDecoder().decode(tpmKeys.second)
                                        val privateKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes))
                                        val publicKey = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
                                        fi.iki.ede.crypto.keystore.KeyStoreHelper.setLoadedKeys(privateKey, publicKey)
                                    }

                                    fi.iki.ede.crypto.keystore.KeyStoreHelper.importExistingEncryptedMasterKey(
                                        fi.iki.ede.crypto.SaltedPassword(salt, fi.iki.ede.crypto.Password(password)),
                                        cipheredKey
                                    )
                                    isLoggedIn = true
                                    statusMessage = "Unlock successful! Logged in."
                                }
                            } catch (ex: Exception) {
                                statusMessage = "Invalid master password"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFe94560)
                        )
                    ) {
                        Text("Unlock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        statusMessage,
                        fontSize = 12.sp,
                        color = Color(0xFF8899aa)
                    )
                }
            }
        }
    }
}
