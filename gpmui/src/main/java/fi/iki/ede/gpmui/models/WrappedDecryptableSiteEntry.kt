package fi.iki.ede.gpmui.models

import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString

data class WrappedDecryptableSiteEntry(val siteEntry: DecryptableSiteEntry) {
    val cachedDecryptedPassword: String by lazy { siteEntry.plainPassword } // ok

    // to speed up the massive search matrix
    val cachedHarmonizedDecryptedDescription: LowerCaseTrimmedString by lazy {
        harmonizePotentialDomainName(siteEntry.cachedPlainDescription).toLowerCasedTrimmedString()
    }
}