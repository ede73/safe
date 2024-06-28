package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.safe.model.DecryptableSiteEntry

sealed class DataType {
    object GPM : DataType()
    object DecryptableSiteEntry : DataType()
    object WrappedDecryptableSiteEntry : DataType()
    // Add other data types as needed
}

data class WrappedDecryptableSiteEntry(val siteEntry: DecryptableSiteEntry) {
    val cachedDecryptedPassword: String by lazy { siteEntry.plainPassword } // ok
}