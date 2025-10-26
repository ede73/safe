package fi.iki.ede.safe.ui.composable

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.theme.SafeTheme
import fi.iki.ede.theme.SafeThemeSurface
import kotlinx.datetime.Clock

@Composable
internal fun SiteEntryListCompose(
    context: Context?,
    category: DecryptableCategoryEntry,
    siteEntriesState: List<DecryptableSiteEntry>
) {
    val add = setupActivityResultLauncher {/*  nothing to do anymore (thanks flow!)*/ }
    SafeTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomActionBar(onAddRequested = {
                    context?.let { context ->
                        add.launch(
                            IntentManager.getAddSiteEntryIntent(
                                context,
                                siteEntryId = category.id as DBID
                            )
                        )
                    }
                })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding),
            ) {
                SiteEntryList(siteEntriesState)
            }
        }
    }
}

@DualModePreview
@Composable
private fun SiteEntryListComposePreview() {
    SafeThemeSurface {
        val (category, siteEntries) = remember {
            KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
            KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
            val encrypter = KeyStoreHelperFactory.getEncrypter()
            val category = DecryptableCategoryEntry().apply {
                id = 1L
                encryptedName = encrypter("Social Media".toByteArray())
            }
            val siteEntries = listOf(
                DecryptableSiteEntry(1L).apply {
                    description = encrypter("Facebook".toByteArray())
                    username = encrypter("user@example.com".toByteArray())
                    passwordChangedDate = Clock.System.now()
                },
                DecryptableSiteEntry(2L).apply {
                    description = encrypter("Twitter".toByteArray())
                    username = encrypter("user@example.com".toByteArray())
                }
            )
            Pair(category, siteEntries)
        }
        SiteEntryListCompose(null, category, siteEntries)
    }
}
