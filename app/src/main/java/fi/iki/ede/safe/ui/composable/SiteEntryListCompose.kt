package fi.iki.ede.safe.ui.composable

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.theme.SafeTheme
import fi.iki.ede.theme.SafeThemeSurface
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import fi.iki.ede.datamodel.DataModel
import kotlinx.coroutines.launch

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
internal fun SiteEntryListCompose(
    context: Context?,
    category: DecryptableCategoryEntry,
    siteEntriesState: List<DecryptableSiteEntry>
) {
    val add = setupActivityResultLauncher {/*  nothing to do anymore (thanks flow!)*/ }
    val categoriesState by DataModel.categoriesStateFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

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
                SiteEntryList(
                    siteEntries = siteEntriesState,
                    categoriesState = categoriesState,
                    onSiteEntryClick = { siteEntry ->
                        context?.let { ctx ->
                            add.launch(
                                IntentManager.getEditSiteEntryIntent(
                                    ctx,
                                    siteEntry.id!!
                                )
                            )
                        }
                    },
                    onDeleteSiteEntry = { siteEntry ->
                        coroutineScope.launch {
                            DataModel.deleteSiteEntry(siteEntry)
                        }
                    },
                    onMoveSiteEntry = { siteEntry, newCategory ->
                        coroutineScope.launch {
                            DataModel.moveSiteEntry(siteEntry, newCategory)
                        }
                    }
                )
            }
        }
    }
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
private fun SiteEntryListComposePreview() {
    SafeThemeSurface {
        val (category, siteEntries) = remember {
            MockKeyStoreHelper.init()
            val category = DecryptableCategoryEntry().apply {
                id = 1L
                encryptedName = "Social Media".encrypt()
            }
            val siteEntries = listOf(
                DecryptableSiteEntry(1L).apply {
                    description = "Facebook".encrypt()
                    username = "user@example.com".encrypt()
                    passwordChangedDate = Clock.System.now()
                },
                DecryptableSiteEntry(2L).apply {
                    description = "Twitter".encrypt()
                    username = "user@example.com".encrypt()
                }
            )
            Pair(category, siteEntries)
        }
        SiteEntryListCompose(null, category, siteEntries)
    }
}
