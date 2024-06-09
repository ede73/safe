package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun TextualCheckbox(
    initiallyChecked: MutableState<Boolean>,
    textResourceId: Int,
    checkedChanged: (Boolean) -> Unit
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
    SafeTheme {
        val checked = remember { mutableStateOf(false) }
        TextualCheckbox(
            initiallyChecked = checked,
            textResourceId = R.string.login_with_biometrics
        ) {
            checked.value = !checked.value
        }
    }
}
