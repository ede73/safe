package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel.passwordsStateFlow
import fi.iki.ede.safe.ui.composable.PasswordList
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class PasswordListScreen : AutoLockingComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId =
            savedInstanceState?.getLong(CATEGORY_ID) ?: intent.getLongExtra(CATEGORY_ID, -1)

        setContent {
            val context = LocalContext.current
            val passwordsState by passwordsStateFlow
                .map { passwords -> passwords.filter { it.categoryId == categoryId } }
                .map { passwords -> passwords.sortedBy { it.plainDescription.lowercase() } }
                .filterNotNull()
                .collectAsState(initial = emptyList())

            SafeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        TopActionBar(onAddRequested = {
                            it.launch(
                                PasswordEntryScreen.getAddPassword(
                                    context,
                                    categoryId = categoryId
                                )
                            )
                        }, onAddCompleted = {
                        })
                        PasswordList(passwordsState, onRefreshEntries = {
                        })
                        // last row
                    }
                }
            }
        }
    }

    companion object {
        private const val CATEGORY_ID = "category_id"
        fun startMe(context: Context, id: DBID) {
            context.startActivity(
                Intent(
                    context, PasswordListScreen::class.java
                ).putExtra(CATEGORY_ID, id).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }
}
