package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.ui.theme.SafeTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditableComboBox(
    items: MutableList<String>,
    onItemSelect: (String) -> Unit,
    onItemEdit: (String) -> Unit,
    onItemDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf("") }

    LaunchedEffect(items) {
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {},
    ) {
        TextField(
            modifier = Modifier.menuAnchor(),
            value = selectedText,
            onValueChange = {
                selectedText = it
                onItemEdit(it)
            },
            readOnly = expanded,
            singleLine = true,
            label = { Text("Enter or select an item") },
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
            items.forEach { menuItemText ->
                DropdownMenuItem(
                    text = {
                        Row(modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        itemToDelete = menuItemText
                                        showDeleteDialog = true
                                    }
                                )
                            }
                            .fillMaxWidth()) {
                            Text(
                                menuItemText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                    onClick = {
                        selectedText = menuItemText
                        onItemSelect(menuItemText)
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
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item?") },
            confirmButton = {
                Button(
                    onClick = {
                        items.remove(itemToDelete)
                        // force recompose
                        onItemDelete(itemToDelete)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
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
            items = mutableListOf("previous1", "previous2"),
            onItemSelect = {},
            onItemEdit = { _ -> }) {
        }
    }
}