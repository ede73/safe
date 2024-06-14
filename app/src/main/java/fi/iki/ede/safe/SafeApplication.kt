package fi.iki.ede.safe

import com.google.android.play.core.splitcompat.SplitCompatApplication
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.splits.registerDFM


class SafeApplication : SplitCompatApplication() {
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
        registerDFM(this, "categorypager")
    }
    
    companion object {
        private var instance: SafeApplication? = null
    }
}
