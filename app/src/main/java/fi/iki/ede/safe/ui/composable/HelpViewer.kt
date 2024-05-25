package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.android.material.textview.MaterialTextView

@Composable
fun HelpViewer(sourceString: String, modifier: Modifier = Modifier) {
    val spannedText = HtmlCompat.fromHtml(sourceString, 0)
    AndroidView(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag("help"),
        factory = { MaterialTextView(it) },
        update = { it.text = spannedText }
    )
}