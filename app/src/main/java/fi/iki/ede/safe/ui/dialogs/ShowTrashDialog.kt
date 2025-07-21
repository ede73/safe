package fi.iki.ede.safe.ui.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeListItem
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ShowTrashDialog(
    onDismiss: () -> Unit,
) {
    val deletedSiteEntries by DataModel.softDeletedStateFlow.collectAsState(initial = emptyList())
    var restoreSiteEntry by remember {
        mutableStateOf<DecryptableSiteEntry?>(
            null
        )
    }
    var showEmptyConfirmation by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    if (showEmptyConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showEmptyConfirmation = false
                restoreSiteEntry = null
                onDismiss()
            },
            title = { Text(stringResource(id = R.string.trash_empty_now)) },
            confirmButton = {
                SafeButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            DataModel.emptyAllSoftDeleted(deletedSiteEntries.map { it.id!! }
                                .toSet())
                        }
                        onDismiss()
                    }
                ) { Text(stringResource(id = R.string.trash_do_empty)) }
            },
            dismissButton = {
                SafeButton(onClick = { showEmptyConfirmation = false }) {
                    Text(stringResource(id = R.string.trash_cancel))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(id = R.string.trash_title)) },
            text = {
                Column {
                    if (restoreSiteEntry != null) {
                        SafeButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                DataModel.restoreSiteEntry(restoreSiteEntry!!)
                                restoreSiteEntry = null
                            }
                        }) { Text(stringResource(id = R.string.trash_restore)) }
                    }
                    LazyColumn {
                        items(deletedSiteEntries.sortedBy { it.cachedPlainDescription }) { entry: DecryptableSiteEntry ->
                            SafeListItem {
                                Text(
                                    // TODO: translate to days!
                                    text = "${entry.cachedPlainDescription} (${entry.deleted})",
                                    modifier = Modifier
                                        .clickable { restoreSiteEntry = entry }
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .testTag(TestTag.TRASH_ITEM)
                                        .let {
                                            if (entry == restoreSiteEntry) {
                                                it
                                                    .border(2.dp, MaterialTheme.colorScheme.primary)
                                                    .shadow(4.dp, RoundedCornerShape(4.dp))
                                            } else it
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                SafeButton(onClick = {
                    showEmptyConfirmation = true
                }) { Text(stringResource(id = R.string.trash_empty_trash)) }
            },
            dismissButton = {
                SafeButton(
                    onClick =
                        { onDismiss() }) { Text(stringResource(id = R.string.trash_close)) }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ShowTrashPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        DecryptableSiteEntry(1).apply {
            description = encrypter("Description".toByteArray())
        }
        ShowTrashDialog {}
    }
}