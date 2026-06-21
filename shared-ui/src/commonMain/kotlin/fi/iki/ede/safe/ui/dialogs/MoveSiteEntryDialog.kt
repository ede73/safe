package fi.iki.ede.safe.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeListItem

import fi.iki.ede.safe.ui.composable.getString

import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText

@Composable
fun MoveSiteEntryDialog(
    targetCategories: List<DecryptableCategoryEntry>,
    onConfirm: (newCategory: DecryptableCategoryEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedEntry = remember { mutableStateOf<DecryptableCategoryEntry?>(null) }
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = {
                Text(getString("move_password_title", selectedEntry.value!!.plainName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedEntry.value?.let { onConfirm(it) }
                        showDialog.value = false
                    }
                ) {
                    Text(getString("move_password_confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text(getString("move_password_cancel"))
                }
            },
            modifier = modifier
        )
    } else {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(getString("move_password_select_category")) },
            text = {
                LazyColumn {
                    items(targetCategories.sortedBy { it.plainName }) { entry ->
                        SafeListItem {
                            Text(
                                text = entry.plainName,
                                modifier = Modifier
                                    .clickable {
                                        selectedEntry.value = entry
                                        showDialog.value = true
                                    }
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag(TestTag.CATEGORY_MOVE_ROW)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text(getString("move_password_cancel"))
                }
            },
            modifier = modifier
        )
    }
}

@Preview
@Composable
fun MoveSiteEntryPreview() {
    val cat = DecryptableCategoryEntry().apply {
        encryptedName = IVCipherText(byteArrayOf(), "Work".encodeToByteArray())
    }
    val cat2 = DecryptableCategoryEntry().apply {
        encryptedName = IVCipherText(byteArrayOf(), "Personal".encodeToByteArray())
    }
    MoveSiteEntryDialog(
        targetCategories = listOf(cat, cat2),
        onConfirm = {},
        onDismiss = {}
    )
}
