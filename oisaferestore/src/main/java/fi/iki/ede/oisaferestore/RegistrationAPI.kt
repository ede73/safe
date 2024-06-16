package fi.iki.ede.oisaferestore

import android.content.Context
import android.content.Intent
import android.util.Log
import fi.iki.ede.safe.splits.DRODOWN_MENU
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.RegistrationAPI

private val TAG = PluginName.OISAFERESTORE.pluginName

// https://github.com/googlearchive/android-dynamic-features/blob/master/app/src/main/java/com/google/android/samples/dynamicfeatures/MainActivity.kt#L151
class RegistrationAPIImpl : RegistrationAPI {
    override fun register(mainContext: Context) {
        Log.e(TAG, "RegistrationAPIImpl::register()")
        IntentManager.registerSubMenu(
            getName(),
            DRODOWN_MENU.TOPACTIONBAR_IMPORT_EXPORT_MENU,
            fi.iki.ede.safe.R.string.action_bar_old_restore
        ) { context ->
            context.startActivity(
                Intent(
                    context,
                    SelectDocumentAndBeginRestoreActivity::class.java
                )
            )
        }
    }

    override fun deregister() {
        // Implementation
        Log.e(TAG, "RegistrationAPIImpl::deregister()")
    }

    override fun getName() = PluginName.OISAFERESTORE

    override fun requestToDeregister(ex: Exception?) {
        // Implementation
        Log.e(TAG, "RegistrationAPIImpl::requestToDeregister()")
    }
}

class RegistrationAPIProviderImpl : RegistrationAPI.Provider {
    override fun get(): RegistrationAPI {
        Log.e(TAG, "RegistrationAPIProviderImpl::get()")
        return RegistrationAPIImpl()
    }
}
