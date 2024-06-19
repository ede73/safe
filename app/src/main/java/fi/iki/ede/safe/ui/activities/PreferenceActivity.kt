package fi.iki.ede.safe.ui.activities

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.activityViewModels
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.ExportConfig
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.model.Preferences.PREFERENCE_AUTOBACKUP_QUOTA_EXCEEDED
import fi.iki.ede.safe.model.Preferences.PREFERENCE_AUTOBACKUP_RESTORE_FINISHED
import fi.iki.ede.safe.model.Preferences.PREFERENCE_AUTOBACKUP_RESTORE_STARTED
import fi.iki.ede.safe.model.Preferences.PREFERENCE_AUTOBACKUP_STARTED
import fi.iki.ede.safe.model.Preferences.getAutoBackupQuotaExceeded
import fi.iki.ede.safe.model.Preferences.getAutoBackupRestoreFinished
import fi.iki.ede.safe.model.Preferences.getAutoBackupRestoreStarts
import fi.iki.ede.safe.model.Preferences.getAutoBackupStarts
import fi.iki.ede.safe.service.AutolockingService
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.ui.models.PluginLoaderViewModel
import fi.iki.ede.safe.ui.utilities.AutolockingBaseAppCompatActivity
import fi.iki.ede.safe.ui.utilities.startActivityForResults


class PreferenceActivity : AutolockingBaseAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
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
            startActivityForResults { result ->
                if (result.resultCode == RESULT_OK) {
                    Preferences.setBackupDocument(result.data!!.data!!.path)
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

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences)

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
                activity?.startService(
                    Intent(
                        requireContext(),
                        AutolockingService::class.java
                    )
                )
            }

            addChangeListener<Preference, Boolean>(Preferences.PREFERENCE_BIOMETRICS_ENABLED) { enabledOrDisabled ->
                if (enabledOrDisabled) {
                    BiometricsActivity.clearBiometricKeys()
                }
            }

            addPreferenceClickListener<Preference>(Preferences.PREFERENCE_MAKE_CRASH) {
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
                throw RuntimeException("Crash Test from preferences")
            }

            findPreference<Preference>(Preferences.PREFERENCE_LAST_BACKUP_TIME).let { it ->
                val lb = Preferences.getLastBackupTime()?.toLocalDateTime()?.toString()
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
                    sharedPreferences?.getStringSet(
                        Preferences.PREFERENCE_EXPERIMENTAL_FEATURES,
                        null
                    )?.forEach { dfm ->
                        val pluginName = PluginName.entries.first { it.pluginName == dfm }
                        pluginLoaderViewModel.getOrInstallPlugin(pluginName)
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