package fi.iki.ede.crypto

// TODO: Replace ByteArray with IVCipherText!
class EncryptedPassword(val encryptedPassword: ByteArray) : DisallowedFunctions() {
    fun toHex() = encryptedPassword.toHexString()
    fun isEmpty(): Boolean = encryptedPassword.isEmpty()
    override fun equals(other: Any?): Boolean =
        (other != null) && (this.hashCode() == (other as? EncryptedPassword)?.hashCode())

    override fun hashCode(): Int {
        return encryptedPassword.contentHashCode()
    }

    companion object {
        fun getEmpty() = EncryptedPassword(byteArrayOf())
    }
}