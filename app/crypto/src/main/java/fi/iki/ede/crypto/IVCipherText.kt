package fi.iki.ede.crypto

data class IVCipherText(val iv: ByteArray, val cipherText: ByteArray) {
    @Deprecated("Try not to use, depends on actual generated keys (IV might be variable in the future)")
    constructor(ivSize: Int, ivAndCipherText: ByteArray) : this(
        ivAndCipherText.copyOfRange(0, ivSize),
        ivAndCipherText.copyOfRange(ivSize, ivAndCipherText.size)
    )

    @Deprecated("Try not to use, depends on actual generated keys (IV might be variable in the future)")
    constructor(ivAndCipherText: ByteArray, ivLength: Int) : this(
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
    fun combineIVAndCipherText(): ByteArray {
        return iv + cipherText
    }

//    fun toHex() = combineIVAndCipherText().toHexString()

    fun isEmpty() = iv.isEmpty() && cipherText.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IVCipherText

        if (!iv.contentEquals(other.iv)) return false
        if (!cipherText.contentEquals(other.cipherText)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + cipherText.contentHashCode()
        return result
    }

    companion object {
        fun getEmpty(): IVCipherText =
            IVCipherText(byteArrayOf(), byteArrayOf())
    }
}