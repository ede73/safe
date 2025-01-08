package fi.iki.ede.safe.ui.composable

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.theme.SafeTheme

@Composable
internal fun SiteEntryListCompose(
    context: Context?,
    category: DecryptableCategoryEntry,
    siteEntriesState: List<DecryptableSiteEntry>
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
                            IntentManager.getAddPassword(context, categoryId = category.id as DBID)
                        )
                    }
                }, title = category.plainName)
                SiteEntryList(siteEntriesState)
                // last row
            }
        }
    }
}