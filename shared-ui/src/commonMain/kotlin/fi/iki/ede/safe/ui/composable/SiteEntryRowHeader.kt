package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fi.iki.ede.theme.LocalSafeTheme

@Composable
fun SiteEntryRowHeader(
    headerString: String,
    modifier: Modifier = Modifier
) {
    val headerStart = headerString.substring(0, 1).uppercase()
    val safeTheme = LocalSafeTheme.current

    Text(
        text = headerStart,
        modifier = modifier
            .padding(0.dp, bottom = 15.dp)
            .zIndex(1f)
            .offset(x = 20.dp, y = (-10).dp),
        style = safeTheme.customFonts.listHeaders
    )
}
