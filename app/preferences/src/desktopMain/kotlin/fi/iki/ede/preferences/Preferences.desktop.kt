package fi.iki.ede.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import java.io.File
import kotlin.time.ExperimentalTime

actual val preferenceBackupPathDefaultValue: String
    get() = "passwordsafe.xml"

actual fun createDataStore(context: Any?): DataStore<DataStorePreferences> {
    val userHome = System.getProperty("user.home") ?: "."
    val file = File(userHome, ".safe_desktop_settings.preferences_pb")
    return PreferenceDataStoreFactory.create(
        produceFile = { file }
    )
}

actual fun notifyPlatformListeners(key: String) {
    // No-op
}

@OptIn(ExperimentalTime::class)
actual fun initializePlatform(context: Any?) {
    Preferences.sharedPreferences = object : SharedPreferences {}
}
