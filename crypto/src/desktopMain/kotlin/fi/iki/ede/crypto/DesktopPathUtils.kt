package fi.iki.ede.crypto

import java.io.File

object DesktopPathUtils {
    val userHome: File
        get() = File(System.getProperty("user.home") ?: ".")

    val tpmKeysFile: File
        get() = File(userHome, ".safe_desktop_tpm_keys")

    val oldSettingsFile: File
        get() = File(userHome, ".safe_desktop_settings")

    val preferencesFile: File
        get() = File(userHome, ".safe_desktop_settings.preferences_pb")
}
