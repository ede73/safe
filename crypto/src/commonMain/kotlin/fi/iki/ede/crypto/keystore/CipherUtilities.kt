package fi.iki.ede.crypto.keystore

import korlibs.crypto.SecureRandom

abstract class CipherUtilities {

    companion object {
        const val KEY_LENGTH_BITS = 256
        const val IV_LENGTH = KEY_LENGTH_BITS / 8 / 2
        const val KEY_ITERATION_COUNT = 20000

        @JvmInline
        value class Bits(val value: Int) {
            init {
                require(value % 8 == 0) { "Bits must be divisible by 8, was ${value % 8}" }
            }

            val toBytes get() = value / 8
        }

        @JvmInline
        value class Bytes(val value: Int) {
            init {
                require(value > 0) { "Bytes must be positive, was $value" }
            }
        }

        val Int.bits get() = Bits(this)
        val Int.bytes get() = Bytes(this)

        // Overloaded function: only Bits or Bytes allowed
        fun generateRandomBytes(bits: Bits): ByteArray = _generateRandomBytesInternal(bits.toBytes)
        fun generateRandomBytes(bytes: Bytes): ByteArray = _generateRandomBytesInternal(bytes.value)

        // Internal helper: fills array cross-platform
        fun _generateRandomBytesInternal(byteSize: Int): ByteArray {
            val key = ByteArray(byteSize)
            fillRandomBytes(key)
            return key
        }
    }
}
