package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.safe.ui.activities.PasswordSearchScreen

@Composable
fun MatchingPasswordEntry(
    passwordEntry: DecryptablePasswordEntry,
    categoryEntry: DecryptableCategoryEntry,
    onEntryClick: (DecryptablePasswordEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = { onEntryClick(passwordEntry) })
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp), modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)

        ) {
            Text(
                text = categoryEntry.plainName,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(8.dp)
            )
            Text(
                text = passwordEntry.plainDescription, modifier = Modifier
                    .padding(8.dp)
                    .testTag(
                        PasswordSearchScreen.TESTTAG_SEARCH_MATCH
                    )
            )
        }
    }
}