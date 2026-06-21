@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.preferences

import android.content.Context
import android.os.Environment
import androidx.preference.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private var appContext: Context? = null

fun setPreferencesContext(context: Context) {
    appContext = context.applicationContext
}

actual val preferenceBackupPathDefaultValue: String
    get() = (Environment.getExternalStorageDirectory()?.absolutePath ?: "") + "/${Preferences.PASSWORDSAFE_EXPORT_FILE}"

actual fun createDataStore(): DataStore<DataStorePreferences> {
    val ctx = checkNotNull(appContext) {
        "Preferences context must be set via setPreferencesContext(context) before initializing Preferences"
    }
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

actual fun initializePlatform() {
    val ctx = checkNotNull(appContext) {
        "Preferences context must be set via setPreferencesContext(context) before initializing Preferences"
    }
    PreferenceManager.setDefaultValues(ctx, R.xml.preferences, false)
    Preferences.sharedPreferences = DataStoreSharedPreferences(Preferences.dataStore)
}
