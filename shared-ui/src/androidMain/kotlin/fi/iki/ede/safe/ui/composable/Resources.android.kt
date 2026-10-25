package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val fallbackStrings = mapOf(
    "restore_screen_restore_button" to "Restore",
    "restore_screen_backups_password" to "Backup Password",
    "restore_screen_passwords_count" to "Passwords restored: %d",
    "restore_screen_categories_count" to "Categories restored: %d",
    "restore_screen_begin_restore" to "Starting restore...",
    "restore_screen_process_backup" to "Processing backup...",
    "restore_screen_finished_backup" to "Backup processed",
    "restore_screen_restore_failed" to "Restore failed",
    "restore_screen_restoring_old_backup" to "Restoring backup...",
    "restore_screen_reread_database" to "Reloading database...",
    "restore_screen_done" to "Restore completed"
)

@Composable
actual fun getString(id: String): String = LocalContext.current.let { context ->
    val resId = context.resources.getIdentifier(id, "string", context.packageName)
    if (resId != 0) context.getString(resId) else fallbackStrings[id] ?: id
}

@Composable
actual fun getString(id: String, formatArg: String): String = LocalContext.current.let { context ->
    context.resources.getIdentifier(id, "string", context.packageName).let { resId ->
        if (resId != 0) context.getString(resId, formatArg) else id.replace("%s", formatArg)
    }
}

@Composable
actual fun getPluralString(id: String, quantity: Int, formatArg: Int): String = LocalContext.current.let { context ->
    context.resources.getIdentifier(id, "plurals", context.packageName).let { resId ->
        if (resId != 0) context.resources.getQuantityString(resId, quantity, formatArg) else "$formatArg $id"
    }
}
