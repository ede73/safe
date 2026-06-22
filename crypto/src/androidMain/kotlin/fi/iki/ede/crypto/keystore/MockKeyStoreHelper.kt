package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.BuildConfig

object MockKeyStoreHelper {
    fun init() {
        if (!BuildConfig.DEBUG) {
            throw Exception("MockKeyStoreHelper init is not allowed in release build")
        }
        KeyStoreHelperFactory.provideKeyStoreHelper = object : IKeyStoreHelper {
            override fun testingDeleteKeys_DO_NOT_USE() {}
            override fun rotateKeys() {}
            override fun getOrCreateBiokey(): KMPKey = object : KMPKey {
                override fun getAlgorithm(): String = "RAW"
                override fun getFormat(): String = "RAW"
                override fun getEncoded(): ByteArray = byteArrayOf()
            }
            override var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray = { iv, _ -> iv.cipherText }
            override var decrypterProvider: (IVCipherText) -> ByteArray = { it.cipherText }
            override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { bytes, _ -> IVCipherText(bytes, bytes) }
            override var encrypterProvider: (ByteArray) -> IVCipherText = { IVCipherText(it, it) }
        }
    }
}
