package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
internal fun SiteEntrySearchCompose() {
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

                SiteEntrySearchScreen.searchProgressPerThread.indices.forEach { index ->
                    LinearProgressIndicator(
                        progress = {
                            SiteEntrySearchScreen.searchProgressPerThread[index]
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