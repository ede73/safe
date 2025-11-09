package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.theme.SafeThemeSurface
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun CategoryList(categories: List<DecryptableCategoryEntry>) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        categories.forEach { category ->
            CategoryRow(category)
        }
    }
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun CategoryListPreview() {
    SafeThemeSurface {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val cat = DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        CategoryList(listOf(cat, cat, cat))
    }
}