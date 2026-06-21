package fi.iki.ede.preferences

import androidx.preference.PreferenceDataStore

class DataStorePreferenceBridge(
    private val sharedPrefs: DataStoreSharedPreferences
) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        sharedPrefs.edit().putStringSet(key, values).apply()
    }

    override fun putInt(key: String, value: Int) {
        sharedPrefs.edit().putInt(key, value).apply()
    }

    override fun putLong(key: String, value: Long) {
        sharedPrefs.edit().putLong(key, value).apply()
    }

    override fun putFloat(key: String, value: Float) {
        sharedPrefs.edit().putFloat(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? {
        return sharedPrefs.getString(key, defValue)
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return sharedPrefs.getStringSet(key, defValues) ?: defValues ?: emptySet()
    }

    override fun getInt(key: String, defValue: Int): Int {
        return sharedPrefs.getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return sharedPrefs.getLong(key, defValue)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return sharedPrefs.getFloat(key, defValue)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return sharedPrefs.getBoolean(key, defValue)
    }
}
