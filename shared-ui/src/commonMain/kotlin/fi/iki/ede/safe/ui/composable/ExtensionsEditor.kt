package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ExtensionsEditor(
    extensions: List<String>,
    onDismiss: () -> Unit,
    done: (List<String?>) -> Unit = {},
) {
    val itemsList = remember { mutableStateListOf(*extensions.toTypedArray()) }
    var textFieldValue by remember { mutableStateOf("") }
    var selectedItem by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extensions") },
        text = {
            Row {
                Column(modifier = Modifier.weight(0.6f)) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        label = { Text(getString("extension_preferences_add_extension")) },
                    )
                    itemsList.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clickable {
                                    selectedItem = index
                                },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item, Modifier.weight(1f))
                            if (selectedItem == index) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(0.4f)) {
                    Button(
                        enabled = textFieldValue.trim()
                            .isNotBlank() && !itemsList.any {
                            it.trim().equals(textFieldValue.trim(), ignoreCase = true)
                        },
                        onClick = {
                            if (textFieldValue.isNotBlank()) {
                                itemsList.add(textFieldValue)
                                textFieldValue = ""
                            }
                        }
                    ) {
                        Text(getString("extension_preferences_add"))
                    }
                    Button(
                        onClick = {
                            if (selectedItem >= 0) {
                                itemsList[selectedItem] = ""
                                selectedItem = -1
                            }
                        },
                        enabled = selectedItem >= 0
                    ) {
                        Text(getString("extension_preferences_delete"))
                    }
                    Button(
                        onClick = {
                            if (selectedItem >= 0) {
                                itemsList[selectedItem] = textFieldValue
                                selectedItem = -1
                                textFieldValue = ""
                            }
                        },
                        enabled = selectedItem >= 0
                    ) {
                        Text(getString("extension_preferences_rename"))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    done(itemsList)
                    onDismiss()
                }
            ) {
                Text(getString("extension_preferences_apply"))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(getString("extension_preferences_cancel"))
            }
        },
    )
}
