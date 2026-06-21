package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.TestTag
import kotlinx.coroutines.job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AddOrEditCategory(
    categoryName: String,
    titleText: String,
    modifier: Modifier = Modifier,
    onSubmit: (text: String) -> Unit
) {
    var newCategory by remember { mutableStateOf(categoryName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            delay(150)
        }
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = { onSubmit(newCategory) },
        content = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = titleText, style = MaterialTheme.typography.titleMedium)
                    
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag(TestTag.CATEGORY_TEXT_FIELD)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            modifier = Modifier.testTag(TestTag.CATEGORY_BUTTON),
                            onClick = { onSubmit(newCategory) }
                        ) {
                            Text(text = getString("generic_ok"))
                        }
                    }
                }
            }
        }
    )
}
