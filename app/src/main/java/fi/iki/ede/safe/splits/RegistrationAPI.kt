package fi.iki.ede.safe.splits

import android.content.Context

interface RegistrationAPI {
    // Main app asks DFM module to perform registration
    fun register(context: Context)

    // Main app tells DFM module to perform de-registration
    fun deregister()

    fun getName(): PluginName

    // used if DFM module notices itself it is malfunctioning, asks main app to dereg
    // main app would also inform user
    fun requestToDeregister(ex: Exception? = null)

    interface Provider {
        fun get(): RegistrationAPI
    }
}

