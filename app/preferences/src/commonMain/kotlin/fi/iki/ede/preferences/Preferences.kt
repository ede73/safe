package fi.iki.ede.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fi.iki.ede.dateutils.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

expect val preferenceBackupPathDefaultValue: String

expect fun createDataStore(context: Any?): DataStore<DataStorePreferences>

expect fun notifyPlatformListeners(key: String)

expect fun initializePlatform(context: Any?)

@ExperimentalTime
object Preferences {
    lateinit var dataStore: DataStore<DataStorePreferences>
    lateinit var sharedPreferences: SharedPreferences

    private val cache = mutableMapOf<String, Any>()
    private val cacheLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize(context: Any?) {
        dataStore = createDataStore(context)
        // Load initial preferences into cache synchronously before event loop starts
        runBlocking {
            try {
                val prefs = dataStore.data.first()
                synchronized(cacheLock) {
                    prefs.asMap().forEach { (key, value) ->
                        cache[key.name] = value
                    }
                }
            } catch (e: Exception) {
                // Ignore or log in-memory
            }
        }
        initializePlatform(context)
    }

    private fun <T> getCached(key: String, defaultValue: T): T {
        return synchronized(cacheLock) {
            @Suppress("UNCHECKED_CAST")
            (cache[key] as? T)
        } ?: defaultValue
    }

    private fun <T> getCachedOrNull(key: String): T? {
        return synchronized(cacheLock) {
            @Suppress("UNCHECKED_CAST")
            (cache[key] as? T)
        }
    }

    private fun <T> setCached(keyStr: String, value: T?, keyObj: DataStorePreferences.Key<T>) {
        synchronized(cacheLock) {
            if (value == null) {
                cache.remove(keyStr)
            } else {
                cache[keyStr] = value
            }
        }
        scope.launch {
            try {
                dataStore.edit { preferences ->
                    if (value == null) {
                        preferences.remove(keyObj)
                    } else {
                        preferences[keyObj] = value
                    }
                }
            } catch (e: Exception) {
                // Ignore or log background save error
            }
        }
        notifyPlatformListeners(keyStr)
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
    const val PREFERENCE_BIOMETRICS_ENABLED = "biometrics"
    const val PREFERENCE_BIO_CIPHER = "bio_cipher"
    const val PREFERENCE_EXPERIMENTAL_FEATURES = "experiments"
    const val PREFERENCE_EXTENSIONS_KEY = "extensions_edit"
    const val PREFERENCE_LAST_BACKUP_TIME = "time_of_last_backup"
    const val PREFERENCE_LOCK_TIMEOUT_MINUTES = "lock_timeout"
    const val PREFERENCE_MAKE_CRASH = "make_a_crash"
    const val PREFERENCE_SOFT_DELETE_DAYS = "soft_delete_days"

    const val NOTIFICATION_PERMISSION_DENIED = "notification_permission_denied"
    const val NOTIFICATION_PERMISSION_REQUIRED = "notification_permission_required"
    const val PREFERENCE_CLIPBOARD_CLEAR_DELAY = "clipboard_clear_delay"
    const val PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE = "45"
    const val PREFERENCE_DEFAULT_USER_NAME = "default_user_name"
    const val PREFERENCE_GPM_IMPORT_USAGE_SHOWN = "gpm_import_usage_shown"
    const val PREFERENCE_LAST_MODIFIED = "last_modified"
    const val PREFERENCE_LOCK_ON_SCREEN_LOCK = "lock_on_screen_lock"
    const val PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES = "5"

    fun getBackupDocument(): String {
        return if (SUPPORT_EXPORT_LOCATION_MEMORY) {
            getCached(PREFERENCE_BACKUP_DOCUMENT, preferenceBackupPathDefaultValue)
        } else {
            preferenceBackupPathDefaultValue
        }
    }

    fun setBackupDocument(uriString: String?) {
        val key = stringPreferencesKey(PREFERENCE_BACKUP_DOCUMENT)
        setCached(PREFERENCE_BACKUP_DOCUMENT, uriString, key)
    }

    fun getDefaultUserName(): String {
        return getCached(PREFERENCE_DEFAULT_USER_NAME, "")
    }

    fun getLockOnScreenLock(default: Boolean): Boolean {
        return getCached(PREFERENCE_LOCK_ON_SCREEN_LOCK, default)
    }

    fun getClipboardClearDelaySecs(): Int {
        val stringVal = getCached(PREFERENCE_CLIPBOARD_CLEAR_DELAY, PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE)
        return stringVal.toIntOrNull() ?: PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE.toInt()
    }

    fun getLockTimeoutDuration(): Duration {
        val stringVal = getCached(PREFERENCE_LOCK_TIMEOUT_MINUTES, PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES)
        val minutes = stringVal.toIntOrNull() ?: PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES.toInt()
        return minutes.toDuration(DurationUnit.MINUTES)
    }

    fun setNotificationPermissionRequired(value: Boolean) {
        val key = booleanPreferencesKey(NOTIFICATION_PERMISSION_REQUIRED)
        setCached(NOTIFICATION_PERMISSION_REQUIRED, value, key)
    }

    fun getNotificationPermissionRequired(): Boolean {
        return getCached(NOTIFICATION_PERMISSION_REQUIRED, false)
    }

    fun setNotificationPermissionDenied(value: Boolean) {
        val key = booleanPreferencesKey(NOTIFICATION_PERMISSION_DENIED)
        setCached(NOTIFICATION_PERMISSION_DENIED, value, key)
    }

    fun getNotificationPermissionDenied(): Boolean {
        return getCached(NOTIFICATION_PERMISSION_DENIED, false)
    }

    private fun storeTimestamp(key: String) {
        val keyObj = longPreferencesKey(key)
        setCached(key, DateUtils.toUnixSeconds(Clock.System.now()), keyObj)
    }

    private fun getStoredTimestamp(key: String): kotlinx.datetime.Instant? {
        val value = getCached(key, 0L)
        return if (value != 0L) {
            DateUtils.unixEpochSecondsToInstant(value)
        } else {
            null
        }
    }

    fun setLastBackupTime() = storeTimestamp(PREFERENCE_LAST_BACKUP_TIME)
    fun getLastBackupTime() = getStoredTimestamp(PREFERENCE_LAST_BACKUP_TIME)

    fun getEnabledExperimentNames(): Set<String> {
        return getCached(PREFERENCE_EXPERIMENTAL_FEATURES, emptySet())
    }

    fun clearAllPlugins() {
        val key = stringSetPreferencesKey(PREFERENCE_EXPERIMENTAL_FEATURES)
        setCached(PREFERENCE_EXPERIMENTAL_FEATURES, emptySet<String>(), key)
    }

    fun autoBackupQuotaExceeded() = storeTimestamp(PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED)
    fun getAutoBackupQuotaExceeded() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED)

