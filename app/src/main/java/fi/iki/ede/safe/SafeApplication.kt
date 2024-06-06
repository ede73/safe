package fi.iki.ede.safe

import android.app.Application
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences


class SafeApplication : Application() {
    init {
        instance = this
//        if (BuildConfig.DEBUG) {
//            StrictMode.setVmPolicy(
//                StrictMode.VmPolicy.Builder()
//                    .detectLeakedClosableObjects()
//                    .penaltyLog()
//                    .build()
//            )
//            StrictMode.enableDefaults()
//        }
        DataModel.attachDBHelper(
            DBHelperFactory.getDBHelper(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        Preferences.initialize(this)
    }

    companion object {
        private var instance: SafeApplication? = null
    }
}
