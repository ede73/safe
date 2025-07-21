package fi.iki.ede.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.preference.PreferenceManager
import androidx.core.content.edit
import fi.iki.ede.dateutils.DateUtils
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Preferences {
    lateinit var sharedPreferences: SharedPreferences

    fun initialize(context: Context) {
        if (BuildConfig.DEBUG) {
            require(System.getProperty("test") != "test") {
                "You MUST mock shared preferences in androidTests"
            }
        }
        // set preferences default values (if not set)
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
        sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    // only used as accessors in SharedPreferencesChange
    const val PASSWORDSAFE_EXPORT_FILE = "passwordsafe.xml"

    // See ExportConfig for woes
    const val SUPPORT_EXPORT_LOCATION_MEMORY = false

    @Deprecated(
        "Mustn't be used, no point for now, see ExportConfig/SUPPORT_EXPORT_LOCATION_MEMORY",
        level = DeprecationLevel.WARNING
    )
    const val PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED = "autobackup_quota_exceeded"
    const val PREFERENCE_AUTOBACKUP_RESTORE_FINISHED = "autobackup_restore_finished"
    const val PREFERENCE_AUTOBACKUP_RESTORE_STARTED = "autobackup_restore_started"
    const val PREFERENCE_AUTOBACKUP_STARTED = "autobackup_started"
    const val PREFERENCE_BACKUP_DOCUMENT = "backup_document"
    private val PREFERENCE_BACKUP_PATH_DEFAULT_VALUE =
        (Environment.getExternalStorageDirectory()?.absolutePath
            ?: "") + "/$PASSWORDSAFE_EXPORT_FILE"
    const val PREFERENCE_BIOMETRICS_ENABLED = "biometrics"
    const val PREFERENCE_BIO_CIPHER = "bio_cipher"
    const val PREFERENCE_EXPERIMENTAL_FEATURES = "experiments"
    const val PREFERENCE_EXTENSIONS_KEY = "extensions_edit"
    const val PREFERENCE_LAST_BACKUP_TIME = "time_of_last_backup"
    const val PREFERENCE_LOCK_TIMEOUT_MINUTES = "lock_timeout"
    const val PREFERENCE_MAKE_CRASH = "make_a_crash"
    const val PREFERENCE_SOFT_DELETE_DAYS = "soft_delete_days"
    private const val NOTIFICATION_PERMISSION_DENIED = "notification_permission_denied"
    private const val NOTIFICATION_PERMISSION_REQUIRED = "notification_permission_required"
    private const val PREFERENCE_CLIPBOARD_CLEAR_DELAY = "clipboard_clear_delay"
    private const val PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE = "45"
    private const val PREFERENCE_DEFAULT_USER_NAME = "default_user_name"
    private const val PREFERENCE_GPM_IMPORT_USAGE_SHOWN = "gpm_import_usage_shown"
    private const val PREFERENCE_LAST_MODIFIED = "last_modified"
    private const val PREFERENCE_LOCK_ON_SCREEN_LOCK = "lock_on_screen_lock"
    private const val PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES = "5"

    fun getBackupDocument() = if (SUPPORT_EXPORT_LOCATION_MEMORY) {
        sharedPreferences
            .getString(
                PREFERENCE_BACKUP_DOCUMENT,
                PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
            ) ?: PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
    } else {
        PREFERENCE_BACKUP_PATH_DEFAULT_VALUE
    }

    fun setBackupDocument(uriString: String?) = sharedPreferences.edit(commit = true) {
        putString(PREFERENCE_BACKUP_DOCUMENT, uriString)
    }

    fun getDefaultUserName() = sharedPreferences.getString(PREFERENCE_DEFAULT_USER_NAME, "") ?: ""

    fun getLockOnScreenLock(default: Boolean) =
        sharedPreferences.getBoolean(PREFERENCE_LOCK_ON_SCREEN_LOCK, default)

    fun getClipboardClearDelaySecs() = sharedPreferences.getString(
        PREFERENCE_CLIPBOARD_CLEAR_DELAY,
        PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE
    )?.toIntOrNull() ?: PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE.toInt()

    fun getLockTimeoutDuration(): Duration = (sharedPreferences.getString(
        PREFERENCE_LOCK_TIMEOUT_MINUTES,
        PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES
    )?.toIntOrNull() ?: PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES.toInt()).toDuration(
        DurationUnit.MINUTES
    )

    // We're checking notification permission in service (countdown timer)
    // if missing, we'll flag here to request the permission when user is
    // at screen (ie. from activity)
    fun setNotificationPermissionRequired(value: Boolean) =
        sharedPreferences.edit(commit = true) {
            putBoolean(
                NOTIFICATION_PERMISSION_REQUIRED,
                value
            )
        }

    fun getNotificationPermissionRequired() =
        sharedPreferences.getBoolean(NOTIFICATION_PERMISSION_REQUIRED, false)

    fun setNotificationPermissionDenied(value: Boolean) =
        sharedPreferences.edit(commit = true) { putBoolean(NOTIFICATION_PERMISSION_DENIED, value) }

    fun getNotificationPermissionDenied() =
        sharedPreferences.getBoolean(NOTIFICATION_PERMISSION_DENIED, false)


    private fun storeTimestamp(key: String) = sharedPreferences.edit(commit = true) {
        putLong(
            key, DateUtils.toUnixSeconds(ZonedDateTime.now())
        )
    }

    private fun getStoredTimestamp(key: String) = sharedPreferences.getLong(key, 0)
        .takeIf { it != 0L }
        ?.let { DateUtils.unixEpochSecondsToLocalZonedDateTime(it) }

    fun setLastBackupTime() = storeTimestamp(PREFERENCE_LAST_BACKUP_TIME)
    fun getLastBackupTime() = getStoredTimestamp(PREFERENCE_LAST_BACKUP_TIME)

    fun getEnabledExperimentNames(): Set<String> =
        sharedPreferences.getStringSet(PREFERENCE_EXPERIMENTAL_FEATURES, emptySet())?.toSet()
            ?: emptySet()

    // This is safety measure on app crash, it must go to the disk immediately
    @SuppressLint("ApplySharedPref")
    fun clearAllPlugins() {
        sharedPreferences.edit(commit = true) {
            putStringSet(
                PREFERENCE_EXPERIMENTAL_FEATURES,
                emptySet()
            )
        }
    }

    fun autoBackupQuotaExceeded() = storeTimestamp(PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED)
    fun getAutoBackupQuotaExceeded() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED)

    fun autoBackupRestoreStarts() = storeTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_STARTED)
    fun getAutoBackupRestoreStarts() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_STARTED)

    fun autoBackupStarts() = storeTimestamp(PREFERENCE_AUTOBACKUP_STARTED)
    fun getAutoBackupStarts() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_STARTED)

    fun autoBackupRestoreFinished() = storeTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_FINISHED)
    fun getAutoBackupRestoreFinished() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_FINISHED)

    fun getSoftDeleteDays() = sharedPreferences.getInt(PREFERENCE_SOFT_DELETE_DAYS, 30)

    fun gpmImportUsageShown() = storeTimestamp(PREFERENCE_GPM_IMPORT_USAGE_SHOWN)
    fun getGpmImportUsageShown() = sharedPreferences.getLong(PREFERENCE_GPM_IMPORT_USAGE_SHOWN, 0L)

    fun setLastModified() = storeTimestamp(PREFERENCE_LAST_MODIFIED)
    fun getLastModified() = getStoredTimestamp(PREFERENCE_LAST_MODIFIED)

    fun getAllExtensions(): Set<String> =
        sharedPreferences.getStringSet(PREFERENCE_EXTENSIONS_KEY, emptySet())?.toSet() ?: emptySet()

    fun storeAllExtensions(extensions: Set<String>) =
        sharedPreferences.edit(commit = true) {
            putStringSet(
                PREFERENCE_EXTENSIONS_KEY,
                extensions
            )
        }
}