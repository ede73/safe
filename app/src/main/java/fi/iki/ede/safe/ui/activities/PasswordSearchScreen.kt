package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.safe.ui.composable.SearchPasswordAndControls
import fi.iki.ede.safe.ui.composable.SearchPasswordEntryList
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.flow.MutableStateFlow

class PasswordSearchScreen : AutoLockingComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val searchText = remember { mutableStateOf(TextFieldValue("")) }
                    val filteredPasswords =
                        remember { MutableStateFlow<List<DecryptablePasswordEntry>>(emptyList()) }
                    Column {
                        SearchPasswordAndControls(
                            filteredPasswords,
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

                        SearchPasswordEntryList(filteredPasswords)
                    }
                }
            }
        }
    }

    companion object {
        const val TESTTAG_SEARCH_TEXTFIELD = "password_search_input"
        const val TESTTAG_SEARCH_MATCH = "password_search_match"

        // minimum threshold for threaded search, else it's just single thread/linear
        const val MIN_PASSWORDS_FOR_THREADED_SEARCH = 20
        var searchProgressPerThread = mutableStateListOf<Float>()
        fun startMe(context: Context) {
            context.startActivity(Intent(context, PasswordSearchScreen::class.java))
        }
    }
}
