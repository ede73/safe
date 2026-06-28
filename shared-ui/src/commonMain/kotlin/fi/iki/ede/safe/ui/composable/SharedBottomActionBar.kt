package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import fi.iki.ede.theme.SafeTheme

@Composable
fun SharedBottomActionBar(
    onAddRequested: () -> Unit = {},
    onLockRequested: () -> Unit = {},
    onSearchRequested: () -> Unit = {},
    onSettingsRequested: () -> Unit = {},
    onHelpRequested: () -> Unit = {},
    onChangeMasterPasswordRequested: () -> Unit = {},
    onShowTrashRequested: () -> Unit = {},
    onImportExportRequested: () -> Unit = {}
) {
    var displayMenu by remember { mutableStateOf(false) }

    BottomAppBar(
        actions = {
            IconButton(onClick = onAddRequested) {
                    Icon(Icons.Default.Add, getString("generic_add"))
                }
                IconButton(onClick = onLockRequested) {
                    Icon(Icons.Default.Lock, getString("action_bar_lock"))
                }
                IconButton(onClick = onSearchRequested) {
                    Icon(Icons.Default.Search, getString("action_bar_search"))
                }
                Box {
                    IconButton(onClick = { displayMenu = !displayMenu }) {
                        Icon(Icons.Default.MoreVert, "More actions")
                    }
                    DropdownMenu(
                        expanded = displayMenu,
                        onDismissRequest = { displayMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = getString("action_bar_settings")) },
                            onClick = {
                                displayMenu = false
                                onSettingsRequested()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = getString("action_bar_help")) },
                            onClick = {
                                displayMenu = false
                                onHelpRequested()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = getString("action_bar_change_master_password")) },
                            onClick = {
                                displayMenu = false
                                onChangeMasterPasswordRequested()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = getString("action_bar_show_trash")) },
                            onClick = {
                                displayMenu = false
                                onShowTrashRequested()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = getString("action_bar_import_export")) },
                            onClick = {
                                displayMenu = false
                                onImportExportRequested()
                            }
                        )
                    }
                }
            }
        )
}
