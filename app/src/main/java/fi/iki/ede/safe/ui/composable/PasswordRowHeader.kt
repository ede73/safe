package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Display nice big start letter of the given header string
 */
@Composable
fun PasswordRowHeader(headerString: String) {
    val headerStart = headerString.substring(0, 1).uppercase()
    Card(
        modifier = Modifier.padding(6.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth()
        ) {

            Text(
                text = headerStart,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
        }
    }
}
