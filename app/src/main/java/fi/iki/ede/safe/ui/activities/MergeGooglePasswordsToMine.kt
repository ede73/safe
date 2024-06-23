package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import fi.iki.ede.safe.ui.composable.ImportControls
import fi.iki.ede.safe.ui.composable.ImportEntryList
import fi.iki.ede.safe.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity

class MergeGooglePasswordsToMine : AutolockingBaseComponentActivity() {
    private val viewModel: ImportGPMViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isLoading by viewModel.isWorking.observeAsState(false to null as Float?)
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val searchText = remember { mutableStateOf(TextFieldValue("")) }
                    Column {
                        ImportControls(
                            viewModel,
                            isLoading,
                        )
                        ImportEntryList(viewModel)
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, MergeGooglePasswordsToMine::class.java))
        }
    }
}
