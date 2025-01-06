package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditableComboBox(
    selectedItems: Set<String>, // SET should suffice! Hoist to top side(.remove)
    allItems: Set<String>,
    onItemSelected: (String) -> String,
    onItemEdited: (String) -> Unit,
    onItemRequestedToDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf("") }

    LaunchedEffect(selectedItems) {
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {},
    ) {
        TextField(
            modifier = Modifier
                .menuAnchor()
                .testTag(TestTag.SITE_ENTRY_EXTENSION_ENTRY),
            value = selectedText,
            onValueChange = {
                selectedText = it
                onItemEdited(it)
            },
            readOnly = expanded,
            singleLine = true,
            label = { Text(stringResource(id = R.string.site_entry_extension_entry_label)) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Dropdown",
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            allItems.forEach { menuItemText ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        selectedText = onItemSelected(menuItemText)
                                        expanded = false
                                    },
                                    onLongClick = {
                                        itemToDelete = menuItemText
                                        showDeleteDialog = true
                                    },
                                )
                                .fillMaxWidth()
                        ) {
                            if (selectedItems.contains(menuItemText)) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Check Mark"
                                )
                            }
                            Text(
                                menuItemText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    onClick = {
                        selectedText = menuItemText
                        onItemSelected(menuItemText)
                        expanded = false
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }

    // Logic for editing and deleting items goes here
    // This can be implemented using a long press gesture on the DropdownMenuItem
    // and showing a context menu with edit and delete options
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(id = R.string.site_entry_extension_delete)) },
            text = { Text(stringResource(id = R.string.site_entry_extension_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onItemRequestedToDelete(itemToDelete)
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(id = R.string.site_entry_extension_delete))
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(id = R.string.site_entry_extension_cancel))
                }
            }
        )
    }
}


@Preview(showBackground = true)
@Composable
fun EditableComboBoxPreview() {
    SafeTheme {
        EditableComboBox(
            selectedItems = setOf("previous1"),
            allItems = setOf("previous1", "previous2"),
            onItemSelected = { "" },
            onItemEdited = { _ -> },
            {
            },
        )
    }
}