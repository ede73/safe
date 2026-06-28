package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

// Addressed PR12 comment: Restored EncryptExtensions.kt for encryption helper extensions
fun Password.encrypt(encrypter: (ByteArray) -> IVCipherText = KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider) =
    encrypter(this.utf8password.concatToString().encodeToByteArray())

fun String.encrypt(encrypter: (ByteArray) -> IVCipherText = KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider) =
    encrypter(this.trim().encodeToByteArray())

fun ByteArray.encrypt(encrypter: (ByteArray) -> IVCipherText = KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider) =
    encrypter(this)
