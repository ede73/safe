package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import fi.iki.ede.safe.ui.composable.ImportControls
import fi.iki.ede.safe.ui.composable.ImportEntryList
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity

class ImportGooglePasswordManager : AutolockingBaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val searchText = remember { mutableStateOf(TextFieldValue("")) }
                    Column {
                        ImportControls(searchText)
                        ImportEntryList(
                            listOf(
                                "oma1",
                                "oma2",
                                "oma3",
                                "oma4",
                                "oma5",
                                "oma6",
                                "oma7",
                                "oma8",
                                "oma9",
                                "oma10",
                                "oma11",
                                "oma12",
                                "oma13",
                                "oma14",
                                "oma15",
                                "oma16"
                            ),
                            listOf("tuo1", "tuo2", "tuo3", "tuo4")
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, ImportGooglePasswordManager::class.java))
        }
    }
}
