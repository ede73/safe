@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package fi.iki.ede.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual val preferenceBackupPathDefaultValue: String
    get() {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        return (documentDirectory?.path ?: "") + "/${Preferences.PASSWORDSAFE_EXPORT_FILE}"
    }

actual fun createDataStore(): DataStore<DataStorePreferences> {
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null
            )
            val path = requireNotNull(documentDirectory?.path) {
                "Could not find iOS documents directory for DataStore"
            }
            "$path/safe_preferences.preferences_pb".toPath()
        }
    )
}

actual fun notifyPlatformListeners(key: String) {
    // No-op
}

actual fun initializePlatform() {
    Preferences.sharedPreferences = object : SharedPreferences {}
}
