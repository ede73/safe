package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable

object IosResources {
    fun getString(id: String): String = when (id) {
        "generic_ok" -> "OK!"
        "category_list_edit_category" -> "Edit category name"
        "category_list_delete" -> "Delete %s?"
        "category_list_delete_confirm" -> "Are you sure you want to delete %s?"
        "category_list_edit" -> "Edit category %s"
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
