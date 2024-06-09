package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun ImportControls(
    viewModel: ImportGPMViewModel? = null,
    isLoading: Boolean,
) {
    val searchFromBeingImported = remember { mutableStateOf(false) }
    val searchFromMyOwn = remember { mutableStateOf(false) }
    val showOnlyMatchingPasswords = remember { mutableStateOf(false) }
    val showOnlyMatchingNames = remember { mutableStateOf(false) }
    var searchTextField by remember { mutableStateOf(TextFieldValue("")) }
    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    val similarityScore = 0 // TODO: make configurable

    fun initiateSearch() {
        val search = searchTextField.text.toLowerCasedTrimmedString()
        if (search.lowercasedTrimmed.isEmpty()) return
        viewModel?.search(
            similarityScore.toDouble(),
            searchTextField.text.toLowerCasedTrimmedString(),
            searchFromMyOwn.value,
            searchFromBeingImported.value
        )
    }

    Column {
        val iconPadding = Modifier
            .padding(15.dp)
            .size(24.dp)
        Row {
            if (isLoading) {
                Button(onClick = { viewModel?.cancelOperation() }) {
                    Text(text = "Cancel...")
                }
                CircularProgressIndicator()
            }
            TextField(
                value = searchTextField,
                onValueChange = { value ->
                    searchTextField = value
                    if (value.text != hackToInvokeSearchOnlyIfTextValueChanges.text) {
                        hackToInvokeSearchOnlyIfTextValueChanges = value
                        initiateSearch()
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
                    if (searchTextField != TextFieldValue("")) {
                        IconButton(onClick = { searchTextField = TextFieldValue("") }) {
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
        }
        Row {
            TextualCheckbox(
                searchFromMyOwn,
                R.string.google_password_import_search_from_mine
            ) {
                initiateSearch()
            }
            TextualCheckbox(
                searchFromBeingImported,
                R.string.google_password_import_search_from_imports
            ) {
                initiateSearch()
            }
        }
        Row {
            TextualCheckbox(
                showOnlyMatchingPasswords,
                R.string.google_password_import_matching_passwords
            ) { checked ->
                if (checked) {
                    viewModel?.applyMatchingPasswords()
                } else {
                    viewModel?.cancelOperation()
                    viewModel?.clearMatchingPasswords()
                }
            }
            TextualCheckbox(
                showOnlyMatchingNames,
                R.string.google_password_import_matching_names
            ) { checked ->
                if (checked) {
                    viewModel?.applyMatchingNames()
                } else {
                    viewModel?.cancelOperation()
                    viewModel?.clearMatchingNames()
                }
            }
        }
        // TODO: Search from what is being displayed?
    }
}

@Preview(showBackground = true)
@Composable
fun ImportControlsPreview() {
    SafeTheme {
        val searchTextField = remember { mutableStateOf(TextFieldValue("")) }
        ImportControls(null, true)
    }
}
