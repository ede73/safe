package fi.iki.ede.safe.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeThemeSurface

@Composable
fun DeleteSiteEntryDialog(
    siteEntry: DecryptableSiteEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { }, confirmButton = {
            SafeButton(onClick = {
                onConfirm()
            }) {
                Text(text = stringResource(id = R.string.generic_yes_delete))
            }
        }, dismissButton = {
            SafeButton(onClick = {
                onDismiss()
            }) {
                Text(text = stringResource(R.string.generic_dont_delete))
            }
        }, title = {
            Text(
                text = stringResource(
                    id = R.string.password_list_delete, siteEntry.cachedPlainDescription
                )
            )
        })
}

@DualModePreview
@Composable
fun DeleteSiteEntryDialogPreview() {
    SafeThemeSurface {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val site = DecryptableSiteEntry(1).apply {
            description = encrypter("Description".toByteArray())
        }
        DeleteSiteEntryDialog(site, {}, {})
    }
}