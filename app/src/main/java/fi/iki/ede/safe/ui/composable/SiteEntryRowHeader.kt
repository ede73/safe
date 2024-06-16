package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTheme

fun Color.darken(factor: Float): Color {
    return copy(alpha = alpha * factor)
}

/**
 * Display nice big start letter of the given header string
 */
@Composable
fun SiteEntryRowHeader(headerString: String) {
    val headerStart = headerString.substring(0, 1).uppercase()
    val safeTheme = LocalSafeTheme.current

    SafeListItem(
        fillWidthFraction = 0.2f,
        yOffset = 32.dp,
        color = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.darken(0.8f),
        )
    ) {
        Text(
            text = headerStart,
            modifier = Modifier
                .padding(14.dp),
            style = safeTheme.customFonts.listHeaders
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SiteEntryRowHeaderPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val site1 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description1".toByteArray())
        }
        val site2 = DecryptableSiteEntry(2).apply {
            description = encrypter("Description1".toByteArray())
        }
        val cat = DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        Column {
            SiteEntryRowHeader("Q")
            SiteEntryRow(site1, listOf(cat))
            SiteEntryRow(site2, listOf(cat))
        }
    }
}