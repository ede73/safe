package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.ui.composable.HelpViewer
import fi.iki.ede.safe.ui.theme.SafeTheme
import java.io.InputStream

class HelpScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HelpViewer(readHelp(this))
                }
            }
        }
    }

    private fun readHelp(context: Context): String {
        val inputStream: InputStream = context.assets.open("help.html")
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        return String(buffer)
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, HelpScreen::class.java))
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