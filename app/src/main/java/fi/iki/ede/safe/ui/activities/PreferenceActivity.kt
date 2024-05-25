package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.Backup
import fi.iki.ede.safe.model.Preferences

class PreferenceActivity : AutoLockingAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        onUserInteraction()
        return super.onOptionsItemSelected(item)
    }

    class PreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
        private val backupDocumentSelected = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                Preferences.setBackupDocumentAndMethod(requireContext(), result.data!!.data!!.path)
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences)
            val l: ListPreference =
                findPreference<Preference>(Preferences.PREFERENCE_LOCK_TIMEOUT) as ListPreference
            val res = requireActivity().resources
            val maxNoLockMins = 5
            val entries = Array(maxNoLockMins) { "" }
            val entryValues = Array(maxNoLockMins) { 0 }
            for (i in 0 until maxNoLockMins) {
                entryValues[i] = i
                entries[i] = res.getQuantityString(R.plurals.preference_locktime, i + 1, i + 1)
            }
            l.entries = entries
            l.entryValues = entries
            val backupPathPref = findPreference<Preference>(Preferences.PREFERENCE_BACKUP_PATH)
            backupPathPref?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { _: Preference? ->
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(Backup.MIME_TYPE_BACKUP)
                        .putExtra(Intent.EXTRA_TITLE, Preferences.PASSWORDSAFE_EXPORT_FILE)
                    backupDocumentSelected.launch(intent)
                    false
                }
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            backupPathPref?.summary = Preferences.getBackupPath(requireActivity())
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Preferences.PREFERENCE_BACKUP_PATH ->
                    findPreference<Preference?>(Preferences.PREFERENCE_BACKUP_PATH)?.summary =
                        Preferences.getBackupPath(activity?.applicationContext!!)

                Preferences.PREFERENCE_BIOMETRICS_ENABLED -> {
                    if (!Biometrics.isBiometricEnabled(activity?.applicationContext!!)) {
                        Biometrics.clearBiometricKeys(requireContext())
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            val meIntent = Intent(
                context,
                PreferenceActivity::class.java
            ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(meIntent)
        }
    }
}