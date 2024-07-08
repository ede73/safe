package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.safe.model.DecryptableSiteEntry

data class WrappedDecryptableSiteEntry(val siteEntry: DecryptableSiteEntry) {
    val cachedDecryptedPassword: String by lazy { siteEntry.plainPassword } // ok
}