package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.safe.ui.composable.SiteEntrySearchCompose

class SiteEntrySearchScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SiteEntrySearchCompose()
        }
    }

    companion object {
        // minimum threshold for threaded search, else it's just single thread/linear
        const val MIN_PASSWORDS_FOR_THREADED_SEARCH = 20
        var searchProgressPerThread = mutableStateListOf<Float>()
    }
}


@DualModePreview
@Composable
fun SiteEntrySearchPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }

    listOf(DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("Android".toByteArray())
    }, DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("iPhone".toByteArray())
    })
    SiteEntrySearchCompose()
}