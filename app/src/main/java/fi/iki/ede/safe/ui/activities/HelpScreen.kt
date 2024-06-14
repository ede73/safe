package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.HelpViewer
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme

class HelpScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HelpViewer(
                        readHelp(this),
                        modifier = Modifier.testTag(TestTag.TEST_TAG_HELP)
                    )
                }
            }
        }
    }

    private fun readHelp(context: Context): String = context.assets.open("help.html").let {
        ByteArray(it.available()).let { buffer ->
            it.read(buffer)
            String(buffer)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HelpScreenPreview() {
    SafeTheme {
        HelpViewer("<html><body>Moro <b>poop</b></body></html>")
    }
}