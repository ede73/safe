package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource

@Composable
fun TextualCheckbox(
    initiallyChecked: MutableState<Boolean>,
    textResourceId: Int,
    startSearch: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = initiallyChecked.value, onCheckedChange = {
            initiallyChecked.value = it
            startSearch()
        })
        Text(text = stringResource(id = textResourceId))
    }
}