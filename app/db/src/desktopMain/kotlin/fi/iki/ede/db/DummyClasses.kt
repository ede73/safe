package fi.iki.ede.cryptoobjects

class DecryptableCategoryEntry {
    var id: Long? = null
    var encryptedName: fi.iki.ede.crypto.IVCipherText = fi.iki.ede.crypto.IVCipherText.getEmpty()
    val plainName: String = ""
    var containedSiteEntryCount: Int = 0
}

class DecryptableSiteEntry(val categoryId: Long) {
    var id: Long? = null
    var password = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var description = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var username = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var website = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var note = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var photo = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var deleted: Long = 0
    var extensions = fi.iki.ede.crypto.IVCipherText.getEmpty()
    var passwordChangedDate: Any? = null
}
