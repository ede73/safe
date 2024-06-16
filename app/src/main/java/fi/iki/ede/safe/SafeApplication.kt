package fi.iki.ede.safe

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.model.Preferences.PREFERENCE_EXPERIMENTAL_FEATURES
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginManager.reinitializePlugins
import fi.iki.ede.safe.splits.PluginName


class SafeApplication : SplitCompatApplication(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    init {
        instance = this
//        if (BuildConfig.DEBUG) {
//            StrictMode.setVmPolicy(
//                StrictMode.VmPolicy.Builder()
//                    .detectLeakedClosableObjects()
//                    .penaltyLog()
//                    .build()
//            )
//            StrictMode.enableDefaults()
//        }
        DataModel.attachDBHelper(
            DBHelperFactory.getDBHelper(this),
        )
        Thread.setDefaultUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()
            ?.let { MyExceptionHandler(it) })
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        throw RuntimeException("Test Crash")
        Preferences.initialize(this)
        reinitializePlugins(this)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        private var instance: SafeApplication? = null
    }

    // We want to ensure as plugins are loaded/unloaded their registration is correctly removed
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREFERENCE_EXPERIMENTAL_FEATURES) {
            println("==================")
            val enabledExperiments = Preferences.getEnabledExperiments()
            PluginName.entries.forEach {
                if (!(it in enabledExperiments)) {
                    // this plugin might have just been disabled
                    IntentManager.removePluginIntegrations(it)
                }
            }
        }
    }
}
