package fi.iki.ede.db.cxf

object CxfSyntheticModelMapper {

    fun isSyntheticId(id: Long?): Boolean =
        id != null && id < 0L

    fun toSyntheticCategory(cxfAccount: CXFAccount): DecryptableCXFCategoryEntry {
        return DecryptableCXFCategoryEntry(cxfAccount)
    }

    fun toSyntheticSiteEntry(cxfImport: CXFImport, cxfAccountId: Long): DecryptableCXFSiteEntry {
        return DecryptableCXFSiteEntry.makeFromImport(cxfAccountId, cxfImport)
    }

    fun toSyntheticSiteEntry(cxfPasskey: CXFPasskey, cxfAccountId: Long): DecryptableCXFSiteEntry {
        return DecryptableCXFSiteEntry.makeFromPasskey(cxfAccountId, cxfPasskey)
    }
}
