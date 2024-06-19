package fi.iki.ede.safe.ui.models

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import fi.iki.ede.safe.splits.PluginManager
import fi.iki.ede.safe.splits.PluginManager.initializePlugin
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.RegistrationAPI
import java.util.Collections

private const val TAG = "PluginLoaderViewModel"

data class SplitSession(val sessionId: Int, val plugin: PluginName) {
    var completed: Boolean = false
}

class PluginLoaderViewModel(app: Application) : AndroidViewModel(app) {
    private val splitInstallManager = SplitInstallManagerFactory.create(getApplication())

    private val sessions = Collections.synchronizedList(mutableListOf<SplitSession>())

    private fun informUser(message: String) = Toast.makeText(
        getApplication(), message, Toast.LENGTH_SHORT
    ).show()

    private val listener = SplitInstallStateUpdatedListener { state ->
        val session = sessions.firstOrNull { it.sessionId == state.sessionId() }
        if (session == null) return@SplitInstallStateUpdatedListener

        when (state.status()) {
            SplitInstallSessionStatus.FAILED -> {
                Log.d(TAG, "${session.plugin.pluginName} install failed with ${state.errorCode()}")
                informUser("${session.plugin.pluginName} install failed with ${state.errorCode()}")
                session.completed = true
                synchronized(sessions) {
                    sessions.removeIf { it.sessionId == state.sessionId() }
                }
            }

            SplitInstallSessionStatus.INSTALLED -> {
                informUser("${session.plugin.pluginName} module installed successfully")
                initializePlugin(getApplication(), session.plugin)
                session.completed = true
                synchronized(sessions) {
                    sessions.removeIf { it.sessionId == state.sessionId() }
                }
            }

            else -> Log.d(TAG, "Status: ${session.plugin.pluginName} ${state.status()}")
        }
    }

    init {
        splitInstallManager.registerListener(listener)
    }

    override fun onCleared() {
        splitInstallManager.unregisterListener(listener)
        super.onCleared()
    }

    fun getOrInstallPlugin(pluginName: PluginName): RegistrationAPI? =
        // Run app-bundle-dfm with launch flags: "-e isBundleTest true"
        if (PluginManager.isPluginInstalled(splitInstallManager, pluginName))
            initializePlugin(getApplication(), pluginName)
        else
            requestStorageInstall(pluginName).let { null }

    private fun requestStorageInstall(plugin: PluginName) {
        informUser("Requesting storage module install")
        SplitInstallRequest
            .newBuilder()
            .addModule(plugin.pluginName)
            .build().let { request ->
                splitInstallManager
                    .startInstall(request)
                    .addOnSuccessListener { id ->
                        synchronized(sessions) {
                            sessions.add(SplitSession(id, plugin))
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error installing module: ", exception)
                        informUser("Error requesting module install ${plugin.pluginName}")
                    }
            }
    }
}