package fi.iki.ede.safe.ui.composable

import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.cryptoobjects.encrypt
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
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
        photo = if (editedSiteEntry.plainPhoto == null) IVCipherText.getEmpty()
        else convertToJpegAndEncrypt(editedSiteEntry.plainPhoto, encrypter)

        if (passwordChanged) {
            passwordChangedDate = ZonedDateTime.now()
        }

        siteEntry.extensions = siteEntry.encryptExtension(editedSiteEntry.plainExtension)
    }

    // TODO: MAKE ASYNC
    //coroutineScope.launch {
    runBlocking {
        DataModel.addOrUpdateSiteEntry(siteEntry)
    }
    // TODO: What if failed
    onSaved(true)
}

@Composable
private fun convertToJpegAndEncrypt(
    plainPhoto: Bitmap,
    encrypter: (ByteArray) -> IVCipherText
) = ByteArrayOutputStream().let {
    plainPhoto.compress(Bitmap.CompressFormat.JPEG, 60, it)
    Base64.encodeToString(it.toByteArray(), Base64.DEFAULT).encrypt(encrypter)
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
                null,
                mutableMapOf()
            ), passwordChanged = false
        ) { _ -> }
    }
}