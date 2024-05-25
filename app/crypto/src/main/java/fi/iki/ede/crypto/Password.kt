package fi.iki.ede.crypto

// According to Java Cryptography Architecture Reference guide, due to garbage collection deficiencies, use char[] instead of String to store passwords
open class Password(open val password: ByteArray) : DisallowedFunctions() {
    val length: Int
        get() {
            return password.size
        }

    // In java at least it's impossible to 'delete' a string from heap reliably
    // And for char/byte array, end result is unpredicable even after GC
    // Since kotlin is just java, I assume the same, so
    // as soon as we don't need the password anymore, make sure it's can't be memory dumped
    fun finalize() {
        if (!isEmpty()) {
            for (i in password.indices) {
                password[i] = 0
            }
        }
    }

    open fun isEmpty(): Boolean = password.isEmpty()
    override fun equals(other: Any?): Boolean =
        (other != null) && (this.hashCode() == (other as? Password)?.hashCode())

    fun toCharArray(): CharArray {
        val c = CharArray(password.size)
        for (i in password.indices) {
            c[i] = password[i].toInt().toChar()
        }
        return c
    }

    override fun hashCode(): Int {
        return password.contentHashCode()
    }

    companion object {
        fun getEmpty() = Password(byteArrayOf())
    }
}