package fi.iki.ede.safe.ui

import android.content.Context
import androidx.activity.ComponentActivity
import fi.iki.ede.autolock.AutoLockingFeatures
import fi.iki.ede.db.DBID
import fi.iki.ede.safe.SafeApplication
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.activities.LoginScreen

object AutolockingFeaturesImpl : AutoLockingFeatures {
    override fun lockApplication(context: Context) =
        SafeApplication.lockTheApplication(context)

    override fun startLoginScreen(context: Context) =
        IntentManager.startLoginScreen(context, openCategoryScreenAfterLogin = false)

    override fun startEditSiteEntry(context: Context, siteEntryID: DBID) =
        IntentManager.startEditSiteEntryScreen(context, siteEntryID)

    override fun isLoggedIn() = LoginHandler.isLoggedIn()
    override fun isThisLoginScreen(componentActivity: ComponentActivity) =
        componentActivity is LoginScreen
}

