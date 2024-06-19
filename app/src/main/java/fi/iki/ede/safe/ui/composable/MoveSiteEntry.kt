package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun MoveSiteEntry(
    targetCategories: List<DecryptableCategoryEntry>,
    onConfirm: (newCategory: DecryptableCategoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedEntry by remember { mutableStateOf<DecryptableCategoryEntry?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    stringResource(
                        id = R.string.move_password_title,
                        selectedEntry!!.plainName
                    )
                )
            },
            confirmButton = {
                SafeButton(
                    onClick = {
                        selectedEntry?.let { onConfirm(it) }
                        showDialog = false
                    }
                ) {
                    Text(
                        stringResource(
                            id = R.string.move_password_confirm,
                            selectedEntry!!.plainName
                        )
                    )
                }
            },
            dismissButton = {
                SafeButton(onClick = { showDialog = false }) {
                    Text(stringResource(id = R.string.move_password_cancel))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(id = R.string.move_password_select_category)) },
            text = {
                LazyColumn {
                    items(targetCategories.sortedBy { it.plainName }) { entry ->
                        SafeListItem {
                            Text(
                                text = entry.plainName,
                                modifier = Modifier
                                    .clickable {
                                        selectedEntry = entry
                                        showDialog = true
                                    }
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .testTag(TestTag.TEST_TAG_CATEGORY_MOVE_ROW)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                SafeButton(onClick = { onDismiss() }) {
                    Text(stringResource(id = R.string.move_password_cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MoveSiteEtryPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val cat = DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        val site = DecryptableSiteEntry(1).apply {
            description = encrypter("Description".toByteArray())
        }
        MoveSiteEntry(listOf(cat, cat, cat), {}, {})
    }
}