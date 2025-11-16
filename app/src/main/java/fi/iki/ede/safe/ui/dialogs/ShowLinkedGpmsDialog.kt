package fi.iki.ede.safe.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.encrypt
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmui.dialogs.ShowInfoDialog
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeListItem
import fi.iki.ede.theme.SafeTextButton
import fi.iki.ede.theme.SafeThemeSurface

@Composable
fun ShowLinkedGpmsDialog(
    gpms: Set<SavedGPM>, onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = { onDismiss() },
    confirmButton = {},
    dismissButton = {
        SafeTextButton(onClick = onDismiss) {
            Text("OK")
        }
    },
    title = { Text(stringResource(id = R.string.google_password_links)) },
    text = {
        val showGpm = remember { mutableStateOf<SavedGPM?>(null) }
        if (showGpm.value != null) {
            ShowInfoDialog(showGpm.value!!, onDismiss = { showGpm.value = null })
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            gpms.forEach {
                SafeListItem {
                    Text(
                        text = it.cachedDecryptedName,
                        modifier = Modifier
                            .clickable {
                                showGpm.value = it
                            }
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag(TestTag.CATEGORY_MOVE_ROW)
                    )
                }
            }
        }
    }
)

@DualModePreview
@Composable
fun ShowLinkedGPMsPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeThemeSurface {
        val gpms = setOf(
            SavedGPM.makeFromEncryptedStringFields(
                1,
                "encryptedName".encrypt(),
                "url".encrypt(),
                "username".encrypt(),
                "password".encrypt(),
                "note".encrypt(),
                false,
                "hash"
            )
        )
        ShowLinkedGpmsDialog(gpms) {}
    }
}