package fi.iki.ede.preferences

sealed interface PreferenceValue {
    data class StringVal(val value: String) : PreferenceValue
    data class IntVal(val value: Int) : PreferenceValue
    data class LongVal(val value: Long) : PreferenceValue
    data class FloatVal(val value: Float) : PreferenceValue
    data class BooleanVal(val value: Boolean) : PreferenceValue
    data class StringSetVal(val value: Set<String>) : PreferenceValue
}
