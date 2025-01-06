package fi.iki.ede.safe.gpmui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.gpmui.composables.AllowUserToMatchAndMergeImportedGpmsAndSiteEntriesControls
import fi.iki.ede.gpmui.composables.AllowUserToMatchAndMergeImportedGpmsAndSiteEntriesList
import fi.iki.ede.gpmui.models.ImportGPMViewModel
import fi.iki.ede.gpmui.models.ImportGPMViewModelFactory
import fi.iki.ede.safe.model.DataModelForGPM
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.theme.SafeTheme

class AddIgnoreMergeGpmsAndSiteEntriesScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    //private val viewModel: ImportGPMViewModel by viewModels()
    private lateinit var viewModel: ImportGPMViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ImportGPMViewModelFactory(DataModelForGPM)
        viewModel = ViewModelProvider(this, factory).get(ImportGPMViewModel::class.java)

        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        AllowUserToMatchAndMergeImportedGpmsAndSiteEntriesControls(
                            DataModelForGPM,
                            viewModel
                        )
                        AllowUserToMatchAndMergeImportedGpmsAndSiteEntriesList(
                            DataModelForGPM,
                            viewModel,
                        )
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
