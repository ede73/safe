@file:OptIn(kotlin.time.ExperimentalTime::class)

package fi.iki.ede.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import java.io.File

import fi.iki.ede.crypto.DesktopPathUtils

actual val preferenceBackupPathDefaultValue: String
    get() = File(DesktopPathUtils.userHome, Preferences.PASSWORDSAFE_EXPORT_FILE).absolutePath

actual fun createDataStore(): DataStore<DataStorePreferences> {
    return PreferenceDataStoreFactory.create(
        produceFile = { DesktopPathUtils.preferencesFile }
    )
}

actual fun notifyPlatformListeners(key: String) {
    // No-op
}

actual fun initializePlatform() {
    Preferences.sharedPreferences = object : SharedPreferences {}
}
