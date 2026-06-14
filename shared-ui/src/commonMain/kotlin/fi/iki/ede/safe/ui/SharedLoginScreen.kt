package fi.iki.ede.safe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SharedLoginScreen(
    isFirstTimeLogin: Boolean,
    isBiometricsEnabled: Boolean,
    statusMessage: String,
    onCreateVault: (String, Boolean) -> Unit, // password, registerBiometrics
    onUnlock: (String, Boolean) -> Unit,      // password, registerBiometrics
    passwordMinimumLength: Int = 8,
    onBiometricLogin: (() -> Unit)? = null,
    onImportBackup: (() -> Unit)? = null,
    onExportBackup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var registerFingerprint by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        androidx.compose.runtime.withFrameNanos {}
        androidx.compose.runtime.withFrameNanos {}
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .width(360.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1e1e2e).copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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

            if (isFirstTimeLogin) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Choose Master Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁️", fontSize = 14.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(TestTag.PASSWORD_PROMPT),
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

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Master Password") },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Text(if (confirmPasswordVisible) "🙈" else "👁️", fontSize = 14.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(TestTag.PASSWORD_PROMPT),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = registerFingerprint,
                        onCheckedChange = { registerFingerprint = it },
                        modifier = Modifier.testTag(TestTag.BIOMETRICS_CHECKBOX),
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFe94560),
                            uncheckedColor = Color(0xFF444466),
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register fingerprint after successful login", color = Color(0xFF8899aa), fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (password.isNotEmpty() && password == confirmPassword) {
                            onCreateVault(password, registerFingerprint)
                        }
                    },
                    enabled = password.isNotEmpty() && password == confirmPassword && password.length >= passwordMinimumLength,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(TestTag.LOGIN_BUTTON),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFe94560)
                    )
                ) {
                    Text("Create Vault", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                if (isBiometricsEnabled && onBiometricLogin != null) {
                    Button(
                        onClick = onBiometricLogin,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(TestTag.BIOMETRICS_BUTTON),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34b38a)
                        )
                    ) {
                        Text("👆 Login with biometrics", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Text("OR", color = Color(0xFF8899aa), fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁️", fontSize = 14.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(TestTag.PASSWORD_PROMPT),
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

                if (!isBiometricsEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = registerFingerprint,
                            onCheckedChange = { registerFingerprint = it },
                            modifier = Modifier.testTag(TestTag.BIOMETRICS_CHECKBOX),
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFe94560),
                                uncheckedColor = Color(0xFF444466),
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register fingerprint after successful login", color = Color(0xFF8899aa), fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        if (password.isNotEmpty()) {
                            onUnlock(password, registerFingerprint)
                        }
                    },
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(TestTag.LOGIN_BUTTON),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFe94560)
                    )
                ) {
                    Text("Unlock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    fontSize = 12.sp,
                    color = Color(0xFF8899aa)
                )
            }

            if (onImportBackup != null || onExportBackup != null) {
                HorizontalDivider(color = Color(0xFF444466), thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (onImportBackup != null) {
                        TextButton(onClick = onImportBackup) {
                            Text("📥 Import Backup", color = Color(0xFF34b38a), fontSize = 12.sp)
                        }
                    }
                    if (onExportBackup != null) {
                        TextButton(onClick = onExportBackup) {
                            Text("📤 Export Backup", color = Color(0xFF3b82f6), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
