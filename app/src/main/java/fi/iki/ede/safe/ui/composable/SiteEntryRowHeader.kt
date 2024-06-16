package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTheme

/**
 * Display nice big start letter of the given header string
 */
@Composable
fun SiteEntryRowHeader(headerString: String) {
    val headerStart = headerString.substring(0, 1).uppercase()
    val safeTheme = LocalSafeTheme.current

    SafeListItem(
        fillWidthFraction = 0.4f,
        //modifier = Modifier.alpha(0.7f),
        // TODO: really ugly :(
        yOffset = (10).dp
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = headerStart,
                modifier = Modifier
                    .padding(14.dp),
                style = safeTheme.customFonts.listHeaders
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SiteEntryRowHeaderPreview() {
    SafeTheme {
        SiteEntryRowHeader("A")
    }
}