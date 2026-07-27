package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.preferences.Preferences
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditExtensions: () -> Unit
) {
    val scrollState = rememberScrollState()

    // 1. Lock Timeout
    val lockTimeoutOptions = listOf("1", "2", "5", "10", "30", "60")
    var lockTimeoutMinutes by remember {
        mutableStateOf(Preferences.getLockTimeoutDuration().toInt(DurationUnit.MINUTES).toString())
    }
    var showLockTimeoutDropdown by remember { mutableStateOf(false) }

    // 2. Clipboard Clear Delay
    var clipboardDelay by remember {
        mutableStateOf(Preferences.getClipboardClearDelaySecs().toString())
    }

    // 3. Lock on Screen Lock
    var lockOnScreenLock by remember {
        mutableStateOf(Preferences.getLockOnScreenLock(true))
    }

    // 4. Default Username
    var defaultUsername by remember {
        mutableStateOf(Preferences.getDefaultUserName())
    }

    // 5. Soft Delete Days
    var softDeleteDays by remember {
        mutableStateOf(Preferences.getSoftDeleteDays().toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF16213e),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1a1a2e)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1a1a2e))
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // General Settings Card
            Text(
                text = "Security",
                color = Color(0xFFe94560),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. Automatic locking timeout
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Automatic locking timeout",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "After chosen amount of minutes, password safe will lock and require logging in again.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = "$lockTimeoutMinutes minutes",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFe94560),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLockTimeoutDropdown = true },
                            enabled = false // Disable direct text input
                        )
                        DropdownMenu(
                            expanded = showLockTimeoutDropdown,
                            onDismissRequest = { showLockTimeoutDropdown = false },
                            modifier = Modifier.background(Color(0xFF16213e))
                        ) {
                            lockTimeoutOptions.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text("$minutes minutes", color = Color.White) },
                                    onClick = {
                                        lockTimeoutMinutes = minutes
                                        Preferences.setLockTimeoutMinutes(minutes)
                                        showLockTimeoutDropdown = false
                                    }
                                )
                            }
                        }
                        // Clickable overlay since OutlinedTextField is disabled
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showLockTimeoutDropdown = true }
                        )
                    }
                }
            }

            // 2. Clipboard Clear Delay
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Clipboard clearing delay",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Delay in seconds after which clipboard is cleared.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = clipboardDelay,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                clipboardDelay = newValue
                                if (newValue.isNotEmpty()) {
                                    Preferences.setClipboardClearDelaySecs(newValue)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFe94560),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Lock on Screen Lock
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Lock when screen locks",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Automatically lock the vault when the screen turns off.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = lockOnScreenLock,
                        onCheckedChange = { checked ->
                            lockOnScreenLock = checked
                            Preferences.setLockOnScreenLock(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFe94560),
                            checkedTrackColor = Color(0xFFe94560).copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Text(
                text = "Preferences",
                color = Color(0xFFe94560),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            // 4. Default Username
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Default username",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Default username used when adding a new password entry.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultUsername,
                        onValueChange = { newValue ->
                            defaultUsername = newValue
                            Preferences.setDefaultUserName(newValue)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFe94560),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 5. Soft Delete Days
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Purge deleted passwords",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Set soft deletion duration (days) for deleted passwords. 0 deletes immediately.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = softDeleteDays,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                softDeleteDays = newValue
                                if (newValue.isNotEmpty()) {
                                    Preferences.setSoftDeleteDays(newValue.toInt())
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFe94560),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 6. Edit Extensions Button
            Button(
                onClick = onEditExtensions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFe94560)
                )
            ) {
                Text("Edit Extensions", fontWeight = FontWeight.Bold)
            }
        }
    }
}
