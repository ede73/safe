package fi.iki.ede.safe.splits

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.model.Preferences
import java.util.ServiceLoader
import kotlin.reflect.full.createInstance

private const val TAG = "PluginManager"

object PluginManager {
    /* NEVER READ THIS */
    private var _bundleTestMode = false

    fun setBundleTestMode(isBundleTest: Boolean) {
        if (BuildConfig.DEBUG) {
            _bundleTestMode = isBundleTest
        }
    }

    fun getBundleTestMode() = if (BuildConfig.DEBUG) _bundleTestMode else false

    fun reinitializePlugins(appContext: Context) {
        val sm = SplitInstallManagerFactory.create(appContext)
        Preferences.getEnabledExperiments().forEach {
            println("Test $it")
            if (isPluginInstalled(sm, it)) {
                println(" is installed $it, initialize now")
                initializePlugin(appContext, it)
            }
        }
    }

    fun isPluginEnabled(plugin: PluginName) =
        plugin in Preferences.getEnabledExperiments()

    /**
     * Literally answers if plugin is INSTALLED, it is different from enabled
     * Use isPluginEnabled() if you need to know if user wants the plugin active
     */
    fun isPluginInstalled(splitInstallManager: SplitInstallManager, pluginName: PluginName) =
        if (getBundleTestMode())
            false
        else if (BuildConfig.DEBUG)
            true
        else
            splitInstallManager.installedModules.contains(
                pluginName.pluginName
            )

    fun getComposableInterface(plugin: PluginName): GetComposable? =
        getPluginFQCN(plugin)?.let {
            // If plugin isn't enabled, we won't allow it to function
            if (!isPluginEnabled(plugin)) return null
            try {
                return Class.forName(it).kotlin.let { getComposable ->
                    getComposable.createInstance() as GetComposable
                }
            } catch (c: ClassNotFoundException) {
                Log.e(TAG, "${plugin.pluginName} getComposable not found")
            }
            null
        }

    private fun getPluginFQCN(plugin: PluginName): String? {
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
        return null
    }

    fun initializePlugin(context: Context, pluginName: PluginName): RegistrationAPI? {
        //if (!isPluginEnabled(pluginName)) return null
        // If plugin isn't enabled, we won't allow it to function
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
            print("Got $next searching for ${pluginName.pluginName}")
            val module = next.get()
            print("Got2 $module search ${pluginName.pluginName}")
            if (module.getName() == pluginName) {
                module.register(context)
                Log.d(TAG, "Loaded $pluginName feature through ServiceLoader")
                return module
            }
        }
        return null
    }
}