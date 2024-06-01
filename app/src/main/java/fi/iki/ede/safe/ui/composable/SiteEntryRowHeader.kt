package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem

/**
 * Display nice big start letter of the given header string
 */
@Composable
fun SiteEntryRowHeader(headerString: String) {
    val headerStart = headerString.substring(0, 1).uppercase()
    val safeTheme = LocalSafeTheme.current

    SafeListItem {
        Row(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
        ) {

            Text(
                text = headerStart,
                modifier = Modifier.padding(16.dp),
                style = safeTheme.customFonts.listHeaders
            )
        }
    }
}
