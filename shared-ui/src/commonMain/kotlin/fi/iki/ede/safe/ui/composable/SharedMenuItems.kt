package fi.iki.ede.safe.ui.composable

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Single, deduplicated menu action component shared across Android, iOS, and Desktop.
 * Guarantees top and bottom action bars across all platforms maintain 100% synchronized menu items.
 */
@Composable
fun SharedMenuItems(
    isLoggedIn: Boolean = true,
    onPeerSyncRequested: () -> Unit = {},
    onSettingsRequested: () -> Unit = {},
    onHelpRequested: () -> Unit = {},
    onChangeMasterPasswordRequested: () -> Unit = {},
    onShowTrashRequested: () -> Unit = {},
    onImportExportRequested: () -> Unit = {},
    onSeedAndroidDbRequested: () -> Unit = {},
    onSeedIosDbRequested: () -> Unit = {},
    onTestConflictMatrixRequested: () -> Unit = {},
    onResolveLocalConflictsRequested: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    DropdownMenuItem(
        text = { Text(text = "⚡ Peer Sync (Device Sync)") },
        onClick = {
            onDismiss()
            onPeerSyncRequested()
        }
    )
    DropdownMenuItem(
        enabled = isLoggedIn,
        text = { Text(text = getString("action_bar_settings")) },
        onClick = {
            onDismiss()
            onSettingsRequested()
        }
    )
    DropdownMenuItem(
        text = { Text(text = getString("action_bar_help")) },
        onClick = {
            onDismiss()
            onHelpRequested()
        }
    )
    DropdownMenuItem(
        enabled = isLoggedIn,
        text = { Text(text = getString("action_bar_change_master_password")) },
        onClick = {
            onDismiss()
            onChangeMasterPasswordRequested()
        }
    )
    DropdownMenuItem(
        enabled = isLoggedIn,
        text = { Text(text = getString("action_bar_show_trash")) },
        onClick = {
            onDismiss()
            onShowTrashRequested()
        }
    )
    DropdownMenuItem(
        enabled = isLoggedIn,
        text = { Text(text = getString("action_bar_import_export")) },
        onClick = {
            onDismiss()
            onImportExportRequested()
        }
    )
    DropdownMenuItem(
        text = { Text(text = "🌱 Seed Local DB (Android Matrix Q, X, Y, M)") },
        onClick = {
            onDismiss()
            onSeedAndroidDbRequested()
        }
    )
    DropdownMenuItem(
        text = { Text(text = "🌱 Seed Local DB (iOS Matrix W, X, Y, M)") },
        onClick = {
            onDismiss()
            onSeedIosDbRequested()
        }
    )
    DropdownMenuItem(
        text = { Text(text = "🧪 Test Conflict Matrix (Simulated Q, W, X, Y, M)") },
        onClick = {
            onDismiss()
            onTestConflictMatrixRequested()
        }
    )
    DropdownMenuItem(
        text = { Text(text = "🧪 Resolve Local DB vs Matrix Conflicts") },
        onClick = {
            onDismiss()
            onResolveLocalConflictsRequested()
        }
    )
}
