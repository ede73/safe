package fi.iki.ede.safe.ui.activities

import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import fi.iki.ede.autolock.AutoLockingBaseAppCompatActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.autolock.AutolockingService
import fi.iki.ede.backup.ExportConfig
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.dateutils.toLocalDateTime
import fi.iki.ede.logger.firebaseCollectCrashlytics
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.preferences.Preferences.PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED
import fi.iki.ede.preferences.Preferences.PREFERENCE_AUTOBACKUP_RESTORE_FINISHED
import fi.iki.ede.preferences.Preferences.PREFERENCE_AUTOBACKUP_RESTORE_STARTED
import fi.iki.ede.preferences.Preferences.PREFERENCE_AUTOBACKUP_STARTED
import fi.iki.ede.preferences.Preferences.getAutoBackupQuotaExceeded
import fi.iki.ede.preferences.Preferences.getAutoBackupRestoreFinished
import fi.iki.ede.preferences.Preferences.getAutoBackupRestoreStarts
import fi.iki.ede.preferences.Preferences.getAutoBackupStarts
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.R
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.ExtensionsEditor
import fi.iki.ede.safe.ui.models.PluginLoaderViewModel
import fi.iki.ede.safe.ui.utilities.startActivityForResults
import kotlinx.coroutines.launch
import fi.iki.ede.preferences.R as prefR


