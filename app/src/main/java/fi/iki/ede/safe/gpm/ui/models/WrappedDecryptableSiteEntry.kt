package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import fi.iki.ede.safe.model.DecryptableSiteEntry

data class WrappedDecryptableSiteEntry(val siteEntry: DecryptableSiteEntry) {
    val cachedDecryptedPassword: String by lazy { siteEntry.plainPassword } // ok

    // to speed up the massive search matrix
    val cachedHarmonizedDecryptedDescription: LowerCaseTrimmedString by lazy {
        harmonizePotentialDomainName(siteEntry.cachedPlainDescription).toLowerCasedTrimmedString()
    }
}