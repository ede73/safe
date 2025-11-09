package fi.iki.ede.safe.ui.composable

import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.cryptoobjects.encrypt
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.theme.SafeThemeSurface
import kotlinx.coroutines.runBlocking
import okio.Buffer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
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
            passwordChangedDate = Clock.System.now()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun convertToJpegAndEncrypt(
    plainPhoto: Bitmap,
    encrypter: (ByteArray) -> IVCipherText
) = Buffer().apply {
    plainPhoto.compress(Bitmap.CompressFormat.JPEG, 60, this.outputStream())
}.let { buffer ->
    Base64.encodeToString(buffer.readByteArray(), Base64.DEFAULT).encrypt(encrypter)
}


@OptIn(ExperimentalTime::class)
@DualModePreview
@Composable
fun PersistPasswordEntryChangesPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeThemeSurface {
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