package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
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
import fi.iki.ede.safe.gpm.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.TextualCheckbox
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import java.util.regex.PatternSyntaxException

@Composable
fun ImportControls(
    viewModel: ImportGPMViewModel,
) {
    val isWorkingAndProgress by viewModel.isWorkingAndProgress.observeAsState(false to null as Float?)

    val searchFromDisplayedGPMs = remember { mutableStateOf(false) }
    val searchFromDisplayedPasswords = remember { mutableStateOf(false) }
    val showOnlyMatchingNames = remember { mutableStateOf(false) }
    val showOnlyMatchingPasswords = remember { mutableStateOf(false) }
    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    var isRegExError by remember { mutableStateOf(false) }
    var isRegularExpression by remember { mutableStateOf(false) }
    var searchTextField by remember { mutableStateOf(TextFieldValue("")) }
    var similarityScore by remember { mutableFloatStateOf(0.5f) }

    fun initiateSearch() {
        if (isRegularExpression && isRegExError) return
        val searchTerm = searchTextField.text.toLowerCasedTrimmedString()
        if (searchTerm.lowercasedTrimmed.isEmpty()) return
        val regex = if (isRegularExpression) Regex(
            searchTerm.lowercasedTrimmed,
            RegexOption.IGNORE_CASE
        ) else null
        viewModel.launchSearch(
            if (isRegularExpression) 0.0 else similarityScore.toDouble(),
            searchTextField.text,
            searchFromDisplayedPasswords.value,
            searchFromDisplayedGPMs.value,
            regex,
        )
    }

    val validateRegex: (String) -> Boolean = { input ->
        try {
            Regex(input)
            false // No error, so return false
        } catch (e: PatternSyntaxException) {
            true // Error exists, so return true
        }
    }

    Column {
        val iconPadding = Modifier
            .padding(15.dp)
            .size(24.dp)
        Row {
            if (isWorkingAndProgress.first) {
                Button(onClick = { viewModel.launchCancelJobs() }) {
                    Text(text = "Cancel\n(${"%.1f".format(isWorkingAndProgress.second)}%)")
                }
            }
            TextField(
                value = searchTextField,
                onValueChange = { value ->
                    searchTextField = value
                    if (isRegularExpression) {
                        isRegExError = validateRegex(value.text)
                    } else {
                        isRegExError = false
                    }
                    if (value.text != hackToInvokeSearchOnlyIfTextValueChanges.text) {
                        hackToInvokeSearchOnlyIfTextValueChanges = value
                        initiateSearch()
                    }
                },
                isError = isRegExError,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTag.SEARCH_TEXT_FIELD)
                    .weight(0.9f),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "",
                        modifier = iconPadding
                    )
                },
                placeholder = {
                    if (isRegularExpression) {
                        Text(stringResource(id = R.string.google_password_import_search_regex))
                    } else {
                        Text(stringResource(id = R.string.google_password_import_search))
                    }
                },
                trailingIcon = {
                    // WTH?
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
            Checkbox(
                modifier = Modifier.weight(0.1f),
                checked = isRegularExpression,
                onCheckedChange = { isRegularExpression = it }
            )
        }
        Row {
            TextualCheckbox(
                searchFromDisplayedPasswords,
                R.string.google_password_import_search_from_displayed_passwords,
                modifier = Modifier.weight(0.5f),
            ) {
                initiateSearch()
            }
            TextualCheckbox(
                searchFromDisplayedGPMs,
                R.string.google_password_import_search_from_displayed_imports,
                modifier = Modifier.weight(0.5f),
            ) {
                initiateSearch()
            }
        }

        Row {
            Slider(enabled = !isRegularExpression,
                modifier = Modifier.weight(0.7f),
                value = similarityScore,
                onValueChange = { similarityScore = it }, valueRange = 0.0f..100.0f,
                steps = 5, onValueChangeFinished = {
                    initiateSearch()
                })
            if (similarityScore == 0.0f) {
                Text(
                    text = "Exact match",
                    modifier = Modifier.weight(0.3f)
                )
            } else {
                Text(
                    text = "Similarity: ${(similarityScore).toInt()}%",
                    modifier = Modifier.weight(0.3f)
                )
            }
        }

        Row {
            TextualCheckbox(
                showOnlyMatchingPasswords,
                R.string.google_password_import_locate_matching_passwords
            ) { checked ->
                if (checked) {
                    viewModel.launchSearchMatchingPasswords()
                } else {
                    viewModel.launchCancelJobs {
                        viewModel.clearMatchingPasswords()
                    }
                }
            }
            TextualCheckbox(
                showOnlyMatchingNames,
                R.string.google_password_import_locate_matching_names
            ) { checked ->
                if (checked) {
                    viewModel.launchSearchMatchingNames()
                } else {
                    viewModel.launchCancelJobs {
                        viewModel.clearMatchingNames()
                    }
                }
            }
        }
        // TODO: Search from what is being displayed?
    }
}

@Preview(showBackground = true)
@Composable
fun ImportControlsPreview() {
    val fakeViewModel = ImportGPMViewModel().apply {
    }
    SafeTheme {
        ImportControls(fakeViewModel)
    }
}
