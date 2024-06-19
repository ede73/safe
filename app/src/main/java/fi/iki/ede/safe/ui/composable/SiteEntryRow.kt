package fi.iki.ede.safe.ui.composable

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteEntryRow(
    passEntry: DecryptableSiteEntry,
    categoriesState: List<DecryptableCategoryEntry>,
) {
    val context = LocalContext.current
    val safeTheme = LocalSafeTheme.current
    val coroutineScope = rememberCoroutineScope()
    var displayDeleteDialog by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }
    var displayMoveDialog by remember { mutableStateOf(false) }

    val editCompleted = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    // TODO: should not be needed
                }
            }
        }
    )

    // bit more padding on the start to emphasize difference between header and entry
    // TODO: themable?
    SafeListItem(
        borderColor = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .padding(start = 32.dp, 6.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = {
                editCompleted.launch(
                    IntentManager.getEditPassword(context, passEntry.id!!)
                )
            }, onLongClick = {
                displayMenu = true
            }),
    ) {
        Row {
            Text(
                text = passEntry.plainDescription,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                // Move to bounding box (up)
                modifier = Modifier
                    .padding(12.dp)
                    .weight(2f)
            )

            if (passEntry.passwordChangedDate != null) {
                Spacer(modifier = Modifier.weight(1f)) // This will push the Text to the end

                Text(
                    text = getPasswordAgePlurality(DateUtils.getPeriodBetweenDates(passEntry.passwordChangedDate!!)),
                    modifier = Modifier.padding(12.dp),
                    style = safeTheme.customFonts.smallNote
                )
            }
        }
        DropdownMenu(expanded = displayMenu, onDismissRequest = { displayMenu = false }) {
            DropdownMenuItem(text = {
                Text(
                    text = stringResource(
                        id = R.string.password_list_delete_password,
                        passEntry.plainDescription
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
                        passEntry.plainDescription
                    )
                )
            }, onClick = {
                displayMenu = false
                displayMoveDialog = true
            })
        }
        if (displayDeleteDialog) {
            DeleteSiteEntryDialog(passEntry, onConfirm = {
                coroutineScope.launch {
                    DataModel.deletePassword(passEntry)
                }
                displayDeleteDialog = false
            }, onDismiss = {
                displayDeleteDialog = false
            })
        }
        if (displayMoveDialog) {
            val currentCategory =
                DataModel.getCategories().first { it.id == passEntry.categoryId }
            val filteredCategories = categoriesState.filter { it != currentCategory }
                .sortedBy { it.plainName.lowercase() }

            MoveSiteEntry(filteredCategories, onConfirm = { newCategory ->
                coroutineScope.launch {
                    DataModel.movePassword(passEntry, newCategory)
                }
                displayMoveDialog = false
            }, onDismiss = {
                displayMoveDialog = false
            })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SiteEntryRowPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val site1 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description1".toByteArray())
        }
        val cat = DecryptableCategoryEntry().apply {
            id = 1
            encryptedName = encrypter("Category".toByteArray())
        }
        SiteEntryRow(site1, listOf(cat))
    }
}