package fi.iki.ede.db.cxf

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