    fun autoBackupRestoreStarts() = storeTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_STARTED)
    fun getAutoBackupRestoreStarts() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_STARTED)

    fun autoBackupStarts() = storeTimestamp(PREFERENCE_AUTOBACKUP_STARTED)
    fun getAutoBackupStarts() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_STARTED)

    fun autoBackupRestoreFinished() = storeTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_FINISHED)
    fun getAutoBackupRestoreFinished() = getStoredTimestamp(PREFERENCE_AUTOBACKUP_RESTORE_FINISHED)

    fun getSoftDeleteDays(): Int {
        return getCached(PREFERENCE_SOFT_DELETE_DAYS, 30)
    }

    fun gpmImportUsageShown() = storeTimestamp(PREFERENCE_GPM_IMPORT_USAGE_SHOWN)
    fun getGpmImportUsageShown(): Long {
        return getCached(PREFERENCE_GPM_IMPORT_USAGE_SHOWN, 0L)
    }

    fun setLastModified() = storeTimestamp(PREFERENCE_LAST_MODIFIED)
    fun getLastModified() = getStoredTimestamp(PREFERENCE_LAST_MODIFIED)

    fun getAllExtensions(): Set<String> {
        return getCached(PREFERENCE_EXTENSIONS_KEY, emptySet())
    }

    fun storeAllExtensions(extensions: Set<String>) {
        val key = stringSetPreferencesKey(PREFERENCE_EXTENSIONS_KEY)
        setCached(PREFERENCE_EXTENSIONS_KEY, extensions, key)
    }

    private val desktopBiometricsEnabledKey = booleanPreferencesKey("desktop_biometrics_enabled")
    private val desktopBioCipherKey = stringPreferencesKey("desktop_bio_cipher")

    fun isDesktopBiometricsRegistered(): Boolean {
        return getCached("desktop_biometrics_enabled", false)
    }

    fun getDesktopBioCipher(): String? {
        return getCachedOrNull("desktop_bio_cipher")
    }

    fun registerDesktopBiometrics(base64Cipher: String) {
        synchronized(cacheLock) {
            cache["desktop_biometrics_enabled"] = true
            cache["desktop_bio_cipher"] = base64Cipher
        }
        scope.launch {
            try {
                dataStore.edit { preferences ->
                    preferences[desktopBiometricsEnabledKey] = true
                    preferences[desktopBioCipherKey] = base64Cipher
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun clearDesktopBiometrics() {
        synchronized(cacheLock) {
            cache.remove("desktop_biometrics_enabled")
            cache.remove("desktop_bio_cipher")
        }
        scope.launch {
            try {
                dataStore.edit { preferences ->
                    preferences.remove(desktopBiometricsEnabledKey)
                    preferences.remove(desktopBioCipherKey)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
