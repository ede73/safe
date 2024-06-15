package fi.iki.ede.safe.splits

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.HelpScreen
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.ui.activities.LoginScreen.Companion.OPEN_CATEGORY_SCREEN_AFTER_LOGIN
import fi.iki.ede.safe.ui.activities.PreferenceActivity
import fi.iki.ede.safe.ui.activities.PrepareDataBaseRestorationScreen
import fi.iki.ede.safe.ui.activities.PrepareDataBaseRestorationScreen.Companion.OISAFE_COMPATIBILITY
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.CATEGORY_ID
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.PASSWORD_ID
import fi.iki.ede.safe.ui.activities.SiteEntryListScreen
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import fi.iki.ede.safe.ui.models.PluginName

// Every intent retrieved/launched in the app go thru IntentManager
// If a plugin wants to tap into the intent, they can modify or even replace it
object IntentManager {
    private inline fun <reified T> getActivityIntent(
        context: Context,
        activityClass: Class<T>,
        flags: Int? = null,
        extras: Bundle? = null
    ): Intent {
        val enabledExperiments = Preferences.getEnabledExperiments()
        val replacements = intentReplacements.filter { (key, _) -> key in enabledExperiments }
            .flatMap { entry ->
                entry.value.filterKeys { it == activityClass }.map { Pair(entry.key, it.value) }
            }.associate { it.first to it.second }

        val askedIntent = Intent(context, activityClass).apply {
            flags?.let { setFlags(it) }
            extras?.let { putExtras(it) }
        }

        when (replacements.size) {
            1 -> return replacements.values.first().apply {
                // pass original extras, so plugin can parse same information
                extras?.let { putExtras(it) }
            }

            0 -> return askedIntent
            else -> {
                // oh no we have a conflict!
                val conflictingPlugins = replacements.keys.joinToString(", ")
                // TODO: Tell user to fix this (need to deactivate a module)
                // TODO: Or ask which one to deactivate?
                // TODO: or just deactivate both(all) and tell user and return default
                return askedIntent
            }
        }
    }

    private inline fun <reified T> startActivity(
        context: Context,
        activityClass: Class<T>,
        flags: Int? = null,
        extras: Bundle? = null
    ) = context.startActivity(getActivityIntent(context, activityClass, flags, extras))

    private val intentReplacements = mutableMapOf<PluginName, MutableMap<Class<*>, Intent>>()
    fun replaceIntents(plugin: PluginName, screen: Class<*>, intent: Intent) {
        intentReplacements.getOrPut(plugin) { mutableMapOf() }[screen] = intent
    }

    fun getDatabaseRestorationScreenIntent(
        context: Context,
        oiSafeCompatibility: Boolean,
        uri: Uri
    ) = Intent(
        context, PrepareDataBaseRestorationScreen::class.java
    ).putExtra(OISAFE_COMPATIBILITY, oiSafeCompatibility)
        .apply { data = uri }

    fun getEditPassword(context: Context, passwordId: DBID) =
        getActivityIntent(context, SiteEntryEditScreen::class.java, extras = Bundle().apply {
            putLong(PASSWORD_ID, passwordId)
        })

    fun getAddPassword(context: Context, categoryId: DBID) =
        getActivityIntent(context, SiteEntryEditScreen::class.java, extras = Bundle().apply {
            putLong(CATEGORY_ID, categoryId)
        })

    // start
    fun startPreferencesActivity(context: Context) =
        startActivity(
            context,
            PreferenceActivity::class.java,
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )

    fun startCategoryScreen(context: Context) =
        startActivity(
            context,
            CategoryListScreen::class.java,
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )

    fun startHelpScreen(context: Context) =
        startActivity(context, HelpScreen::class.java)

    fun startLoginScreen(context: Context, openCategoryScreenAfterLogin: Boolean = true) =
        startActivity(context, LoginScreen::class.java, extras = Bundle().apply {
            putBoolean(OPEN_CATEGORY_SCREEN_AFTER_LOGIN, openCategoryScreenAfterLogin)
        })

    fun startSiteEntrySearchScreen(context: Context) =
        startActivity(context, SiteEntrySearchScreen::class.java)

    fun startSiteEntryListScreen(context: Context, id: DBID) =
        startActivity(context, SiteEntryListScreen::class.java,
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            extras = Bundle().apply {
                putLong(SiteEntryListScreen.CATEGORY_ID, id)
            })
}