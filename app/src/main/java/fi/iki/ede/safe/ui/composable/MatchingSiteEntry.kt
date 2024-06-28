package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DataModel.getCategory
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.dialogs.DeleteSiteEntryDialog
import fi.iki.ede.safe.ui.dialogs.MoveSiteEntryDialog
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.launch

// TODO: THIS! Should have MUCH more common with PasswordRow!
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchingSiteEntry(
    siteEntry: DecryptableSiteEntry,
    categoryEntry: DecryptableCategoryEntry,
    onSiteEntryClick: (DecryptableSiteEntry) -> Unit,
    onDeleteSiteEntry: (DecryptableSiteEntry) -> Unit,
    onUpdateSiteEntry: (DecryptableSiteEntry) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val safeTheme = LocalSafeTheme.current
    var displayDeleteDialog by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }
    var displayMoveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .combinedClickable(onClick = { onSiteEntryClick(siteEntry) }, onLongClick = {
                displayMenu = true
            })
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        SafeListItem {
            Text(
                text = categoryEntry.plainName,
                style = safeTheme.customFonts.listEntries,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(8.dp)
            )
            Row {
                Text(
                    text = siteEntry.cachedPlainDescription,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(8.dp)
                        .weight(2f)
                        .testTag(TestTag.SEARCH_MATCH)
                )
                if (siteEntry.passwordChangedDate != null) {
                    Spacer(modifier = Modifier.weight(1f)) // This will push the Text to the end
                    Text(
                        text = getPasswordAgePlurality(
                            duration = DateUtils.getPeriodBetweenDates(
                                siteEntry.passwordChangedDate!!
                            )
                        ),
                        style = safeTheme.customFonts.smallNote,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            DropdownMenu(expanded = displayMenu, onDismissRequest = { displayMenu = false }) {
                DropdownMenuItem(text = {
                    Text(
                        text = stringResource(
                            id = R.string.password_list_delete_password,
                            siteEntry.cachedPlainDescription
                        )
                    )
                }, onClick = {
                    displayMenu = false
                    displayDeleteDialog = true
                })
                DropdownMenuItem(text = {
                    Text(
                        text = stringResource(
                            id = R.string.password_list_move_password,
                            siteEntry.cachedPlainDescription
                        )
                    )
                }, onClick = {
                    displayMenu = false
                    displayMoveDialog = true
                })
            }
            if (displayDeleteDialog) {
                DeleteSiteEntryDialog(siteEntry, onConfirm = {
                    coroutineScope.launch {
                        DataModel.deleteSiteEntry(siteEntry)
                        onDeleteSiteEntry(siteEntry)
                    }
                    displayDeleteDialog = false
                }, onDismiss = {
                    displayDeleteDialog = false
                })
            }
            if (displayMoveDialog) {
                val currentCategory = siteEntry.getCategory()
                val filteredCategories = DataModel.getCategories().filter { it != currentCategory }

                MoveSiteEntryDialog(filteredCategories, onConfirm = { newCategory ->
                    coroutineScope.launch {
                        DataModel.moveSiteEntry(siteEntry, newCategory)
                    }
                    displayMoveDialog = false
                    val z = siteEntry.copy()
                    z.categoryId = newCategory.id
                    onUpdateSiteEntry(z)
                }, onDismiss = {
                    displayMoveDialog = false
                })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MatchingSiteEntryPreview() {
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
        MatchingSiteEntry(site, cat, {}, {}, {})
    }
}