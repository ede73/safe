package fi.iki.ede.datamodel.cxf

import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.cxf.CXFAccount
import fi.iki.ede.db.cxf.CXFImport
import fi.iki.ede.db.cxf.CXFPasskey

object CxfSyntheticModelMapper {

    fun isSyntheticId(id: Long?): Boolean =
        id != null && id < 0L

    fun toSyntheticCategory(cxfAccount: CXFAccount): DecryptableGPMCategoryEntry {
        return DecryptableGPMCategoryEntry(cxfAccount)
    }

    fun toSyntheticSiteEntry(cxfImport: CXFImport, cxfAccountId: Long): DecryptableGPMSiteEntry {
        return DecryptableGPMSiteEntry.makeFromImport(cxfAccountId, cxfImport)
    }

    fun toSyntheticSiteEntry(cxfPasskey: CXFPasskey, cxfAccountId: Long): DecryptableGPMSiteEntry {
        return DecryptableGPMSiteEntry.makeFromPasskey(cxfAccountId, cxfPasskey)
    }
}
