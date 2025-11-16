package fi.iki.ede.safe.splits

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import fi.iki.ede.db.DBID
import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.ui.activities.BackupDatabaseScreen
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.HelpScreen
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.ui.activities.LoginScreen.Companion.OPEN_CATEGORY_SCREEN_AFTER_LOGIN
import fi.iki.ede.safe.ui.activities.PreferenceActivity
import fi.iki.ede.safe.ui.activities.RestoreDatabaseScreen
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.CATEGORY_ID
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.SITE_ENTRY_ID
import fi.iki.ede.safe.ui.activities.SiteEntryListScreen
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import kotlin.time.ExperimentalTime

private const val TAG = "IntentManager"

@ExperimentalTime
fun Preferences.getEnabledExperiments(): Set<PluginName> =
    getEnabledExperimentNames()
        .mapNotNull { PluginName.entries.firstOrNull { p -> p.pluginName == it } }
        .toSet()


// Every intent retrieved/launched in the app go thru IntentManager
// If a plugin wants to tap into the intent, they can modify or even replace it
@ExperimentalTime
object IntentManager {
    fun registerSubMenu(
        plugin: PluginName,
        menu: DropDownMenu,
        stringResource: Int,
        selected: (Context) -> Unit
    ) = menuAdditions.getOrPut(plugin) { mutableMapOf() }
        .getOrPut(menu) { mutableListOf() }
        .add(stringResource to selected)

    fun replaceIntents(plugin: PluginName, screen: Class<*>, intent: Intent) {
        intentReplacements.getOrPut(plugin) { mutableMapOf() }[screen] = intent
    }

    fun getMenuItems(menu: DropDownMenu) =
        menuAdditions.flatMap { (_, menuMap) -> menuMap[menu]?.toList() ?: listOf() }

    @ExperimentalTime
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
                // TODO: Tell user to fix this (need to deactivate a module)
                // TODO: Or ask which one to deactivate?
                // TODO: or just deactivate both(all) and tell user and return default
                return askedIntent
            }
        }
    }

    private val intentReplacements = mutableMapOf<PluginName, MutableMap<Class<*>, Intent>>()

    private val menuAdditions =
        mutableMapOf<PluginName, MutableMap<DropDownMenu, MutableList<Pair<Int, (Context) -> Unit>>>>()

    @ExperimentalTime
    @ExperimentalFoundationApi
    fun getAddSiteEntryIntent(context: Context, siteEntryId: DBID) =
        getActivityIntent(context, SiteEntryEditScreen::class.java, extras = Bundle().apply {
            putLong(CATEGORY_ID, siteEntryId)
        })

    @ExperimentalTime
    @ExperimentalFoundationApi
    fun getEditSiteEntryIntent(context: Context, siteEntryId: DBID) =
        getActivityIntent(context, SiteEntryEditScreen::class.java, extras = Bundle().apply {
            putLong(SITE_ENTRY_ID, siteEntryId)
        })

    @ExperimentalTime
    @ExperimentalFoundationApi
    fun startEditSiteEntryScreen(context: Context, siteEntryId: DBID) =
        startActivity(
            context,
            SiteEntryEditScreen::class.java, extras = Bundle().apply {
                putLong(SITE_ENTRY_ID, siteEntryId)
            }
        )

    @ExperimentalTime
    fun startRestoreDatabaseScreen(context: Context) =
        startActivity(context, RestoreDatabaseScreen::class.java)

    @ExperimentalTime
    fun startCategoryScreen(context: Context) =
        startActivity(
            context,
            CategoryListScreen::class.java,
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )

    @ExperimentalTime
    fun startHelpScreen(context: Context) =
        startActivity(context, HelpScreen::class.java)

    fun startLoginScreen(context: Context, openCategoryScreenAfterLogin: Boolean = true) =
        startActivity(context, LoginScreen::class.java, extras = Bundle().apply {
            putBoolean(OPEN_CATEGORY_SCREEN_AFTER_LOGIN, openCategoryScreenAfterLogin)
        })

    // start
    fun startPreferencesActivity(context: Context) =
        startActivity(
            context,
            PreferenceActivity::class.java,
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )

    @ExperimentalTime
    @ExperimentalFoundationApi
    fun startSiteEntrySearchScreen(context: Context) =
        startActivity(context, SiteEntrySearchScreen::class.java)

    @ExperimentalFoundationApi
    fun startSiteEntryListScreen(context: Context, id: DBID) =
        startActivity(
            context, SiteEntryListScreen::class.java,
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            extras = Bundle().apply {
                putLong(SiteEntryListScreen.CATEGORY_ID, id)
            })

    fun removePluginIntegrations(pluginName: PluginName) {
        Logger.d(TAG, "Plugin ${pluginName.pluginName} is being disabled")
        // not perfect, category pager ..is uninstalled/disabled YES, but app
        // requires restart to get the original category screen established
        intentReplacements.remove(pluginName)
        menuAdditions.remove(pluginName)
    }

    @ExperimentalTime
    private inline fun <reified T> startActivity(
        context: Context,
        activityClass: Class<T>,
        flags: Int? = null,
        extras: Bundle? = null
    ) = context.startActivity(getActivityIntent(context, activityClass, flags, extras))

    fun startBackupDatabaseScreen(context: Context) =
        startActivity(context, BackupDatabaseScreen::class.java)
}
