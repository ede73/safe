package fi.iki.ede.autolock

import android.content.Context
import androidx.activity.ComponentActivity

object AutolockingFeaturesImpl : AutoLockingFeatures {
    private var lockTheApplicationCallback: ((context: Context) -> Unit)? = null
    private var startLoginScreenCallback: ((context: Context) -> Unit)? = null
    private var startEditSiteEntryCallback: ((context: Context, siteEntryID: Long) -> Unit)? = null
    private var isLoggedInCallback: (() -> Boolean)? = null
    private var isThisLoginScreenCallback: ((componentActivity: ComponentActivity) -> Boolean)? =
        null

    fun registerCallbacks(
        lockTheApplication: (context: Context) -> Unit,
        startLoginScreen: (context: Context) -> Unit,
        startEditSiteEntry: (context: Context, siteEntryID: Long) -> Unit,
        isLoggedIn: () -> Boolean,
        isThisLoginScreen: (componentActivity: ComponentActivity) -> Boolean
    ) {
        lockTheApplicationCallback = lockTheApplication
        startLoginScreenCallback = startLoginScreen
        startEditSiteEntryCallback = startEditSiteEntry
        isLoggedInCallback = isLoggedIn
        isThisLoginScreenCallback = isThisLoginScreen
    }

    override fun lockApplication(context: Context) = lockTheApplicationCallback!!(context)
    override fun startLoginScreen(context: Context) = startLoginScreenCallback!!(context)
    override fun startEditSiteEntry(context: Context, siteEntryID: Long) =
        startEditSiteEntryCallback!!(context, siteEntryID)

    override fun isLoggedIn() = isLoggedInCallback!!()
    override fun isThisLoginScreen(componentActivity: ComponentActivity) =
        isThisLoginScreenCallback!!(componentActivity)

}

