package fi.iki.ede.preferences

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList

class DataStoreSharedPreferences(
    private val dataStore: DataStore<DataStorePreferences>
) : SharedPreferences {

    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    fun notifyListeners(key: String) {
        for (listener in listeners) {
            listener.onSharedPreferenceChanged(this, key)
        }
    }

    override fun getAll(): Map<String, *> {
        return runBlocking {
            dataStore.data.first().asMap().mapKeys { it.key.name }
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        val dsKey = stringPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[dsKey] }.first()
        } ?: defValue
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val dsKey = stringSetPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[dsKey] }.first()
        } ?: defValues ?: emptySet()
    }

    override fun getInt(key: String, defValue: Int): Int {
        val dsKey = intPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[dsKey] }.first()
        } ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val dsKey = longPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[dsKey] }.first()
        } ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val dsKey = floatPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[dsKey] }.first()
        } ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val dsKey = booleanPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[dsKey] }.first()
        } ?: defValue
    }

    override fun contains(key: String): Boolean {
        return runBlocking {
            val map = dataStore.data.first()
            map.contains(stringPreferencesKey(key)) ||
                    map.contains(booleanPreferencesKey(key)) ||
                    map.contains(intPreferencesKey(key)) ||
                    map.contains(longPreferencesKey(key)) ||
                    map.contains(floatPreferencesKey(key)) ||
                    map.contains(stringSetPreferencesKey(key))
        }
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    inner class Editor : SharedPreferences.Editor {
        private val tempMap = mutableMapOf<String, PreferenceValue?>()
        private val toRemove = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) {
                toRemove.add(key)
            } else {
                tempMap[key] = PreferenceValue.StringVal(value)
                toRemove.remove(key)
            }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values == null) {
                toRemove.add(key)
            } else {
                tempMap[key] = PreferenceValue.StringSetVal(values)
                toRemove.remove(key)
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            tempMap[key] = PreferenceValue.IntVal(value)
            toRemove.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            tempMap[key] = PreferenceValue.LongVal(value)
            toRemove.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            tempMap[key] = PreferenceValue.FloatVal(value)
            toRemove.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            tempMap[key] = PreferenceValue.BooleanVal(value)
            toRemove.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            toRemove.add(key)
            tempMap.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            tempMap.clear()
            toRemove.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val keysChanged = mutableListOf<String>()
            runBlocking {
                dataStore.edit { preferences ->
                    if (clearAll) {
                        preferences.clear()
                    }
                    for (key in toRemove) {
                        preferences.remove(stringPreferencesKey(key))
                        preferences.remove(booleanPreferencesKey(key))
                        preferences.remove(intPreferencesKey(key))
                        preferences.remove(longPreferencesKey(key))
                        preferences.remove(floatPreferencesKey(key))
                        preferences.remove(stringSetPreferencesKey(key))
                        keysChanged.add(key)
                    }
                    for ((key, wrapped) in tempMap) {
                        if (wrapped == null) {
                            preferences.remove(stringPreferencesKey(key))
                            preferences.remove(booleanPreferencesKey(key))
                            preferences.remove(intPreferencesKey(key))
                            preferences.remove(longPreferencesKey(key))
                            preferences.remove(floatPreferencesKey(key))
                            preferences.remove(stringSetPreferencesKey(key))
                        } else {
                            when (wrapped) {
                                is PreferenceValue.StringVal -> preferences[stringPreferencesKey(key)] = wrapped.value
                                is PreferenceValue.BooleanVal -> preferences[booleanPreferencesKey(key)] = wrapped.value
                                is PreferenceValue.IntVal -> preferences[intPreferencesKey(key)] = wrapped.value
                                is PreferenceValue.LongVal -> preferences[longPreferencesKey(key)] = wrapped.value
                                is PreferenceValue.FloatVal -> preferences[floatPreferencesKey(key)] = wrapped.value
                                is PreferenceValue.StringSetVal -> preferences[stringSetPreferencesKey(key)] = wrapped.value
                            }
                        }
                        keysChanged.add(key)
                    }
                }
            }
            for (key in keysChanged) {
                notifyListeners(key)
            }
        }
    }
}
