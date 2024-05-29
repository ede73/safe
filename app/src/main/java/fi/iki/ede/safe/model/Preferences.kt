package fi.iki.ede.safe.model

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.preference.PreferenceManager

object Preferences {
    lateinit var sharedPreferences: SharedPreferences

    fun initialize(context: Context) {
        sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    // only used as accessors in SharedPrerefencesChange
    const val PASSWORDSAFE_EXPORT_FILE = "passwordsafe.xml"

    // See ExportConfig for woes
    const val SUPPORT_EXPORT_LOCATION_MEMORY = false

    @Deprecated(
        "Mustn't be used, no point for now, see ExportConfig/SUPPORT_EXPORT_LOCATION_MEMORY",
        level = DeprecationLevel.WARNING
    )
    const val PREFERENCE_BACKUP_DOCUMENT = "backup_document"
    private val PREFERENCE_BACKUP_PATH_DEFAULT_VALUE =
        Environment.getExternalStorageDirectory().absolutePath + "/" + PASSWORDSAFE_EXPORT_FILE
    const val PREFERENCE_BIOMETRICS_ENABLED = "biometrics"
    const val PREFERENCE_LOCK_TIMEOUT = "lock_timeout"
    private const val PREFERENCE_DEFAULT_USER_NAME = "default_user_name"
    private const val NOTIFICATION_PERMISSION_REQUIRED = "notification_permission_required"
    const val PREFERENCE_BIO_CIPHER = "bio_cipher"
    private const val PREFERENCE_LOCK_ON_SCREEN_LOCK = "lock_on_screen_lock"
    private const val PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE = "5"

    fun getBackupDocument() = if (SUPPORT_EXPORT_LOCATION_MEMORY) {
        sharedPreferences
            .getString(
                PREFERENCE_BACKUP_DOCUMENT,
                PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
            ) ?: PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
    } else {
        PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
    }


    fun setBackupDocument(uriString: String?) =
        sharedPreferences.edit()
            .putString(PREFERENCE_BACKUP_DOCUMENT, uriString)
            .apply()

    fun getDefaultUserName() =
        sharedPreferences.getString(PREFERENCE_DEFAULT_USER_NAME, "") ?: ""

    fun getLockOnScreenLock(default: Boolean) =
        sharedPreferences.getBoolean(PREFERENCE_LOCK_ON_SCREEN_LOCK, default)

    fun getLockTimeoutMinutes() = sharedPreferences.getString(
        PREFERENCE_LOCK_TIMEOUT,
        PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE
    )?.toIntOrNull() ?: PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE.toInt()

    // We're checking notification permission in service (countdown timer)
    // if missing, we'll flag here to request the permission when user is
    // at screen (ie. from activity)
    fun setNotificationPermissionRequired(value: Boolean) =
        sharedPreferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUIRED, value).apply()
}