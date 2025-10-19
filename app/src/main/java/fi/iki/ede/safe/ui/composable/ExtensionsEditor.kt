package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.R
import fi.iki.ede.theme.SafeThemeSurface

@Composable
fun ExtensionsEditor(
    extensions: List<String>,
    done: (List<String?>) -> Unit = {},
) {
    var showDialog by remember { mutableStateOf(true) }
    val itemsList = remember { mutableStateListOf(*extensions.toTypedArray()) }
    var textFieldValue by remember { mutableStateOf("") }
    var selectedItem by remember { mutableIntStateOf(-1) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Extensions") },
            text = {
                Row {
                    Column(modifier = Modifier.weight(0.6f)) {
                        TextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            label = { Text(stringResource(R.string.extension_preferences_add_extension)) },
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
                            Text(stringResource(R.string.extension_preferences_add))
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
                            Text(stringResource(R.string.extension_preferences_delete))
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
                            Text(stringResource(R.string.extension_preferences_rename))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // return edits in EXACT order present in the list
                        done(itemsList)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.extension_preferences_apply))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false }
                ) {
                    Text(stringResource(R.string.extension_preferences_cancel))
                }
            },
        )
    }
}

@DualModePreview
@Composable
fun ExtensionsEditorPreview() {
    SafeThemeSurface {
        ExtensionsEditor(listOf("a", "b", "c"))
    }
}