package fi.iki.ede.categorypager

import android.content.Context
import android.content.Intent
import android.util.Log
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.RegistrationAPI
import fi.iki.ede.safe.ui.activities.CategoryListScreen

private val TAG = PluginName.CATEGORY_PAGER.pluginName

class RegistrationAPIImpl : RegistrationAPI {
    override fun register(context: Context) {
        Log.e(TAG, "RegistrationAPIImpl::register()")
        val intent = Intent(
            context,
            CategoryListPagedScreen::class.java
        ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

        IntentManager.replaceIntents(
            PluginName.CATEGORY_PAGER,
            CategoryListScreen::class.java,
            intent
        )
    }

    override fun deregister() {
        Log.e(TAG, "RegistrationAPIImpl::deregister()")
    }

    override fun getName() = PluginName.CATEGORY_PAGER

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
