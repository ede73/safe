package fi.iki.ede.safe.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag

@Composable
fun BottomActionBarForSiteEntryView(
    onLock: () -> Unit,
    onGeneratePassword: (custom: Boolean) -> Unit = {},
) {
    var displayMenu by remember { mutableStateOf(false) }

    BottomAppBar(
        actions = {
            IconButton(
                onClick = onLock,
                modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_LOCK)
            ) {
                Icon(Icons.Default.Lock, getString("action_bar_lock"))
            }

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
                    text = { Text(text = getString("action_bar_generate_password")) },
                    onClick = {
                        displayMenu = false
                        onGeneratePassword(false)
                    },
                    modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_GENERATE_PASSWORD)
                )
                DropdownMenuItem(
                    text = { Text(text = getString("action_bar_generate_custom_password")) },
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
