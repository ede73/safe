package fi.iki.ede.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TextualCheckbox(
    initiallyChecked: MutableState<Boolean>,
    @StringRes
    textResourceId: Int,
    modifier: Modifier = Modifier,
    checkedChanged: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = initiallyChecked.value, onCheckedChange = {
            initiallyChecked.value = it
            checkedChanged(it)
        })
        Text(text = stringResource(id = textResourceId))
    }
}

@Preview(showBackground = true)
@Composable
fun TextualCheckboxPreview() {
    MaterialTheme {
        val checked = remember { mutableStateOf(false) }
        TextualCheckbox(
            initiallyChecked = checked,
            textResourceId = R.string.just_preview_text
        ) {
            checked.value = !checked.value
        }
    }
}
