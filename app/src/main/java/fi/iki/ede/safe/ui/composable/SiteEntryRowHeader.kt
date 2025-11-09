package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.theme.LocalSafeTheme
import fi.iki.ede.theme.SafeThemeSurface
import kotlin.time.ExperimentalTime

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

    Text(
        text = headerStart,
        modifier = Modifier
            .padding(0.dp, bottom = 15.dp)
            .zIndex(1f)
            .offset(x = 20.dp, y = (-10).dp),
        style = safeTheme.customFonts.listHeaders
    )
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun SiteEntryRowHeaderPreview() {
    SafeThemeSurface {
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