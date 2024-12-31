package fi.iki.ede.safe.gpm.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import fi.iki.ede.safe.autolocking.AutoLockingBaseComponentActivity
import fi.iki.ede.safe.gpm.ui.composables.AddIgnoreMergeGpmsAndSiteEntriesControls
import fi.iki.ede.safe.gpm.ui.composables.AddIgnoreMergeGpmsAndSiteEntriesList
import fi.iki.ede.safe.gpm.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.ui.theme.SafeTheme

class AddIgnoreMergeGpmsAndSiteEntriesScreen : AutoLockingBaseComponentActivity() {
    private val viewModel: ImportGPMViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        AddIgnoreMergeGpmsAndSiteEntriesControls(viewModel)
                        AddIgnoreMergeGpmsAndSiteEntriesList(viewModel)
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(
                Intent(
                    context,
                    AddIgnoreMergeGpmsAndSiteEntriesScreen::class.java
                )
            )
        }
    }
}
