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
import fi.iki.ede.safe.splits.RegistrationAPI

private const val TAG = "PluginLoaderViewModel"

enum class PluginName(val pluginName: String) {
    CATEGORY_PAGER("categorypager"),
}

class PluginLoaderViewModel(app: Application) : AndroidViewModel(app) {
    private val splitInstallManager = SplitInstallManagerFactory.create(getApplication())
    private var sessionId = 0

    private val listener = SplitInstallStateUpdatedListener { state ->
        if (state.sessionId() == sessionId) {
            val pluginName = PluginName.entries.first {
                it.pluginName == state.moduleNames().first()
            }

            when (state.status()) {
                SplitInstallSessionStatus.FAILED -> {
                    Log.d(TAG, "${pluginName.pluginName} install failed with ${state.errorCode()}")
                    Toast.makeText(
                        getApplication(),
                        "${pluginName.pluginName} install failed with ${state.errorCode()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                SplitInstallSessionStatus.INSTALLED -> {
                    Toast.makeText(
                        getApplication(),
                        "${pluginName.pluginName} module installed successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    initializePlugin(getApplication(), pluginName)
                    sessionId = 0
                }

                else -> Log.d(TAG, "Status: ${pluginName.pluginName} ${state.status()}")
            }
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
        // TODO: TOGGLE THIS IN BUNDLE INSTALL TEST
        if (PluginManager.isPluginInstalled(splitInstallManager, pluginName))
            initializePlugin(getApplication(), pluginName)
        else
            requestStorageInstall(pluginName)

    private fun requestStorageInstall(pluginName: PluginName): RegistrationAPI? {
        Toast.makeText(getApplication(), "Requesting storage module install", Toast.LENGTH_SHORT)
            .show()
        val request =
            SplitInstallRequest
                .newBuilder()
                .addModule(pluginName.pluginName)
                .build()

        splitInstallManager
            .startInstall(request)
            .addOnSuccessListener { id -> sessionId = id }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error installing module: ", exception)
                Toast.makeText(
                    getApplication(),
                    "Error requesting module install",
                    Toast.LENGTH_SHORT
                ).show()
            }
        return null
    }
}