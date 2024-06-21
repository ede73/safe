package fi.iki.ede.safe.splits

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.model.Preferences
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.reflect.full.createInstance

private const val TAG = "PluginManager"

object PluginManager {
    /* NEVER READ THIS */
    private var _bundleTestMode = false

    fun getComposableInterface(plugin: PluginName): GetComposable? = try {
        Firebase.crashlytics.log("Get getComposableInterface for ${plugin.pluginName}")
        getPluginFQCN(plugin)?.let {
            Firebase.crashlytics.log("Get getComposableInterface got FGCN=$it")
            // If plugin isn't enabled, we won't allow it to function
            if (!isPluginEnabled(plugin)) return null
            try {
                Firebase.crashlytics.log("Trying to instantiate at getComposableInterface got FGCN=$it")
                return Class.forName(it).kotlin.let { getComposable ->
                    getComposable.createInstance() as GetComposable
                }
            } catch (c: ClassNotFoundException) {
                Firebase.crashlytics.recordException(c)
                Log.e(TAG, "${plugin.pluginName} getComposable not found")
            }
            null
        }
    } catch (e: ServiceConfigurationError) {
        Firebase.crashlytics.recordException(e)
        null
    }

    fun initializePlugin(context: Context, pluginName: PluginName): RegistrationAPI? {
        //if (!isPluginEnabled(pluginName)) return null
        // If plugin isn't enabled, we won't allow it to function
        Firebase.crashlytics.log("Get ServiceLoader of RegistrationAPI.Provider::class.java(${RegistrationAPI.Provider::class.java.name}) for $pluginName")
        val serviceLoader = ServiceLoader.load(
            RegistrationAPI.Provider::class.java,
            RegistrationAPI.Provider::class.java.classLoader
        )
        require(serviceLoader != null) { "Did not get service loader" }
        // Explicitly ONLY use the .iterator() method on the returned ServiceLoader to enable R8 optimization.
        // When these two conditions are met, R8 replaces ServiceLoader calls with direct object instantiation.
        try {
            Firebase.crashlytics.log("Get ServiceLoader iterator for $pluginName")
            val iterator = serviceLoader.iterator()
            if (!iterator.hasNext()) {
                println("There is NO next iterator available!?!?")
                return null
            }

            while (iterator.hasNext()) {
                try {
                    val next = iterator.next()
                    Firebase.crashlytics.log("Got $next searching for ${pluginName.pluginName}")
                    val module = next.get()
                    Firebase.crashlytics.log("Got module=$module search ${pluginName.pluginName}")
                    if (module.getName() == pluginName) {
                        Firebase.crashlytics.log("Trying to register module=$module search ${pluginName.pluginName}")
                        module.register(context)
                        Log.d(TAG, "Loaded $pluginName feature through ServiceLoader")
                        return module
                    }
                } catch (i: Exception) {
                    // on samsung S7+ iterator.next throws...don't understand or know why, workse everywhere else
                    Firebase.crashlytics.recordException(i)
                }
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
        }
        return null
    }

    fun uninstallPlugin(splitInstallManager: SplitInstallManager, plugin: PluginName) {
        splitInstallManager.deferredUninstall(listOf(plugin.pluginName))
            .addOnSuccessListener { Firebase.crashlytics.log("Successfully uninstalled module  $plugin") }
            .addOnFailureListener { Firebase.crashlytics.log("Failed to uninstall module  $plugin") }
            .addOnCanceledListener { Firebase.crashlytics.log("Cancelled uninstalling module  $plugin") }
            .addOnCompleteListener { Firebase.crashlytics.log("Completed uninstalling module  $plugin") }
    }

    /**
     * Literally answers if plugin is INSTALLED, it is different from enabled
     * Use isPluginEnabled() if you need to know if user wants the plugin active
     */
    fun isPluginInstalled(splitInstallManager: SplitInstallManager, pluginName: PluginName) =
        if (getBundleTestMode())
            false
        else if (BuildConfig.DEBUG)
            true
        else splitInstallManager.installedModules.contains(pluginName.pluginName)

    fun setBundleTestMode(isBundleTest: Boolean) {
        if (BuildConfig.DEBUG) {
            _bundleTestMode = isBundleTest
        }
    }

    fun reinitializePlugins(appContext: Context) {
        val sm = SplitInstallManagerFactory.create(appContext)
        Preferences.getEnabledExperiments().forEach {
            Firebase.crashlytics.log("Is enabled plugin $it installed?")
            if (isPluginInstalled(sm, it)) {
                Firebase.crashlytics.log(" is installed $it, initialize now")
                initializePlugin(appContext, it)
            }
        }
    }

    private fun getPluginFQCN(plugin: PluginName): String? {
        try {
            val serviceLoader = ServiceLoader.load(
                RegistrationAPI.Provider::class.java,
                RegistrationAPI.Provider::class.java.classLoader
            )
            val iterator = serviceLoader.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                val module = next.get()
                if (module.getName() == plugin) {
                    return next.javaClass.canonicalName
                }
            }
        } catch (e: ServiceConfigurationError) {
            Firebase.crashlytics.recordException(e)
        }
        return null
    }

    private fun getBundleTestMode() = if (BuildConfig.DEBUG) _bundleTestMode else false

    private fun isPluginEnabled(plugin: PluginName) =
        plugin in Preferences.getEnabledExperiments()
}