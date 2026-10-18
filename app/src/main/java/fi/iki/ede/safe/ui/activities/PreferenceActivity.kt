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
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import fi.iki.ede.autolock.AutoLockingBaseAppCompatActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.autolock.AutolockingService
import fi.iki.ede.backup.ExportConfig
import fi.iki.ede.backup.getCreateDocumentIntent
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.dateutils.toLocalDateTime
import fi.iki.ede.logger.firebaseCollectCrashlytics
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.preferences.DataStorePreferenceBridge
import fi.iki.ede.preferences.DataStoreSharedPreferences
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
import fi.iki.ede.safe.ui.sync.DeviceInfoProvider
import fi.iki.ede.safe.ui.sync.DevicePlatformInfo
import fi.iki.ede.safe.ui.utilities.startActivityForResults
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import fi.iki.ede.preferences.R as prefR


@ExperimentalTime
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
            firebaseLog("pref onOptions: finish()")
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
        private var deviceNamePref: EditTextPreference? = null
        private var pairedCategory: androidx.preference.PreferenceCategory? = null

        private fun updateDeviceNameSummary() {
            deviceNamePref?.let { pref ->
                val custom = Preferences.getSyncDeviceCustomName().trim()
                pref.text = custom
                pref.summary = if (custom.isNotBlank()) {
                    "Custom name: $custom\nTap to change"
                } else {
                    "Default: ${DeviceInfoProvider.getFormattedDeviceName()}\nTap to set custom name"
                }
            }
        }

        private fun updatePairedDevicesCategory() {
            val category = pairedCategory ?: return
            category.removeAll()
            val pairedDevices = Preferences.getPairedSyncDeviceList()
            category.title = "Paired Devices (${pairedDevices.size})"

            if (pairedDevices.isEmpty()) {
                category.addPreference(Preference(requireContext()).apply {
                    title = "No linked devices"
                    summary = "Devices on your local Wi-Fi will appear here after synchronizing"
                    isEnabled = false
                    isIconSpaceReserved = false
                })
            } else {
                pairedDevices.forEach { dev ->
                    category.addPreference(Preference(requireContext()).apply {
                        title = dev.name
                        summary = if (dev.lastSyncTimestamp > 0L) {
                            "Last synced ${DateUtils.formatTimeAgo(dev.lastSyncTimestamp)} • Linked for auto-sync"
                        } else {
                            "Linked for automatic peer synchronization"
                        }
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                    })
                }
            }
        }

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

        @Suppress("DEPRECATION")
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStorePreferenceBridge(Preferences.sharedPreferences as DataStoreSharedPreferences)
            addPreferencesFromResource(prefR.xml.preferences)

            DevicePlatformInfo.appVersion = BuildConfig.VERSION_NAME

            val versionPreference = Preference(requireContext()).apply {
                key = "version_preference"
                title = "App Version"
                summary =
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) (${BuildConfig.GIT_COMMIT_HASH})"
                isIconSpaceReserved = false
            }

            preferenceScreen?.addPreference(versionPreference)

            // --- Synchronization Preferences ---
            val syncCategory = androidx.preference.PreferenceCategory(requireContext()).apply {
                title = "Device Synchronization"
                isIconSpaceReserved = false
            }
            preferenceScreen?.addPreference(syncCategory)

            val autoSyncWifi = androidx.preference.SwitchPreferenceCompat(requireContext()).apply {
                key = Preferences.PREFERENCE_AUTO_SYNC_WIFI_ENABLED
                title = "Auto-Sync on Local Wi-Fi"
                summary = "Continuously sync updates with linked devices when on the same Wi-Fi network"
                isChecked = Preferences.isAutoSyncWifiEnabled()
                isIconSpaceReserved = false
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    Preferences.setAutoSyncWifiEnabled(enabled)
                    true
                }
            }
            syncCategory.addPreference(autoSyncWifi)

            val devNamePref = EditTextPreference(requireContext()).apply {
                key = Preferences.PREFERENCE_SYNC_DEVICE_CUSTOM_NAME
                title = "Device Name"
                dialogTitle = "Device Name"
                dialogMessage = "Enter a custom name for this device (e.g. Work Laptop, Tablet):"
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceChangeListener { _, newValue ->
                    val name = (newValue as String).trim()
                    Preferences.setSyncDeviceCustomName(name)
                    updateDeviceNameSummary()
                    true
                }
            }
            deviceNamePref = devNamePref
            updateDeviceNameSummary()
            syncCategory.addPreference(devNamePref)

            val pairedCat = androidx.preference.PreferenceCategory(requireContext()).apply {
                isIconSpaceReserved = false
            }
            pairedCategory = pairedCat
            preferenceScreen?.addPreference(pairedCat)
            updatePairedDevicesCategory()

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
                if (!enabledOrDisabled) {
                    BiometricsActivity.clearBiometricKeys()
                }
            }

            addPreferenceClickListener<Preference>(Preferences.PREFERENCE_MAKE_CRASH) {
                firebaseCollectCrashlytics(true)
                throw RuntimeException("Crash Test from preferences")
            }

            findPreference<Preference>(Preferences.PREFERENCE_LAST_BACKUP_TIME).let {
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
                        ExtensionsEditor(
                            extensions = allUsedExtensionsAndOnesInPreferences,
                            onDismiss = { showDialog.value = false }
                        ) {
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
            Preferences.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            updateDeviceNameSummary()
            updatePairedDevicesCategory()
        }

        override fun onPause() {
            super.onPause()
            Preferences.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Preferences.PREFERENCE_SYNC_DEVICE_CUSTOM_NAME -> {
                    updateDeviceNameSummary()
                }

                Preferences.PREFERENCE_PAIRED_SYNC_DEVICES -> {
                    updatePairedDevicesCategory()
                }

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