package fi.iki.ede.gpmui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import fi.iki.ede.gpmui.DataModelIF
import fi.iki.ede.gpmui.R
import fi.iki.ede.gpmui.TestTag
import fi.iki.ede.gpmui.getFakeDataModel
import fi.iki.ede.gpmui.models.ImportGPMViewModel
import fi.iki.ede.gpmui.models.SearchTarget
import fi.iki.ede.gpmui.testTag
import fi.iki.ede.theme.TextualCheckbox
import java.util.regex.PatternSyntaxException

@Composable
fun AddIgnoreMergeGpmsAndSiteEntriesControls(
    datamodel: DataModelIF,
    viewModel: ImportGPMViewModel,
) {
    val isWorkingAndProgress by viewModel.isWorkingAndProgress.observeAsState(false to null as Float?)
    val searchPasswords = remember { mutableStateOf(ToggleableState.Indeterminate) }
    val searchGPMs = remember { mutableStateOf(ToggleableState.Off) }
    val startSearchingMatchingNames = remember { mutableStateOf(false) }
    val startSearchingMatchingPasswords = remember { mutableStateOf(false) }
    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    var isRegExError by remember { mutableStateOf(false) }
    var isRegularExpression by remember { mutableStateOf(false) }
    var searchTextField by remember { mutableStateOf(TextFieldValue("")) }
    var similarityScore by remember { mutableFloatStateOf(0.0f) }

    fun toggleableStateToSearchTarget(input: ToggleableState) =
        when (input) {
            ToggleableState.On -> SearchTarget.SEARCH_FROM_DISPLAYED
            ToggleableState.Off -> SearchTarget.IGNORE
            ToggleableState.Indeterminate -> SearchTarget.SEARCH_FROM_ALL
        }

    fun initiateSearch() {
        if (isRegularExpression && isRegExError) return
        val searchTerm = searchTextField.text.toLowerCasedTrimmedString()
        if (searchTerm.lowercasedTrimmed.isEmpty()) return
        val regex = if (isRegularExpression) Regex(
            searchTerm.lowercasedTrimmed,
            RegexOption.IGNORE_CASE
        ) else null

        viewModel.launchSearch(
            toggleableStateToSearchTarget(searchPasswords.value),
            toggleableStateToSearchTarget(searchGPMs.value),
            if (isRegularExpression) 0.0 else similarityScore.toDouble() / 100,
            searchTextField.text,
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
                Button(onClick = { viewModel.cancelAllJobs() }) {
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
            Slider(
                modifier = Modifier.weight(0.7f),
                value = similarityScore,
                onValueChange = { similarityScore = it }, valueRange = 0.0f..100.0f,
                steps = 50, onValueChangeFinished = {
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
            Box(modifier = Modifier.weight(1f)) {
                Row {
                    TriStateCheckbox(
                        modifier = Modifier.padding(10.dp),
                        state = searchPasswords.value,
                        onClick = {
                            searchPasswords.value =
                                ToggleableState.entries[(searchPasswords.value.ordinal + 1) % 3]
                        },
                    )
                    when (toggleableStateToSearchTarget(searchPasswords.value)) {
                        SearchTarget.SEARCH_FROM_DISPLAYED -> Text(stringResource(R.string.google_password_import_search_from_displayed_passwords))
                        SearchTarget.SEARCH_FROM_ALL -> Text(stringResource(R.string.google_password_import_search_from_all_passwords))
                        SearchTarget.IGNORE -> Text(stringResource(R.string.google_password_import_dont_search_from_passwords))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Row {
                    TriStateCheckbox(
                        modifier = Modifier.padding(10.dp),
                        state = searchGPMs.value,
                        onClick = {
                            searchGPMs.value =
                                ToggleableState.entries[(searchGPMs.value.ordinal + 1) % 3]
                        },
                    )
                    when (toggleableStateToSearchTarget(searchGPMs.value)) {
                        SearchTarget.SEARCH_FROM_DISPLAYED -> Text(stringResource(R.string.google_password_import_search_from_displayed_imports))
                        SearchTarget.SEARCH_FROM_ALL -> Text(stringResource(R.string.google_password_import_search_from_all_imports))
                        SearchTarget.IGNORE -> Text(stringResource(R.string.google_password_import_dont_search_from_imports))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            TextualCheckbox(
                startSearchingMatchingPasswords,
                R.string.google_password_import_locate_matching_passwords,
                modifier = Modifier.weight(1f),
            ) { checked ->
                if (checked) {
                    viewModel.launchMatchingPasswordSearchAndResetDisplayLists()
                } else {
                    viewModel.cancelAllJobs {}
                }
            }
            TextualCheckbox(
                startSearchingMatchingNames,
                R.string.google_password_import_locate_matching_names,
                modifier = Modifier.weight(1f),
            ) { checked ->
                if (checked) {
                    viewModel.launchMatchingNameSearchAndResetDisplayLists(
                        datamodel,
                        similarityScore.toDouble() / 100
                    )
                } else {
                    viewModel.cancelAllJobs {
                        //viewModel.resetDisplayLists()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImportControlsPreview() {
    val fakeModel = getFakeDataModel()

    val fakeViewModel = ImportGPMViewModel(fakeModel).apply {
    }
    MaterialTheme {
        AddIgnoreMergeGpmsAndSiteEntriesControls(fakeModel, fakeViewModel)
    }
}