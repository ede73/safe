package fi.iki.ede.crypto

/**
 * Convenience class representing encrypted cipher text with exact knowledge of the IV used
 * Typically in a backup for instance IV is prefixed to cipher and one assumes knowledge of its size
 */
data class IVCipherText(val iv: ByteArray, val cipherText: ByteArray) {
    fun isEmpty() = iv.isEmpty() && cipherText.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()

    @Deprecated("Try not to use, depends on actual generated keys (IV might be variable in the future)")
    constructor(ivLength: Int, ivAndCipherText: ByteArray) : this(
        if (ivAndCipherText.isNotEmpty()) {
            ivAndCipherText.copyOfRange(0, ivLength)
        } else {
            byteArrayOf()
        },
        if (ivAndCipherText.isNotEmpty()) {
            ivAndCipherText.copyOfRange(ivLength, ivAndCipherText.size)
        } else {
            byteArrayOf()
        }
    )

    @Deprecated("Try not to use")
    fun combineIVAndCipherText() = iv + cipherText

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IVCipherText

        if (!iv.contentEquals(other.iv)) return false
        if (!cipherText.contentEquals(other.cipherText)) return false

        return true
    }

    override fun hashCode() = 31 * iv.contentHashCode() + cipherText.contentHashCode()

    companion object {
        fun getEmpty(): IVCipherText =
            IVCipherText(byteArrayOf(), byteArrayOf())
    }
}
