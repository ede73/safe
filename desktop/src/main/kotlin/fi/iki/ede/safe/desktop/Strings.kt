package fi.iki.ede.safe.desktop

import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import java.util.Locale

object DesktopStrings {
    private val strings = mutableMapOf<String, String>()

    init {
        // Fallback defaults in English
        strings["login_password_tip"] = "Enter your password"
        strings["login_verify_password_tip"] = "Enter the same password again"
        strings["login_with_biometrics"] = "Login with biometrics"
        strings["biometrics_register"] = "Register biometrics after successful login"
        strings["login_invalid_password"] = "Invalid password"
        strings["action_bar_lock"] = "Lock app"
        strings["generic_add"] = "Add"
        strings["action_bar_backup"] = "Backup"
        strings["action_bar_restore"] = "Restore"
        strings["generic_cancel"] = "Cancel"
        strings["password_entry_save"] = "Save?"
        strings["create_vault"] = "Create Vault"
        strings["unlock"] = "Unlock"
        strings["vault"] = "Vault"
        strings["categories"] = "Categories"
        strings["add_category"] = "Add Category"
        strings["category_name"] = "Category Name"
        strings["add_password_entry"] = "Add Password Entry"
        strings["password_entry_details"] = "Password Entry Details"
        strings["description"] = "Description"
        strings["website"] = "Website"
        strings["username"] = "Username"
        strings["password"] = "Password"
        strings["notes"] = "Notes"
        strings["delete"] = "Delete"
        strings["save"] = "Save"
        strings["import_backup_xml"] = "Import Backup XML"
        strings["backup_xml_file"] = "Backup XML File"
        strings["backup_password"] = "Backup Password"
        strings["browse"] = "Browse"
        strings["export_backup"] = "Export Backup"
        strings["backup_export_success"] = "Backup successfully exported to:\n%s"
        strings["backup_export_failure"] = "Export failed:\n%s"
        strings["biometrics_verifying"] = "Verifying biometrics..."
        strings["biometrics_unlock_success"] = "Unlock successful! Logged in via biometrics."
        strings["biometrics_scanning"] = "Scanning fingerprint..."
        strings["biometrics_unlock"] = "Biometric Unlock"
        strings["extension_type"] = "Type"
        strings["extension_value"] = "Value"

        // Load translations from strings.xml
        runCatching {
            val userLanguage = Locale.getDefault().language
            val paths = listOf(
                "app/src/main/res/values/strings.xml",
                "../app/src/main/res/values/strings.xml"
            )
            val fiPaths = listOf(
                "app/src/main/res/values-fi/strings.xml",
                "../app/src/main/res/values-fi/strings.xml"
            )
            val selectedPaths = if (userLanguage == "fi") fiPaths + paths else paths

            for (pathStr in selectedPaths) {
                val path = Path(pathStr)
                if (path.exists()) {
                    val content = path.readText()
                    val regex = """<string\s+name="([^"]+)"(?:[^>]*)>(.*?)</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    regex.findAll(content).forEach { match ->
                        val name = match.groupValues[1]
                        val value = match.groupValues[2]
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("\\'", "'")
                            .replace("\\\"", "\"")
                            .replace("<![CDATA[", "")
                            .replace("]]>", "")
                        strings[name] = value
                    }
                    break
                }
            }
        }
    }

    fun get(key: String, vararg args: Any): String {
        val pattern = strings[key] ?: key
        return if (args.isEmpty()) pattern else pattern.format(*args)
    }
}
