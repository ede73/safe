package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.safe.model.DataModel.getCategory
import fi.iki.ede.safe.ui.activities.PasswordEntryScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SearchPasswordEntryList(
    filteredPasswords: MutableStateFlow<List<DecryptablePasswordEntry>>
) {
    val context = LocalContext.current
    val passwordState = filteredPasswords.collectAsState()
    val sortedPasswords by remember(passwordState) {
        derivedStateOf {
            passwordState.value.sortedBy { it.plainDescription }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = sortedPasswords, itemContent = { filteredItem ->
            MatchingPasswordEntry(
                passwordEntry = filteredItem,
                categoryEntry = filteredItem.getCategory()
            ) {
                context.startActivity(
                    PasswordEntryScreen.getEditPassword(context, it.id!!)
                )
            }
        })
    }
}
