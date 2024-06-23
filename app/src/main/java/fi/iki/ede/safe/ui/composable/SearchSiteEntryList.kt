package fi.iki.ede.safe.ui.composable

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DataModel.getCategory
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SearchSiteEntryList(
    filteredPasswords: MutableStateFlow<List<DecryptableSiteEntry>>
) {
    val context = LocalContext.current
    val passwordState = filteredPasswords.collectAsState()
    val sortedPasswords by remember(passwordState) {
        derivedStateOf {
            passwordState.value.sortedBy { it.cachedPlainDescription }
        }
    }

    fun updateEntry(entryToUpdate: DecryptableSiteEntry) {
        val updatedList = filteredPasswords.value.map { entry ->
            if (entry.id == entryToUpdate.id)
                entryToUpdate.copy()
            else
                entry
        }
        filteredPasswords.value = updatedList
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Handle the result here
                val resultIntent = result.data
                if (resultIntent != null) {
                    val passwordId = resultIntent.getLongExtra(SiteEntryEditScreen.PASSWORD_ID, -1L)
                    updateEntry(DataModel.getPassword(passwordId))
                }
            }
        }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = sortedPasswords, itemContent = { filteredItem ->
            // Merge with PasswordRow
            MatchingSiteEntry(
                passwordEntry = filteredItem,
                categoryEntry = filteredItem.getCategory(), onEntryClick = {
                    launcher.launch(
                        IntentManager.getEditPassword(context, it.id!!)
                    )
                }, onDelete = { deletedEntry ->
                    // it is gone already
                    val newList = filteredPasswords.value.filter { it != deletedEntry }
                    filteredPasswords.value = newList
                }, onUpdate = { entryToUpdate ->
                    updateEntry(entryToUpdate)
                })
        })
    }
}

@Preview(showBackground = true)
@Composable
fun SearchSiteEntryListPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val site1 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description1".toByteArray())
        }
        val site2 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description2".toByteArray())
        }
        val cat = DecryptableCategoryEntry().apply {
            id = 1
            encryptedName = encrypter("Category".toByteArray())
        }
        val lst = mutableListOf(site1, site2)
        val sitesFlow = MutableStateFlow(lst.toList())
        DataModel._categories[cat] = lst
        SearchSiteEntryList(sitesFlow)
    }
}