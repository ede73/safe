package fi.iki.ede.safe.ui.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun DeleteCategoryDialog(
    category: DecryptableCategoryEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            SafeButton(
                onClick = {
                    onConfirm()
                },
                modifier = Modifier.testTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE_CONFIRM)
            ) {
                Text(text = stringResource(R.string.generic_yes_delete))
            }
        },
        dismissButton = {
            SafeButton(
                onClick = {
                    onDismiss()
                },
                modifier = Modifier.testTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE_CANCEL)
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
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val cat = DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        DeleteCategoryDialog(cat, {}, {})
    }
}