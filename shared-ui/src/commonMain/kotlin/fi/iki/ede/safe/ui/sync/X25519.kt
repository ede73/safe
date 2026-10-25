package fi.iki.ede.safe.ui.sync

import korlibs.crypto.SecureRandom

/**
 * Pure Kotlin RFC 7748 / TweetNaCl Curve25519 Montgomery Ladder implementation (X25519).
 * Provides constant-time field arithmetic modulo 2^255 - 19.
 * Zero external dependencies, 100% Kotlin Multiplatform compatible.
 */
object X25519 {

    private val BASE_POINT = ByteArray(32).apply { this[0] = 9 }

    fun generatePrivateKey(): ByteArray {
        val key = SecureRandom.nextBytes(32)
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
        return key
    }

    fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        return scalarmult(privateKey, BASE_POINT)
    }

    fun isAllZero(bytes: ByteArray): Boolean {
        var d = 0
        for (b in bytes) {
            d = d or b.toInt()
        }
        return d == 0
    }

    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += (1L shl 16)
            val c = o[i] shr 16
            if (i < 15) {
                o[i + 1] += c - 1
            } else {
                o[0] += 38 * (c - 1)
            }
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val mask = (-(b.toLong() and 1L)) and 0xFFFFFFFFL
        for (i in 0 until 16) {
            val t = mask and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack25519(n: LongArray): ByteArray {
        val t = LongArray(16) { n[it] }
        car25519(t)
        car25519(t)
        car25519(t)
        for (w in 0 until 2) {
            val m = LongArray(16)
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            m[14] = m[14] and 0xffff
            val b = (((m[15] shr 16) and 1) xor 1).toInt()
            sel25519(t, m, b)
        }
        val res = ByteArray(32)
        for (i in 0 until 16) {
            res[2 * i] = (t[i] and 0xff).toByte()
            res[2 * i + 1] = ((t[i] shr 8) and 0xff).toByte()
        }
        return res
    }

    private fun unpack25519(o: LongArray, d: ByteArray) {
        for (i in 0 until 16) {
            val b0 = d[2 * i].toLong() and 0xff
            val b1 = d[2 * i + 1].toLong() and 0xff
            o[i] = b0 + (b1 shl 8)
        }
        o[15] = o[15] and 0x7fff
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val product = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                product[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            product[i] += 38 * product[i + 16]
        }
        for (i in 0 until 16) o[i] = product[i]
        car25519(o)
        car25519(o)
    }

    private fun sqr(o: LongArray, a: LongArray) {
        mul(o, a, a)
    }

    private fun inv25519(o: LongArray, i: LongArray) {
        val c = LongArray(16) { i[it] }
        for (a in 253 downTo 0) {
            sqr(c, c)
            if (a != 2 && a != 4) {
                mul(c, c, i)
            }
        }
        for (k in 0 until 16) o[k] = c[k]
    }

    fun scalarmult(scalar: ByteArray, point: ByteArray): ByteArray {
        val clampedScalar = scalar.copyOf(32)
        clampedScalar[0] = (clampedScalar[0].toInt() and 248).toByte()
        clampedScalar[31] = (clampedScalar[31].toInt() and 127).toByte()
        clampedScalar[31] = (clampedScalar[31].toInt() or 64).toByte()

        val p = LongArray(16)
        unpack25519(p, point)

        val a = LongArray(16)
        val b = LongArray(16)
        val c = LongArray(16)
        val d = LongArray(16)
        val e = LongArray(16)
        val f = LongArray(16)
        val a24 = LongArray(16).apply { this[0] = 121665L }

        for (i in 0 until 16) {
            b[i] = p[i]
            d[i] = 0
            a[i] = 0
            c[i] = 0
        }
        a[0] = 1
        d[0] = 1

        for (i in 254 downTo 0) {
            val bit = ((clampedScalar[i shr 3].toInt() and 0xff) shr (i and 7)) and 1
            sel25519(a, b, bit)
            sel25519(c, d, bit)
            add(e, a, c)
            sub(a, a, c)
            add(c, b, d)
            sub(b, b, d)
            sqr(d, e)
            sqr(f, a)
            mul(a, c, a)
            mul(c, b, e)
            add(e, a, c)
            sub(a, a, c)
            sqr(b, a)
            sub(c, d, f)
            mul(a, c, a24)
            add(a, a, d)
            mul(c, c, a)
            mul(a, d, f)
            mul(d, b, p)
            sqr(b, e)
            sel25519(a, b, bit)
            sel25519(c, d, bit)
        }

        inv25519(c, c)
        mul(a, a, c)
        return pack25519(a)
    }
}
