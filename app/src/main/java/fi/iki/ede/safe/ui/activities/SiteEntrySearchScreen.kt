package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.safe.ui.composable.SiteEntrySearchCompose
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalFoundationApi
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
@ExperimentalTime
@ExperimentalFoundationApi
private fun SiteEntrySearchPreview() {
    MockKeyStoreHelper.init()
    SiteEntrySearchCompose()
}