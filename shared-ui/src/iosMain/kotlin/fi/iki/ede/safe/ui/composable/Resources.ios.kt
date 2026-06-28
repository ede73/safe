package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable

object IosResources {
    fun getString(id: String): String = when (id) {
        "generic_ok" -> "OK!"
        "category_list_edit_category" -> "Edit category name"
        "category_list_delete" -> "Delete %s?"
        "category_list_delete_confirm" -> "Are you sure you want to delete %s?"
        "category_list_edit" -> "Edit category %s"
        "login_app_title" -> "Safe"
        "login_app_subtitle" -> "Password Manager"
        "login_choose_password_label" -> "Choose Master Password"
        "login_confirm_password_label" -> "Confirm Master Password"
        "biometrics_register" -> "Register Biometrics"
        "login_create_vault_button" -> "Create Vault"
        "login_with_biometrics" -> "Login with Biometrics"
        "login_or_divider" -> "OR"
        "login_master_password_label" -> "Master Password"
        "login_unlock_button" -> "Unlock"
        "login_import_backup_button" -> "📥 Import Backup"
        "login_export_backup_button" -> "📤 Export Backup"
        "restore_screen_backup_help" -> "Restore backup file: %s"
        "restore_screen_backups_password" -> "Backup Password"
        "restore_screen_restore_button" -> "Restore"
        "restore_screen_passwords_count" -> "Passwords: %d"
        "restore_screen_categories_count" -> "Categories: %d"
        "restore_screen_begin_restore" -> "Begin restoration"
        "restore_screen_process_backup" -> "Process backup"
        "restore_screen_finished_backup" -> "Finished with backup"
        "restore_screen_restore_failed" -> "Something failed, rollback"
        "generic_add" -> "Add"
        "action_bar_lock" -> "Lock"
        "action_bar_search" -> "Search"
        "action_bar_settings" -> "Settings"
        "action_bar_help" -> "Help"
        "action_bar_change_master_password" -> "Change Master Password"
        "action_bar_show_trash" -> "Show Trash"
        "action_bar_import_export" -> "Export & Import"
        "action_bar_backup" -> "Export Backup"
        "action_bar_restore" -> "Import Backup"
        else -> id
    }

    fun getPluralString(id: String, quantity: Int): String {
        val quantityStr = when (quantity) {
            0 -> "zero"
            1 -> "one"
            2 -> "two"
            else -> "other"
        }
        return when (id) {
            "password_list_password_age_days" -> when (quantityStr) {
                "zero" -> "(brand new)"
                "one" -> "%d day"
                else -> "%d days"
            }
            "password_list_password_age_months" -> when (quantityStr) {
                "one" -> "%d month"
                else -> "%d months"
            }
            "password_list_password_age_years" -> when (quantityStr) {
                "one" -> "%d year"
                else -> "%d years"
            }
            "category_passwords_count" -> when (quantityStr) {
                "one" -> "%d password"
                else -> "%d passwords"
            }
            else -> "%d $id"
        }
    }
}

@Composable
actual fun getString(id: String): String = IosResources.getString(id)

@Composable
actual fun getString(id: String, formatArg: String): String = 
    IosResources.getString(id).replace("%s", formatArg).replace("%1\$s", formatArg)

@Composable
actual fun getPluralString(id: String, quantity: Int, formatArg: Int): String = 
    IosResources.getPluralString(id, quantity).replace("%d", formatArg.toString()).replace("%1\$d", formatArg.toString())
