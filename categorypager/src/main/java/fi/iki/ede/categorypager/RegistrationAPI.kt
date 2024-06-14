package fi.iki.ede.categorypager

import android.content.Context
import android.content.Intent
import android.util.Log
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.RegistrationAPI
import fi.iki.ede.safe.ui.activities.CategoryListScreen

class RegistrationAPIImpl : RegistrationAPI {
    override fun register(context: Context) {
        val intent = Intent(
            context,
            CategoryListPagedScreen::class.java
        ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

        IntentManager.replaceIntents("categorypager", CategoryListScreen::class.java, intent)
        // Implementation
        Log.e("RegistrationAPIImpl", "RegistrationAPIImpl::register()")
    }

    override fun deregister() {
        // Implementation
        Log.e("RegistrationAPIImpl", "RegistrationAPIImpl::deregister()")
    }

    override fun requestToDeregister(ex: Exception?) {
        // Implementation
        Log.e("RegistrationAPIImpl", "RegistrationAPIImpl::requestToDeregister()")
    }
}

class RegistrationAPIProviderImpl : RegistrationAPI.Provider {
    override fun get(): RegistrationAPI {
        Log.e("RegistrationAPIProviderImpl", "RegistrationAPIProviderImpl::get()")
        return RegistrationAPIImpl()
    }
}
