package fi.iki.ede.oisaferestore

import android.content.Context
import android.content.Intent
import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.splits.DropDownMenu
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.RegistrationAPI

private val TAG = PluginName.OISAFERESTORE.pluginName

// https://github.com/googlearchive/android-dynamic-features/blob/master/app/src/main/java/com/google/android/samples/dynamicfeatures/MainActivity.kt#L151
class RegistrationAPIImpl : RegistrationAPI {
    override fun register(mainContext: Context) {
        Logger.e(TAG, "RegistrationAPIImpl::register()")
        IntentManager.registerSubMenu(
            getName(),
            DropDownMenu.TopActionBarImportExportMenu,
            // TODO: move locally
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
        Logger.e(TAG, "RegistrationAPIImpl::deregister()")
    }

    override fun getName() = PluginName.OISAFERESTORE

    override fun requestToDeregister(ex: Exception?) {
        // Implementation
        Logger.e(TAG, "RegistrationAPIImpl::requestToDeregister()")
    }
}

class RegistrationAPIProviderImpl : RegistrationAPI.Provider {
    override fun get(): RegistrationAPI {
        Logger.e(TAG, "RegistrationAPIProviderImpl::get()")
        return RegistrationAPIImpl()
    }
}
