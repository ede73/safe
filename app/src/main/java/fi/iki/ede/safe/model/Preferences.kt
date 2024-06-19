package fi.iki.ede.safe.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.preference.PreferenceManager
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.splits.PluginName
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
    const val PREFERENCE_BACKUP_DOCUMENT = "backup_document"
    const val PREFERENCE_EXPERIMENTAL_FEATURES = "experiments"
    const val PREFERENCE_MAKE_CRASH = "make_a_crash"
    private val PREFERENCE_BACKUP_PATH_DEFAULT_VALUE =
        Environment.getExternalStorageDirectory().absolutePath + "/" + PASSWORDSAFE_EXPORT_FILE
    const val PREFERENCE_BIOMETRICS_ENABLED = "biometrics"
    const val PREFERENCE_LOCK_TIMEOUT_MINUTES = "lock_timeout"
    private const val PREFERENCE_CLIPBOARD_CLEAR_DELAY = "clipboard_clear_delay"
    private const val PREFERENCE_DEFAULT_USER_NAME = "default_user_name"
    private const val NOTIFICATION_PERMISSION_REQUIRED = "notification_permission_required"
    const val PREFERENCE_BIO_CIPHER = "bio_cipher"
    private const val PREFERENCE_LOCK_ON_SCREEN_LOCK = "lock_on_screen_lock"
    private const val PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES = "5"
    private const val PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE = "45"
    const val PREFERENCE_LAST_BACKUP_TIME = "time_of_last_backup"
    const val PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED = "autobackup_quota_exceeded"
    const val PREFERENCE_AUTOBACKUP_STARTED = "autobackup_started"
    const val PREFERENCE_AUTOBACKUP_RESTORE_STARTED = "autobackup_restore_started"
    const val PREFERENCE_AUTOBACKUP_RESTORE_FINISHED = "autobackup_restore_finished"

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
        sharedPreferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUIRED, value).apply()
    
    fun getLastBackupTime() = sharedPreferences.getLong(PREFERENCE_LAST_BACKUP_TIME, 0)
        .takeIf { it != 0L }
        ?.let { DateUtils.unixEpochSecondsToLocalZonedDateTime(it) }

    fun setLastBackupTime() = sharedPreferences.edit().putLong(
        PREFERENCE_LAST_BACKUP_TIME, DateUtils.toUnixSeconds(
            ZonedDateTime.now()
        )
    ).apply()

    fun getEnabledExperiments(): Set<PluginName> =
        sharedPreferences.getStringSet(PREFERENCE_EXPERIMENTAL_FEATURES, emptySet())
            ?.mapNotNull { PluginName.entries.firstOrNull { p -> p.pluginName == it } }
            ?.toSet() ?: emptySet()

    // This is safety measure on app crash, it must go to the disk immediately
    @SuppressLint("ApplySharedPref")
    fun clearAllPlugins() {
        sharedPreferences.edit().putStringSet(PREFERENCE_EXPERIMENTAL_FEATURES, emptySet()).commit()
    }

    fun autoBackupQuotaExceeded() =
        sharedPreferences.edit().putLong(
            PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED, DateUtils.toUnixSeconds(ZonedDateTime.now())
        ).apply()

    fun getAutoBackupQuotaExceeded() =
        sharedPreferences.getLong(PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED, 0)
            .takeIf { it != 0L }
            ?.let { DateUtils.unixEpochSecondsToLocalZonedDateTime(it) }

    fun autoBackupRestoreStarts() =
        sharedPreferences.edit().putLong(
            PREFERENCE_AUTOBACKUP_RESTORE_STARTED, DateUtils.toUnixSeconds(ZonedDateTime.now())
        ).apply()

    fun getAutoBackupRestoreStarts() =
        sharedPreferences.getLong(PREFERENCE_AUTOBACKUP_RESTORE_STARTED, 0)
            .takeIf { it != 0L }
            ?.let { DateUtils.unixEpochSecondsToLocalZonedDateTime(it) }

    fun autoBackupStarts() =
        sharedPreferences.edit().putLong(
            PREFERENCE_AUTOBACKUP_STARTED, DateUtils.toUnixSeconds(ZonedDateTime.now())
        ).apply()

    fun getAutoBackupStarts() =
        sharedPreferences.getLong(PREFERENCE_AUTOBACKUP_STARTED, 0)
            .takeIf { it != 0L }
            ?.let { DateUtils.unixEpochSecondsToLocalZonedDateTime(it) }

    fun autoBackupRestoreFinished() =
        sharedPreferences.edit().putLong(
            PREFERENCE_AUTOBACKUP_RESTORE_FINISHED, DateUtils.toUnixSeconds(ZonedDateTime.now())
        ).apply()

    fun getAutoBackupRestoreFinished() =
        sharedPreferences.getLong(PREFERENCE_AUTOBACKUP_RESTORE_FINISHED, 0)
            .takeIf { it != 0L }
            ?.let { DateUtils.unixEpochSecondsToLocalZonedDateTime(it) }
}