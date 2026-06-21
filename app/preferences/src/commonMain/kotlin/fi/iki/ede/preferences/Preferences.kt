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

expect fun createDataStore(): DataStore<DataStorePreferences>

expect fun notifyPlatformListeners(key: String)

expect fun initializePlatform()

@ExperimentalTime
object Preferences {
    lateinit var dataStore: DataStore<DataStorePreferences>
    lateinit var sharedPreferences: SharedPreferences

    fun isSharedPreferencesInitialized() = ::sharedPreferences.isInitialized
    fun isDataStoreInitialized() = ::dataStore.isInitialized

    private val cache = mutableMapOf<String, PreferenceValue>()
    private val cacheLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize() {
        dataStore = createDataStore()
        // Load initial preferences into cache synchronously before event loop starts
        runBlocking {
            try {
                val prefs = dataStore.data.first()
                synchronized(cacheLock) {
                    prefs.asMap().forEach { (key, value) ->
                        val wrapped = when (value) {
                            is String -> PreferenceValue.StringVal(value)
                            is Int -> PreferenceValue.IntVal(value)
                            is Long -> PreferenceValue.LongVal(value)
                            is Float -> PreferenceValue.FloatVal(value)
                            is Boolean -> PreferenceValue.BooleanVal(value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                PreferenceValue.StringSetVal(value as Set<String>)
                            }
                            else -> null
                        }
                        if (wrapped != null) {
                            cache[key.name] = wrapped
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore or log in-memory
            }
        }
        initializePlatform()
    }

    private fun <T> getCached(keyObj: DataStorePreferences.Key<T>, defaultValue: T): T {
        return synchronized(cacheLock) {
            val cached = cache[keyObj.name]
            if (cached != null) {
                val value = when (cached) {
                    is PreferenceValue.StringVal -> cached.value
                    is PreferenceValue.IntVal -> cached.value
                    is PreferenceValue.LongVal -> cached.value
                    is PreferenceValue.FloatVal -> cached.value
                    is PreferenceValue.BooleanVal -> cached.value
                    is PreferenceValue.StringSetVal -> cached.value
                }
                @Suppress("UNCHECKED_CAST")
                value as? T
            } else {
                null
            }
        } ?: defaultValue
    }

    private fun <T> getCachedOrNull(keyObj: DataStorePreferences.Key<T>): T? {
        return synchronized(cacheLock) {
            val cached = cache[keyObj.name]
            if (cached != null) {
                val value = when (cached) {
                    is PreferenceValue.StringVal -> cached.value
                    is PreferenceValue.IntVal -> cached.value
                    is PreferenceValue.LongVal -> cached.value
                    is PreferenceValue.FloatVal -> cached.value
                    is PreferenceValue.BooleanVal -> cached.value
                    is PreferenceValue.StringSetVal -> cached.value
                }
                @Suppress("UNCHECKED_CAST")
                value as? T
            } else {
                null
            }
        }
    }

    private fun <T> setCached(keyObj: DataStorePreferences.Key<T>, value: T?) {
        val keyStr = keyObj.name
        synchronized(cacheLock) {
            if (value == null) {
                cache.remove(keyStr)
            } else {
                val wrapped = when (value) {
                    is String -> PreferenceValue.StringVal(value)
                    is Int -> PreferenceValue.IntVal(value)
                    is Long -> PreferenceValue.LongVal(value)
                    is Float -> PreferenceValue.FloatVal(value)
                    is Boolean -> PreferenceValue.BooleanVal(value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        PreferenceValue.StringSetVal(value as Set<String>)
                    }
                    else -> null
                }
                if (wrapped != null) {
                    cache[keyStr] = wrapped
                }
            }
        }
        if (::dataStore.isInitialized) {
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
    const val PREFERENCE_DESKTOP_BIOMETRICS_ENABLED = "desktop_biometrics_enabled"
    const val PREFERENCE_DESKTOP_BIO_CIPHER = "desktop_bio_cipher"

    private val backupDocumentKey = stringPreferencesKey(PREFERENCE_BACKUP_DOCUMENT)
    private val defaultUserNameKey = stringPreferencesKey(PREFERENCE_DEFAULT_USER_NAME)
    private val lockOnScreenLockKey = booleanPreferencesKey(PREFERENCE_LOCK_ON_SCREEN_LOCK)
    private val clipboardClearDelayKey = stringPreferencesKey(PREFERENCE_CLIPBOARD_CLEAR_DELAY)
    private val lockTimeoutMinutesKey = stringPreferencesKey(PREFERENCE_LOCK_TIMEOUT_MINUTES)
    private val notificationPermissionRequiredKey = booleanPreferencesKey(NOTIFICATION_PERMISSION_REQUIRED)
    private val notificationPermissionDeniedKey = booleanPreferencesKey(NOTIFICATION_PERMISSION_DENIED)
    private val experimentalFeaturesKey = stringSetPreferencesKey(PREFERENCE_EXPERIMENTAL_FEATURES)
    private val extensionsKey = stringSetPreferencesKey(PREFERENCE_EXTENSIONS_KEY)
    private val lastBackupTimeKey = longPreferencesKey(PREFERENCE_LAST_BACKUP_TIME)
    private val autoBackupQuotaExceededKey = longPreferencesKey(PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED)
    private val autoBackupRestoreFinishedKey = longPreferencesKey(PREFERENCE_AUTOBACKUP_RESTORE_FINISHED)
    private val autoBackupRestoreStartedKey = longPreferencesKey(PREFERENCE_AUTOBACKUP_RESTORE_STARTED)
    private val autoBackupStartedKey = longPreferencesKey(PREFERENCE_AUTOBACKUP_STARTED)
    private val softDeleteDaysKey = intPreferencesKey(PREFERENCE_SOFT_DELETE_DAYS)
    private val gpmImportUsageShownKey = longPreferencesKey(PREFERENCE_GPM_IMPORT_USAGE_SHOWN)
    private val lastModifiedKey = longPreferencesKey(PREFERENCE_LAST_MODIFIED)
    private val desktopBiometricsEnabledKey = booleanPreferencesKey(PREFERENCE_DESKTOP_BIOMETRICS_ENABLED)
    private val desktopBioCipherKey = stringPreferencesKey(PREFERENCE_DESKTOP_BIO_CIPHER)

    fun getBackupDocument(): String =
        if (SUPPORT_EXPORT_LOCATION_MEMORY) {
            getCached(backupDocumentKey, preferenceBackupPathDefaultValue)
        } else {
            preferenceBackupPathDefaultValue
        }

    fun setBackupDocument(uriString: String?) =
        setCached(backupDocumentKey, uriString)

    fun getDefaultUserName(): String =
        getCached(defaultUserNameKey, "")

    fun getLockOnScreenLock(default: Boolean): Boolean =
        getCached(lockOnScreenLockKey, default)

    fun getClipboardClearDelaySecs(): Int =
        getCached(clipboardClearDelayKey, PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE)
            .toIntOrNull() ?: PREFERENCE_CLIPBOARD_CLEAR_DELAY_DEFAULT_VALUE.toInt()

    fun getLockTimeoutDuration(): Duration =
        (getCached(lockTimeoutMinutesKey, PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES)
            .toIntOrNull() ?: PREFERENCE_LOCK_TIMEOUT_DEFAULT_VALUE_MINUTES.toInt())
            .toDuration(DurationUnit.MINUTES)

    fun setNotificationPermissionRequired(value: Boolean) =
        setCached(notificationPermissionRequiredKey, value)

    fun getNotificationPermissionRequired(): Boolean =
        getCached(notificationPermissionRequiredKey, false)

    fun setNotificationPermissionDenied(value: Boolean) =
        setCached(notificationPermissionDeniedKey, value)

    fun getNotificationPermissionDenied(): Boolean =
        getCached(notificationPermissionDeniedKey, false)

    private fun storeTimestamp(keyObj: DataStorePreferences.Key<Long>) =
        setCached(keyObj, DateUtils.toUnixSeconds(Clock.System.now()))

    private fun getStoredTimestamp(keyObj: DataStorePreferences.Key<Long>): kotlinx.datetime.Instant? =
        getCached(keyObj, 0L).let { if (it != 0L) DateUtils.unixEpochSecondsToInstant(it) else null }

    fun setLastBackupTime() = storeTimestamp(lastBackupTimeKey)
    fun getLastBackupTime() = getStoredTimestamp(lastBackupTimeKey)

    fun getEnabledExperimentNames(): Set<String> =
        getCached(experimentalFeaturesKey, emptySet())

    fun clearAllPlugins() =
        setCached(experimentalFeaturesKey, emptySet())

    fun autoBackupQuotaExceeded() = storeTimestamp(autoBackupQuotaExceededKey)
    fun getAutoBackupQuotaExceeded() = getStoredTimestamp(autoBackupQuotaExceededKey)

    fun autoBackupRestoreStarts() = storeTimestamp(autoBackupRestoreStartedKey)
    fun getAutoBackupRestoreStarts() = getStoredTimestamp(autoBackupRestoreStartedKey)

    fun autoBackupStarts() = storeTimestamp(autoBackupStartedKey)
    fun getAutoBackupStarts() = getStoredTimestamp(autoBackupStartedKey)

    fun autoBackupRestoreFinished() = storeTimestamp(autoBackupRestoreFinishedKey)
    fun getAutoBackupRestoreFinished() = getStoredTimestamp(autoBackupRestoreFinishedKey)

    fun getSoftDeleteDays(): Int =
        getCached(softDeleteDaysKey, 30)

    fun gpmImportUsageShown() = storeTimestamp(gpmImportUsageShownKey)
    fun getGpmImportUsageShown(): Long =
        getCached(gpmImportUsageShownKey, 0L)

    fun setLastModified() = storeTimestamp(lastModifiedKey)
    fun getLastModified() = getStoredTimestamp(lastModifiedKey)

    fun getAllExtensions(): Set<String> =
        getCached(extensionsKey, emptySet())

    fun storeAllExtensions(extensions: Set<String>) =
        setCached(extensionsKey, extensions)

    fun isDesktopBiometricsRegistered(): Boolean =
        getCached(desktopBiometricsEnabledKey, false)

    fun getDesktopBioCipher(): String? =
        getCachedOrNull(desktopBioCipherKey)

    fun registerDesktopBiometrics(base64Cipher: String) {
        synchronized(cacheLock) {
            cache[desktopBiometricsEnabledKey.name] = PreferenceValue.BooleanVal(true)
            cache[desktopBioCipherKey.name] = PreferenceValue.StringVal(base64Cipher)
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
        notifyPlatformListeners(desktopBiometricsEnabledKey.name)
        notifyPlatformListeners(desktopBioCipherKey.name)
    }

    fun clearDesktopBiometrics() {
        synchronized(cacheLock) {
            cache.remove(desktopBiometricsEnabledKey.name)
            cache.remove(desktopBioCipherKey.name)
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
        notifyPlatformListeners(desktopBiometricsEnabledKey.name)
        notifyPlatformListeners(desktopBioCipherKey.name)
    }
}
