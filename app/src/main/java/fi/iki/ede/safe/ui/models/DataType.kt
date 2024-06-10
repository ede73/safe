package fi.iki.ede.safe.ui.models

sealed class DataType {
    object GPM : DataType()
    object DecryptableSiteEntry : DataType()
    // Add other data types as needed
}