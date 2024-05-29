package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.safe.model.DataModel

@Composable
fun SiteEntryList(passwords: List<DecryptableSiteEntry>) {

    val passwordItems = remember { mutableStateListOf<@Composable () -> Unit>() }
    val passwordListHash = remember(passwords) { passwords.hashCode() }
    val categoriesState by DataModel.categoriesStateFlow
        .collectAsState(initial = emptyList())

    LaunchedEffect(passwordListHash) {
        var previousValue = ""
        passwordItems.clear()
        passwords.forEach { password ->
            val beginning = password.plainDescription.substring(0, 1).uppercase()
            if (previousValue != beginning) {
                previousValue = beginning
                passwordItems.add { SiteEntryRowHeader(headerString = beginning) }
            }
            passwordItems.add { SiteEntryRow(password, categoriesState) }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        passwordItems.forEach { composable ->
            composable()
        }
    }
}