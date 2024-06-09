package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun ImportControls(
    searchTextField: MutableState<TextFieldValue>,
    showOnlyMatchingPasswordsCallback: (Boolean) -> Unit = {},
    showOnlyMatchingNamesCallback: (Boolean) -> Unit = {},
) {
    val searchFromBeingImported = remember { mutableStateOf(false) }
    val searchFromMyOwn = remember { mutableStateOf(false) }
    val showOnlyMatchingPasswords = remember { mutableStateOf(false) }
    val showOnlyMatchingNames = remember { mutableStateOf(false) }

    fun findNow(checked: Boolean) {
        println("Findnow $checked")
    }

    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    Column {
        val iconPadding = Modifier
            .padding(15.dp)
            .size(24.dp)
        TextField(
            value = searchTextField.value,
            onValueChange = { value ->
                searchTextField.value = value
                if (value.text != hackToInvokeSearchOnlyIfTextValueChanges.text) {
                    hackToInvokeSearchOnlyIfTextValueChanges = value
                    //findNow()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "",
                    modifier = iconPadding
                )
            },
            placeholder = { Text(stringResource(id = R.string.google_password_import_search)) },
            trailingIcon = {
                if (searchTextField.value != TextFieldValue("")) {
                    IconButton(onClick = { searchTextField.value = TextFieldValue("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "",
                            modifier = iconPadding
                        )
                    }
                }
            },
            singleLine = true,
            shape = RectangleShape
        )
        Row {
            TextualCheckbox(
                searchFromMyOwn,
                R.string.google_password_import_search_from_mine,
                ::findNow
            )
            TextualCheckbox(
                searchFromBeingImported,
                R.string.google_password_import_search_from_imports,
                ::findNow
            )
        }
        Row {
            TextualCheckbox(
                showOnlyMatchingPasswords,
                R.string.google_password_import_matching_passwords,
                showOnlyMatchingPasswordsCallback
            )
            TextualCheckbox(
                showOnlyMatchingNames,
                R.string.google_password_import_matching_names,
                showOnlyMatchingNamesCallback
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImportControlsPreview() {
    SafeTheme {
        val searchTextField = remember { mutableStateOf(TextFieldValue("")) }
        ImportControls(searchTextField)
    }
}
