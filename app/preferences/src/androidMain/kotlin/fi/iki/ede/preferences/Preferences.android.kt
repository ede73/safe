@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

actual val preferenceBackupPathDefaultValue: String
    get() = (android.os.Environment.getExternalStorageDirectory()?.absolutePath ?: "") + "/${Preferences.PASSWORDSAFE_EXPORT_FILE}"

actual fun createDataStore(context: Any?): DataStore<DataStorePreferences> {
    val ctx = context as Context
    return PreferenceDataStoreFactory.create(
        migrations = listOf(
            SharedPreferencesMigration(
                context = ctx,
                sharedPreferencesName = ctx.packageName + "_preferences"
            )
        ),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { ctx.preferencesDataStoreFile("safe_preferences") }
    )
}

actual fun notifyPlatformListeners(key: String) {
    (Preferences.sharedPreferences as? DataStoreSharedPreferences)?.notifyListeners(key)
}

actual fun initializePlatform(context: Any?) {
    val ctx = context as Context
    PreferenceManager.setDefaultValues(ctx, R.xml.preferences, false)
    Preferences.sharedPreferences = DataStoreSharedPreferences(Preferences.dataStore)
}
