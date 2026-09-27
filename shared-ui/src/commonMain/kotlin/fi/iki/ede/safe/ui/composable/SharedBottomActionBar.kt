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
    onImportExportRequested: () -> Unit = {},
    onPeerSyncRequested: () -> Unit = {},
    onSeedAndroidDbRequested: () -> Unit = {},
    onSeedIosDbRequested: () -> Unit = {},
    onTestConflictMatrixRequested: () -> Unit = {},
    onResolveLocalConflictsRequested: () -> Unit = {}
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
                    SharedMenuItems(
                        isLoggedIn = true,
                        onPeerSyncRequested = onPeerSyncRequested,
                        onSettingsRequested = onSettingsRequested,
                        onHelpRequested = onHelpRequested,
                        onChangeMasterPasswordRequested = onChangeMasterPasswordRequested,
                        onShowTrashRequested = onShowTrashRequested,
                        onImportExportRequested = onImportExportRequested,
                        onSeedAndroidDbRequested = onSeedAndroidDbRequested,
                        onSeedIosDbRequested = onSeedIosDbRequested,
                        onTestConflictMatrixRequested = onTestConflictMatrixRequested,
                        onResolveLocalConflictsRequested = onResolveLocalConflictsRequested,
                        onDismiss = { displayMenu = false }
                    )
                }
            }
        }
    )
}
