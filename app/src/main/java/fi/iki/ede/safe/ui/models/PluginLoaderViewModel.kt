package fi.iki.ede.safe.ui.models

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import fi.iki.ede.logger.firebaseJustTry
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.safe.splits.PluginManager
import fi.iki.ede.safe.splits.PluginManager.initializePlugin
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.RegistrationAPI

private const val TAG = "PluginLoaderViewModel"

data class SplitSession(val sessionId: Int, val plugin: PluginName) {
    var completed: Boolean = false
}

class PluginLoaderViewModel(app: Application) : AndroidViewModel(app) {
    private val splitInstallManager = SplitInstallManagerFactory.create(getApplication())

    private val sessions = mutableListOf<SplitSession>()

    private fun informUser(message: String) {
        firebaseLog(message)
        Toast.makeText(
            getApplication(), message, Toast.LENGTH_SHORT
        ).show()
    }

    private val listener = SplitInstallStateUpdatedListener { state ->
        val session = synchronized(sessions) { sessions.firstOrNull { it.sessionId == state.sessionId() } }
        if (session == null) return@SplitInstallStateUpdatedListener

        when (state.status()) {
            SplitInstallSessionStatus.FAILED -> {
                informUser("${session.plugin.pluginName} install failed with ${state.errorCode()}")
                session.completed = true
                synchronized(sessions) {
                    sessions.removeIf { it.sessionId == state.sessionId() }
                }
            }

            SplitInstallSessionStatus.INSTALLED -> {
                informUser("${session.plugin.pluginName} module installed successfully")
                firebaseJustTry("SplitInstallSessionStatus.INSTALLED try to init too") {
                    initializePlugin(getApplication(), session.plugin)
                }
                session.completed = true
                synchronized(sessions) {
                    sessions.removeIf { it.sessionId == state.sessionId() }
                }
            }

            else -> firebaseLog(
                TAG,
                "Status: ${session.plugin.pluginName} ${state.status()}"
            )
        }
    }

    init {
        splitInstallManager.registerListener(listener)
    }

    override fun onCleared() {
        splitInstallManager.unregisterListener(listener)
        super.onCleared()
    }

    fun uninstallPlugin(pluginName: PluginName) {
        try {
            PluginManager.uninstallPlugin(splitInstallManager, pluginName)
        } catch (e: Exception) {
            firebaseLog("Error uninstalling plugin $pluginName -> $e")
        }
    }

    fun getOrInstallPlugin(pluginName: PluginName): RegistrationAPI? =
        try {
            // Run app-bundle-dfm with launch flags: "-e isBundleTest true"
            if (PluginManager.isPluginInstalled(splitInstallManager, pluginName)) {
                firebaseLog("Plugin $pluginName is already installed..so pluginManager claims")
                initializePlugin(getApplication(), pluginName)
            } else {
                requestStorageInstall(pluginName).let { null }
            }
        } catch (ex: Exception) {
            firebaseRecordException(ex)
            null
        }

    private fun requestStorageInstall(plugin: PluginName) {
        informUser("Requesting storage module install ${plugin.pluginName}")
        SplitInstallRequest
            .newBuilder()
            .addModule(plugin.pluginName)
            .build().let { request ->
                splitInstallManager
                    .startInstall(request)
                    .addOnSuccessListener { id ->
                        firebaseLog("Successfully installed module  ${plugin.pluginName}")
                        synchronized(sessions) {
                            sessions.add(SplitSession(id, plugin))
                        }
                    }
                    .addOnCompleteListener {
                        firebaseLog("Completed installing module  ${plugin.pluginName}")
                    }
                    .addOnCanceledListener {
                        firebaseLog("Cancelled installing module  $plugin")
                    }
                    .addOnFailureListener { exception ->
                        firebaseRecordException(
                            "Error installing module  $plugin -> $exception",
                            exception
                        )
                        informUser("Error requesting module install ${plugin.pluginName}")
                    }
            }
    }
}
