package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.datamodel.DataModel.siteEntriesStateFlow
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.safe.ui.composable.SiteEntryListCompose
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalFoundationApi
class SiteEntryListScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {

    private var categoryId = -1L

    @Suppress("FlowOperatorInvokedInComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryId =
            savedInstanceState?.getLong(CATEGORY_ID) ?: intent.getLongExtra(CATEGORY_ID, -1)
        require(categoryId != -1L) { "You have to pass a proper category" }
        SetupNotifications.setup(this)
        val category = DataModel.categoriesStateFlow.value.first { it.id == categoryId }
        setContent {
            val context = LocalContext.current
            // TODO: Either new kotlin, coroutines or both, this is a linter error now
            val siteEntriesState by siteEntriesStateFlow
                .map { passwords -> passwords.filter { it.categoryId == categoryId } }
                .map { passwords -> passwords.sortedBy { it.cachedPlainDescription.lowercase() } }
                .filterNotNull()
                .collectAsState(initial = emptyList())

            SiteEntryListCompose(context, category, siteEntriesState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(CATEGORY_ID, categoryId)
    }

    companion object {
        const val CATEGORY_ID = "category_id"
    }
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun SiteEntryListScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }

    val flow = listOf(DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("Android".toByteArray())
    }, DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("iPhone".toByteArray())
    })
    val category = DecryptableCategoryEntry().apply {
        id = 1
        encryptedName = KeyStoreHelperFactory.getEncrypter()("Category".toByteArray())
    }
    SiteEntryListCompose(null, category, flow)
}