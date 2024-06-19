package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.android.material.textview.MaterialTextView
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun HelpViewer(sourceString: String, modifier: Modifier = Modifier) {
    val spannedText = HtmlCompat.fromHtml(sourceString, 0)
    AndroidView(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag(TestTag.TAG_HELP),
        factory = { MaterialTextView(it) },
        update = { it.text = spannedText }
    )
}

@Preview(showBackground = true)
@Composable
fun HelpViewerPreview() {
    SafeTheme {
        HelpViewer("<h1>Hello</h1>")
    }
}
