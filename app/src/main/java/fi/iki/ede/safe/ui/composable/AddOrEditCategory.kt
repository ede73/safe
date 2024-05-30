package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.job

@Composable
fun AddOrEditCategory(
    categoryName: String,
    textId: Int,
    onSubmit: (text: String) -> Unit
) {
    var newCategory by remember { mutableStateOf(categoryName) }

    // Initial focus on first category edit field..
    val focusRequester = remember { FocusRequester() }

    // nuts...gotta wait before focus works..sounds IFFY!
    LaunchedEffect(Unit) {
        awaitFrame()
        awaitFrame()
        this.coroutineContext.job.invokeOnCompletion {
            focusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = { onSubmit(newCategory) },
        content = {
            Column {
                Text(text = stringResource(id = textId))
                TextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag(TestTag.TEST_TAG_CATEGORY_TEXT_FIELD)
                        .focusable()
                        .focusTarget()
                )
                Button(
                    onClick = { onSubmit(newCategory) },
                    modifier = Modifier.testTag(TestTag.TEST_TAG_CATEGORY_BUTTON)
                ) {
                    Text(text = stringResource(id = R.string.generic_ok))
                }
            }
        }
    )
}