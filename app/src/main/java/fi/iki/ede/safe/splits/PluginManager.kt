package fi.iki.ede.safe.splits

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.models.PluginName
import java.util.ServiceLoader

private const val TAG = "PluginManager"

object PluginManager {
    fun reinitializePlugins(appContext: Context) {
        val sm = SplitInstallManagerFactory.create(appContext)
        Preferences.getEnabledExperiments().forEach {
            if (isPluginInstalled(sm, it)) {
                initializePlugin(appContext, it)
            }
        }
    }

    fun isPluginInstalled(splitInstallManager: SplitInstallManager, pluginName: PluginName) =
        if (BuildConfig.DEBUG) true else splitInstallManager.installedModules.contains(
            pluginName.pluginName
        )

    fun initializePlugin(context: Context, pluginName: PluginName): RegistrationAPI? {
        val serviceLoader = ServiceLoader.load(
            RegistrationAPI.Provider::class.java,
            RegistrationAPI.Provider::class.java.classLoader
        )
        require(serviceLoader != null) { "Did not get service loader" }
        // Explicitly ONLY use the .iterator() method on the returned ServiceLoader to enable R8 optimization.
        // When these two conditions are met, R8 replaces ServiceLoader calls with direct object instantiation.
        val iterator = serviceLoader.iterator()
        if (!iterator.hasNext()) {
            println("There is NO next iterator available!?!?")
            return null
        }

        while (iterator.hasNext()) {
            val next = iterator.next()
            val module = next.get()
            if (module.getName() == pluginName) {
                module.register(context)
                Log.d(TAG, "Loaded $pluginName feature through ServiceLoader")
                return module
            }
        }
        return null
    }
}