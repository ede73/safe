package fi.iki.ede.safe.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag

@Composable
fun DeleteCategoryDialog(
    category: DecryptableCategoryEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            fi.iki.ede.theme.SafeButton(
                modifier = Modifier.testTag(TestTag.CATEGORY_ROW_DELETE_CONFIRM),
                onClick = {
                    onConfirm()
                }
            ) {
                Text(text = stringResource(R.string.generic_yes_delete))
            }
        },
        dismissButton = {
            fi.iki.ede.theme.SafeButton(
                modifier = Modifier.testTag(TestTag.CATEGORY_ROW_DELETE_CANCEL),
                onClick = {
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.generic_dont_delete))
            }
        },
        title = {
            Text(
                text = stringResource(
                    id = R.string.category_list_delete_confirm,
                    category.plainName
                )
            )
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DeleteCategoryDialogPreview() {
    fi.iki.ede.theme.SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val cat = DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        DeleteCategoryDialog(cat, {}, {})
    }
}