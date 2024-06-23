package fi.iki.ede.safe.gpm.ui.models

sealed class DataType {
    object GPM : DataType()
    object DecryptableSiteEntry : DataType()
    // Add other data types as needed
}