package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen.Companion.searchProgressPerThread
import fi.iki.ede.safe.ui.composable.SearchSiteEntryControls
import fi.iki.ede.safe.ui.composable.SearchSiteEntryList
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.flow.MutableStateFlow

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

@Composable
private fun SiteEntrySearchCompose() {
    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            val searchText = remember { mutableStateOf(TextFieldValue("")) }
            val matchingPasswordEntries =
                remember {
                    MutableStateFlow<List<DecryptableSiteEntry>>(
                        emptyList()
                    )
                }
            Column {
                SearchSiteEntryControls(
                    matchingPasswordEntries,
                    searchText
                )

                searchProgressPerThread.indices.forEach { index ->
                    LinearProgressIndicator(
                        progress = {
                            searchProgressPerThread[index]
                        },
                        modifier = Modifier
                            .height(4.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }

                // TODO: Merge with password list
                SearchSiteEntryList(matchingPasswordEntries)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SiteEntrySearchPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }

    val flow = listOf(DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("Android".toByteArray())
    }, DecryptableSiteEntry(1).apply {
        description = KeyStoreHelperFactory.getEncrypter()("iPhone".toByteArray())
    })
    SiteEntrySearchCompose()
}