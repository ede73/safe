package fi.iki.ede.safe.splits

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.BuildConfig
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.reflect.full.createInstance
import kotlin.time.ExperimentalTime

private const val TAG = "PluginManager"

object PluginManager {
    /* NEVER READ THIS */
    private var _bundleTestMode = false

    @ExperimentalTime
    fun getComposableInterface(plugin: PluginName): GetComposable? = try {
        firebaseLog("Get getComposableInterface for ${plugin.pluginName}")
        getPluginFQCN(plugin)?.let {
            firebaseLog("Get getComposableInterface got FGCN=$it")
            // If plugin isn't enabled, we won't allow it to function
            if (!isPluginEnabled(plugin)) {
                firebaseLog("Get getComposableInterface not enabled FGCN=$it")
                return null
            }
            try {
                firebaseLog("Trying to instantiate at getComposableInterface got FGCN=$it")
                return Class.forName(it).kotlin.let { getComposable ->
                    getComposable.createInstance() as GetComposable
                }
            } catch (c: ClassNotFoundException) {
                firebaseRecordException(c)
            }
            null
        }
    } catch (e: ServiceConfigurationError) {
        firebaseRecordException(e)
        null
    }

    fun initializePlugin(context: Context, pluginName: PluginName): RegistrationAPI? {
        //if (!isPluginEnabled(pluginName)) return null
        // If plugin isn't enabled, we won't allow it to function
        try {
            firebaseLog("initializePlugin Get ServiceLoader of RegistrationAPI.Provider::class.java(${RegistrationAPI.Provider::class.java.name}) for $pluginName")
            val serviceLoader = ServiceLoader.load(
                RegistrationAPI.Provider::class.java,
                RegistrationAPI.Provider::class.java.classLoader
            )
            require(serviceLoader != null) { "Did not get service loader" }
            // Explicitly ONLY use the .iterator() method on the returned ServiceLoader to enable R8 optimization.
            // When these two conditions are met, R8 replaces ServiceLoader calls with direct object instantiation.
            firebaseLog("initializePlugin Get ServiceLoader iterator for $pluginName")
            val iterator = serviceLoader.iterator()
            firebaseLog("initializePlugin iterator hasNext() for $pluginName")
            if (!iterator.hasNext()) {
                firebaseLog("initializePlugin There is NO next iterator available!?!?")
                return null
            }

            while (iterator.hasNext()) {
                try {
                    val next = iterator.next()
                    firebaseLog("Got $next searching for ${pluginName.pluginName}")
                    val module = next.get()
                    firebaseLog("Got module=$module search ${pluginName.pluginName}")
                    if (module.getName() == pluginName) {
                        firebaseLog("Trying to register module=$module search ${pluginName.pluginName}")
                        module.register(context)
                        firebaseLog("Loaded $pluginName feature through ServiceLoader")
                        return module
                    }
                } catch (i: Throwable) {
                    // on samsung S7+ iterator.next throws...don't understand or know why, works everywhere else
                    firebaseRecordException("initializePLugin inner", i)
                }
            }
        } catch (t: Throwable) {
            firebaseRecordException("initializePLugin uncaught1", t)
        }
        return null
    }

    fun uninstallPlugin(splitInstallManager: SplitInstallManager, plugin: PluginName) {
        splitInstallManager.deferredUninstall(listOf(plugin.pluginName))
            .addOnSuccessListener { firebaseLog("Successfully uninstalled module  $plugin") }
            .addOnFailureListener { firebaseLog("Failed to uninstall module  $plugin") }
            .addOnCanceledListener { firebaseLog("Cancelled uninstalling module  $plugin") }
            .addOnCompleteListener { firebaseLog("Completed uninstalling module  $plugin") }
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

    @ExperimentalTime
    fun reinitializePlugins(appContext: Context) {
        val sm = SplitInstallManagerFactory.create(appContext)
        Preferences.getEnabledExperiments().forEach {
            firebaseLog("Is enabled plugin $it installed?")
            if (isPluginInstalled(sm, it)) {
                firebaseLog(" is installed $it, initialize now")
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
                firebaseLog("getPluginFQCN $next searching for ${plugin.pluginName}")
                val module = next.get()
                if (module.getName() == plugin) {
                    firebaseLog("getPluginFQCN got $next searching for ${plugin.pluginName}")
                    return next.javaClass.canonicalName
                }
            }
        } catch (e: ServiceConfigurationError) {
            firebaseRecordException(e)
        }
        return null
    }

    private fun getBundleTestMode() = if (BuildConfig.DEBUG) _bundleTestMode else false

    @ExperimentalTime
    fun isPluginEnabled(plugin: PluginName) =
        plugin in Preferences.getEnabledExperiments()
}