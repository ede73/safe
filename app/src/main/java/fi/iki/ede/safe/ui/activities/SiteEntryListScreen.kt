package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.model.DataModel.passwordsStateFlow
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.composable.SiteEntryList
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class SiteEntryListScreen : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId =
            savedInstanceState?.getLong(CATEGORY_ID) ?: intent.getLongExtra(CATEGORY_ID, -1)

        setContent {
            val context = LocalContext.current
            val passwordsState by passwordsStateFlow
                .map { passwords -> passwords.filter { it.categoryId == categoryId } }
                .map { passwords -> passwords.sortedBy { it.cachedPlainDescription.lowercase() } }
                .filterNotNull()
                .collectAsState(initial = emptyList())

            SiteEntryListCompose(context, categoryId, passwordsState)
        }
    }

    companion object {
        const val CATEGORY_ID = "category_id"
    }
}

@Composable
private fun SiteEntryListCompose(
    context: Context?,
    categoryId: Long,
    passwordsState: List<DecryptableSiteEntry>
) {
    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TopActionBar(onAddRequested = {
                    context?.let { context ->
                        it.launch(
                            IntentManager.getAddPassword(context, categoryId = categoryId)
                        )
                    }
                })
                SiteEntryList(passwordsState)
                // last row
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SiteEntryListScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }

    val flow = listOf(DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("Android".toByteArray())
    }, DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("iPhone".toByteArray())
    })
    SiteEntryListCompose(null, 1, flow)
}