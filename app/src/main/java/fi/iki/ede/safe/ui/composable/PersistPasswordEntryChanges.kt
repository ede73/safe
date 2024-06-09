package fi.iki.ede.safe.ui.composable

import android.text.TextUtils
import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

@Composable
fun PersistPasswordEntryChanges(
    edits: EditableSiteEntry,
    passwordChanged: Boolean,
    onSaved: (Boolean) -> Unit
) {
    require(!TextUtils.isEmpty(edits.description)) { "Description must be set" }
    val encrypter = KeyStoreHelperFactory.getEncrypter()
    val passwordEntry = DecryptableSiteEntry(edits.categoryId)
    passwordEntry.apply {
        id = edits.id
        description = edits.description.encrypt(encrypter)
        website = edits.website.encrypt(encrypter)
        username = edits.username
        password = edits.password
        passwordChangedDate = edits.passwordChangedDate
        note = edits.note
        photo =
            if (edits.plainPhoto == null) IVCipherText.getEmpty()
            else edits.plainPhoto.encrypt(encrypter)

        if (passwordChanged) {
            passwordChangedDate = ZonedDateTime.now()
        }
    }

    // TODO: MAKE ASYNC
    //coroutineScope.launch {
    runBlocking {
        DataModel.addOrUpdatePassword(passwordEntry)
    }
    // TODO: What if failed
    onSaved(true)
}