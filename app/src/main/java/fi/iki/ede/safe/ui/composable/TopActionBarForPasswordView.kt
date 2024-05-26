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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.activities.AutoLockingComponentActivity

/**
 * Used in password views
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopActionBarForPasswordView(
    onGeneratePassword: () -> Unit = {},
) {
    val context = LocalContext.current
    var displayMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(id = R.string.application_name), color = Color.White) },
        actions = {
            IconButton(onClick = {
                AutoLockingComponentActivity.lockTheApplication(context)
            }) {
                Icon(Icons.Default.Lock, stringResource(id = R.string.action_bar_lock))
            }

            // Creating Icon button for dropdown menu
            IconButton(onClick = { displayMenu = !displayMenu }) {
                Icon(Icons.Default.MoreVert, "")
            }

            DropdownMenu(
                expanded = displayMenu,
                onDismissRequest = { displayMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_generate_password)) },
                    onClick = {
                        displayMenu = false
                        onGeneratePassword()
                    }
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TopActionBarForPasswordViewPreview() {
    TopActionBarForPasswordView {}
}
