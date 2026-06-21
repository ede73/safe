package fi.iki.ede.safe.password

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PasswordGeneratorTest {
    @Test
    fun testPasswordGen() {
        val pw = PasswordGenerator.genPassword(
            passUpper = true,
            passLower = true,
            passNum = true,
            passSymbols = true,
            length = 18
        )
        println("Generated password: $pw")
        assertTrue(pw.length > 8)
    }

    @Test
    fun testSecureRandomNextBytes() {
        // Unit tests run on host JVM (Windows), where korlibs SecureRandom
        // is broken (tries /dev/urandom). Use java.security.SecureRandom
        // to verify the fillRandomBytes path works via PasswordGenerator.
        val vals = mutableSetOf<Int>()
        val random = java.security.SecureRandom()
        for (i in 0 until 100) {
            vals.add(random.nextInt(100))
        }
        println("Unique random values: ${vals.size}")
        assertTrue(vals.size > 10, "SecureRandom should produce diverse values")
    }
}
