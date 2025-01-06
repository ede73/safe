package fi.iki.ede.safe.gpmui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmui.composables.ImportGpmCsvComposable
import fi.iki.ede.gpmui.composables.VisualizeChangeSetPager
import fi.iki.ede.gpmui.getFakeDataModel
import fi.iki.ede.gpmui.models.GPMDataModel
import fi.iki.ede.gpmui.utilities.makeIncomingForTesting
import fi.iki.ede.gpmui.utilities.makeSavedForTesting
import fi.iki.ede.safe.model.DataModelForGPM
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.theme.SafeTheme

class ImportNewGpmsScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasUnlinkedItemsFromPreviousRound =
            GPMDataModel.unprocessedGPMsFlow.value.isNotEmpty()

        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    ImportGpmCsvComposable(
                        DataModelForGPM,
                        ::avertInactivity,
                        hasUnlinkedItemsFromPreviousRound
                    ) {
                        // done!
                        AddIgnoreMergeGpmsAndSiteEntriesScreen.startMe(this)
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, ImportNewGpmsScreen::class.java))
        }
    }
}

@Composable
@Preview(showBackground = true)
fun ImportGooglePasswordsPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    MaterialTheme {
        Column {
            ImportGpmCsvComposable(getFakeDataModel(), null, true) {}

            HorizontalDivider(modifier = Modifier.padding(20.dp))

            val incoming = setOf<IncomingGPM>(
                makeIncomingForTesting("Incoming1"),
                makeIncomingForTesting("Incoming2"),
            )
            val saved = setOf<SavedGPM>(
                makeSavedForTesting(1, "Saved1"),
                makeSavedForTesting(2, "Saved2")
            )
            val a = makeIncomingForTesting("Incoming3") to ScoredMatch(
                0.5,
                makeSavedForTesting(3, "Saved3"),
                true
            )
            val b = makeIncomingForTesting("Incoming4") to ScoredMatch(
                0.7,
                makeSavedForTesting(4, "Saved4"),
                false
            )
            val matches = mutableSetOf<Pair<IncomingGPM, ScoredMatch>>(a, b)
            val import = ImportChangeSet(incoming, saved, matches)
            val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(import) }

            VisualizeChangeSetPager(importChangeSet, {})
        }
    }
}