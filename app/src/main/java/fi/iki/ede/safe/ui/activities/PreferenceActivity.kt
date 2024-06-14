package fi.iki.ede.safe.ui.activities

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.ExportConfig
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.service.AutolockingService
import fi.iki.ede.safe.ui.TestTag
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
        private val backupDocumentSelected =
            startActivityForResults(TestTag.TEST_TAG_PREFERENCES_SAVE_LOCATION) { result ->
                if (result.resultCode == RESULT_OK) {
                    Preferences.setBackupDocument(result.data!!.data!!.path)
                }
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences)

            val backupPathClicker =
                findPreference<Preference>(Preferences.PREFERENCE_BACKUP_DOCUMENT)
            if (Preferences.SUPPORT_EXPORT_LOCATION_MEMORY) {
                backupPathClicker?.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { _: Preference? ->
                        backupDocumentSelected.launch(
                            ExportConfig.getCreateDocumentIntent(
                                // requireContext()
                            )
                        )
                        false
                    }
            } else {
                backupPathClicker?.isEnabled = false
            }

            findPreference<Preference>(Preferences.PREFERENCE_LOCK_TIMEOUT_MINUTES)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, _: Any ->
                    activity?.startService(Intent(requireContext(), AutolockingService::class.java))
                    true
                }

            findPreference<Preference>(Preferences.PREFERENCE_BIOMETRICS_ENABLED)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, enabledOrDisabled: Any ->
                    if (!(enabledOrDisabled as Boolean)) {
                        BiometricsActivity.clearBiometricKeys()
                    }
                    true
                }

            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

            backupPathClicker?.summary = Preferences.getBackupDocument()
            val lb = Preferences.getLastBackupTime()?.toLocalDateTime()?.toString()
                ?: resources.getString(R.string.preferences_summary_lastback_never_done)
            val lastBackupTime =
                findPreference<Preference>(Preferences.PREFERENCE_LAST_BACKUP_TIME)
            lastBackupTime?.summary = lb
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Preferences.PREFERENCE_BACKUP_DOCUMENT -> {
                    findPreference<Preference?>(Preferences.PREFERENCE_BACKUP_DOCUMENT)?.summary =
                        Preferences.getBackupDocument()
                }
            }
        }
    }
}