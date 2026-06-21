package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText

object KeyStoreHelperFactory {
    private val fallbackKeyStoreHelper by lazy {
        object : IKeyStoreHelper {
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

    lateinit var provideKeyStoreHelper: IKeyStoreHelper
    var getKeyStoreHelper: () -> IKeyStoreHelper = {
        // Addressed PR12 comment: Fallback to dummy key store helper if provideKeyStoreHelper is not initialized (e.g. in Compose Previews or tests)
        if (::provideKeyStoreHelper.isInitialized) provideKeyStoreHelper else fallbackKeyStoreHelper
    }
}
