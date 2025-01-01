package fi.iki.ede.safe.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.R
import fi.iki.ede.safe.SafeApplication
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme

/**
 * Used in password views
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopActionBarForSiteEntryView(
    onGeneratePassword: (custom: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var displayMenu by remember { mutableStateOf(false) }

    SafeTheme {
        TopAppBar(
            title = {
                Text(
                    stringResource(id = R.string.application_name),
                    color = SafeTheme.colorScheme.onSurface
                )
            },
            actions = {
                IconButton(onClick = {
                    SafeApplication.lockTheApplication(context)
                }, modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_LOCK)) {
                    Icon(Icons.Default.Lock, stringResource(id = R.string.action_bar_lock))
                }

                // Creating Icon button for dropdown menu
                IconButton(
                    onClick = { displayMenu = !displayMenu },
                    modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_MENU)
                ) {
                    Icon(Icons.Default.MoreVert, "")
                }

                DropdownMenu(
                    expanded = displayMenu,
                    onDismissRequest = { displayMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = R.string.action_bar_generate_password)) },
                        onClick = {
                            displayMenu = false
                            onGeneratePassword(false)
                        },
                        modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_GENERATE_PASSWORD)
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = R.string.action_bar_generate_custom_password)) },
                        onClick = {
                            displayMenu = false
                            onGeneratePassword(true)
                        },
                        modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_GENERATE_PASSWORD)
                    )
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopActionBarForSiteEntryViewPreview() {
    TopActionBarForSiteEntryView {}
}
