package fi.iki.ede.crypto


class Salt(val salt: ByteArray) : DisallowedFunctions() {
    fun isEmpty(): Boolean = salt.isEmpty()
    fun toHex() = salt.toHexString()

    override fun equals(other: Any?): Boolean =
        (other != null) && (this.hashCode() == (other as? Salt)?.hashCode())

    override fun hashCode(): Int {
        return salt.contentHashCode()
    }

    companion object {
        fun getEmpty() = Salt(byteArrayOf())
    }
}