class PreferenceActivity :
    AutoLockingBaseAppCompatActivity(AutolockingFeaturesImpl) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        PreferenceManager.setDefaultValues(this, prefR.xml.preferences, false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        onUserInteraction()
        return super.onOptionsItemSelected(item)
    }

    class PreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
        private val pluginLoaderViewModel: PluginLoaderViewModel by activityViewModels()
        private val backupDocumentSelected =
            startActivityForResults(TestTag.PREFERENCES_SAVE_LOCATION) { result ->
                if (result.resultCode == RESULT_OK) {
                    Preferences.setBackupDocument(result.data!!.data!!.path)
                }
            }
        private var serviceConnection: ServiceConnection? = null

        private inline fun <reified T : Preference, reified C : Any> addChangeListener(
            preferenceKey: String,
            noinline changed: (change: C) -> Unit
        ) {
            findPreference<T>(preferenceKey)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue is C) {
                        changed(newValue)
                    }
                    true
                }
        }

        private fun <T : Preference> addPreferenceClickListener(
            preferenceKey: String,
            clicked: () -> Boolean
        ) {
            findPreference<T>(preferenceKey)?.onPreferenceClickListener =
                addClickListener(clicked)
        }

        private fun addClickListener(clicked: () -> Boolean) =
            Preference.OnPreferenceClickListener { _ ->
                clicked()
            }

        override fun onDestroy() {
            super.onDestroy()
            if (serviceConnection != null) {
                activity?.unbindService(serviceConnection!!)
                serviceConnection = null
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(prefR.xml.preferences)

            val versionPreference = Preference(requireContext()).apply {
                key = "version_preference"
                title = "App Version"
                summary =
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) (${BuildConfig.GIT_COMMIT_HASH})"
                isIconSpaceReserved = false
            }

            preferenceScreen?.addPreference(versionPreference)

            findPreference<MultiSelectListPreference>(Preferences.PREFERENCE_EXPERIMENTAL_FEATURES).let { experimentalFeatures ->
                experimentalFeatures?.apply {
                    PluginName.entries.map { it.pluginName }.let { dfms ->
                        entries = dfms.toTypedArray()
                        entryValues = dfms.toTypedArray()
                    }
                }
            }

            findPreference<Preference>(Preferences.PREFERENCE_BACKUP_DOCUMENT).let { backupPathClicker ->
                backupPathClicker?.summary = Preferences.getBackupDocument()
                if (Preferences.SUPPORT_EXPORT_LOCATION_MEMORY) {
                    backupPathClicker?.onPreferenceClickListener = addClickListener {
                        backupDocumentSelected.launch(ExportConfig.getCreateDocumentIntent())
                        false
                    }
                } else backupPathClicker?.isEnabled = false
            }

            addChangeListener<Preference, Any>(Preferences.PREFERENCE_LOCK_TIMEOUT_MINUTES) {
                serviceConnection = AutolockingService.startAutolockingService(
                    requireActivity(),
                    AutolockingFeaturesImpl,
                    requireContext()
                )
            }

            addChangeListener<Preference, Boolean>(Preferences.PREFERENCE_BIOMETRICS_ENABLED) { enabledOrDisabled ->
                if (enabledOrDisabled) {
                    BiometricsActivity.clearBiometricKeys()
                }
            }

            addPreferenceClickListener<Preference>(Preferences.PREFERENCE_MAKE_CRASH) {
                firebaseCollectCrashlytics(true)
                throw RuntimeException("Crash Test from preferences")
            }

            findPreference<Preference>(Preferences.PREFERENCE_LAST_BACKUP_TIME).let { it ->
                val lb = Preferences.getLastBackupTime()?.toLocalDateTime()
                    ?.toString()
                    ?: resources.getString(R.string.preferences_summary_lastback_never_done)
                it?.summary = lb
            }

            listOf(
                PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED to ::getAutoBackupQuotaExceeded,
                PREFERENCE_AUTOBACKUP_STARTED to ::getAutoBackupStarts,
                PREFERENCE_AUTOBACKUP_RESTORE_STARTED to ::getAutoBackupRestoreStarts,
                PREFERENCE_AUTOBACKUP_RESTORE_FINISHED to ::getAutoBackupRestoreFinished
            ).forEach { (prefKey, getTime) ->
                findPreference<Preference>(prefKey)?.summary =
                    getTime()?.toLocalDateTime()?.toString()
                        ?: "N/A"
            }
        }

        val showDialog = mutableStateOf(false)

        fun combineLists(list1: List<String>, list2: List<String?>): List<Pair<String?, String?>> {
            val maxSize = maxOf(list1.size, list2.size)
            val extendedList1 = list1 + List(maxSize - list1.size) { null }
            val extendedList2 = list2 + List(maxSize - list2.size) { null }

            return extendedList1.zip(extendedList2)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    val coroutineScope = rememberCoroutineScope()
                    if (showDialog.value) {
                        // Collect all extensions from prod, merge with prefs
                        val allUsedExtensionsAndOnesInPreferences =
                            (DataModel.siteEntriesStateFlow.collectAsState().value.map {
                                it.plainExtensions.keys
                            }.flatten()
                                .toSet() + Preferences.getAllExtensions()).toList()
                        ExtensionsEditor(allUsedExtensionsAndOnesInPreferences) {
                            // we should have two lists, original and modifications
                            // empty items represent DELETIONS
                            // new items represent ADDITIONS
                            // any changes should be RENAMES

                            val newList = mutableListOf<String>()
                            val editedEntries = mutableSetOf<DecryptableSiteEntry>()
                            combineLists(
                                allUsedExtensionsAndOnesInPreferences,
                                it
                            ).forEach { (old, new) ->
                                if (old != null && new != null && old != new) {
                                    // renamed
                                    newList.add(new)
                                    // scan all site entries for RENAMES
                                    val list = DataModel.siteEntriesStateFlow.value
                                    list.forEach { siteEntry ->
                                        val map = siteEntry.plainExtensions.toMutableMap()
                                        if (map.containsKey(old)) {
                                            val values = map[old] ?: emptySet()
                                            map[new] = values
                                            map.remove(old)
                                            siteEntry.extensions =
                                                siteEntry.encryptExtension(map.toMap())
                                            editedEntries.add(siteEntry)
                                        }
                                    }
                                } else if (new == null) {
                                    // delete
                                    // scan all site entries and DELETE the items
                                    val list = DataModel.siteEntriesStateFlow.value
                                    list.forEach { siteEntry ->
                                        val map = siteEntry.plainExtensions.toMutableMap()
                                        if (map.containsKey(old)) {
                                            map.remove(old)
                                            siteEntry.extensions =
                                                siteEntry.encryptExtension(map.toMap())
                                            editedEntries.add(siteEntry)
                                        }
                                    }
                                } else if (old == null) {
                                    // add
                                    newList.add(new)
                                }
                            }
                            Preferences.storeAllExtensions(newList.toSet())
                            coroutineScope.launch {
                                editedEntries.forEach { siteEntry ->
                                    DataModel.addOrUpdateSiteEntry(siteEntry) {}
                                }
                            }
                            showDialog.value = false
                        }
                    }
                }
            }

            (view as? ViewGroup)?.addView(composeView)

            addPreferenceClickListener<Preference>(Preferences.PREFERENCE_EXTENSIONS_KEY) {
                showDialog.value = true
                true
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Preferences.PREFERENCE_EXPERIMENTAL_FEATURES -> {
                    val allPlugins = PluginName.entries.toSet()
                    val enabledPlugins = sharedPreferences?.getStringSet(
                        Preferences.PREFERENCE_EXPERIMENTAL_FEATURES,
                        null
                    )?.map { dfmName ->
                        PluginName.entries.first { it.pluginName == dfmName }
                    }?.toSet() ?: emptySet()

                    allPlugins.intersect(enabledPlugins).forEach { plugin ->
                        try {
                            firebaseLog("Begin loading plugin $plugin")
                            pluginLoaderViewModel.getOrInstallPlugin(plugin)
                        } catch (ex: Exception) {
                            firebaseRecordException(ex)
                        }
                    }
                    allPlugins.subtract(enabledPlugins).forEach { plugin ->
                        try {
                            firebaseLog("Begin Uninstalling plugin $plugin")
                            pluginLoaderViewModel.uninstallPlugin(plugin)
                        } catch (ex: Exception) {
                            firebaseRecordException(ex)
                        }
                    }
                }

                Preferences.PREFERENCE_BACKUP_DOCUMENT -> {
                    findPreference<Preference?>(Preferences.PREFERENCE_BACKUP_DOCUMENT)?.summary =
                        Preferences.getBackupDocument()
                }
            }
        }
    }
}