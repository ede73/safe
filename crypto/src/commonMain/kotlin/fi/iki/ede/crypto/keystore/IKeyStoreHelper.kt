package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText

interface IKeyStoreHelper {
    fun testingDeleteKeys_DO_NOT_USE()
    fun rotateKeys()
    fun getOrCreateBiokey(): KMPKey

    var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray
    var decrypterProvider: (IVCipherText) -> ByteArray
    var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText
    var encrypterProvider: (ByteArray) -> IVCipherText
}
