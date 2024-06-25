package fi.iki.ede.safe.ui.composable

import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

@Composable
fun PersistPasswordEntryChanges(
    editedSiteEntry: EditableSiteEntry,
    passwordChanged: Boolean,
    onSaved: (Boolean) -> Unit
) {
    require(!TextUtils.isEmpty(editedSiteEntry.description)) { "Description must be set" }
    val encrypter = KeyStoreHelperFactory.getEncrypter()
    val siteEntry = DecryptableSiteEntry(editedSiteEntry.categoryId)
    siteEntry.apply {
        id = editedSiteEntry.id
        description = editedSiteEntry.description.encrypt(encrypter)
        website = editedSiteEntry.website.encrypt(encrypter)
        username = editedSiteEntry.username
        password = editedSiteEntry.password
        passwordChangedDate = editedSiteEntry.passwordChangedDate
        note = editedSiteEntry.note
        photo =
            if (editedSiteEntry.plainPhoto == null) IVCipherText.getEmpty()
            else editedSiteEntry.plainPhoto.encrypt(encrypter)

        if (passwordChanged) {
            passwordChangedDate = ZonedDateTime.now()
        }
    }

    // TODO: MAKE ASYNC
    //coroutineScope.launch {
    runBlocking {
        DataModel.addOrUpdateSiteEntry(siteEntry)
    }
    // TODO: What if failed
    onSaved(true)
}

@Preview(showBackground = true)
@Composable
fun PersistPasswordEntryChangesPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeTheme {
        PersistPasswordEntryChanges(
            editedSiteEntry = EditableSiteEntry(
                1,
                1,
                "desc",
                "web",
                "user".encrypt(),
                "pass".encrypt(),
                "note".encrypt(),
                null,
                null
            ), passwordChanged = false
        ) { _ -> }
    }
}