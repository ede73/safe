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
        if (ivAndCipherText.size >= ivLength) {
            ivAndCipherText.copyOfRange(0, ivLength)
        } else {
            // Addressed PR12 comment: return empty bytearray if input is too short to extract IV
            byteArrayOf()
        },
        if (ivAndCipherText.size >= ivLength) {
            ivAndCipherText.copyOfRange(ivLength, ivAndCipherText.size)
        } else {
            byteArrayOf()
        }
    )

    @Deprecated("Try not to use")
    fun combineIVAndCipherText() = iv + cipherText

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this::class != other?.let { it::class }) return false

        other as IVCipherText

        if (!iv.contentEquals(other.iv)) return false
        if (!cipherText.contentEquals(other.cipherText)) return false

        return true
    }

    override fun hashCode() = 31 * iv.contentHashCode() + cipherText.contentHashCode()

    override fun toString(): String =
        "IVCipherText(iv=${iv.toHexString()},cipherText=${cipherText.toHexString()})"

    companion object {
        fun getEmpty(): IVCipherText =
            IVCipherText(byteArrayOf(), byteArrayOf())
    }
}
