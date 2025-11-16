package fi.iki.ede.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class EncodingTests {

    @Test
    fun ensurePasswordRemainsDespiteDifferentInputsTest() {

        KeystoreHelperMock4UnitTests.mock()
        // crypto APIs prefer byte[] where ever except when dealing with passwords
        // Their sensitiveness requires char[]
        // Now, this exposes immediate problem, password is a string, is a (usually) UTF-8
        // but anything else is a byte[] is a byte[] (like photo)
        // Verify that potentially hard to encode strings (will indeed be StandardCharsets.UTF_8)
        // And encoding/decoding a PASSWORD will still result in original string

        val password = "TässäpäSalasana-كلمة المرور"
        val p1 = Password(password.toCharArray())
        val p2 = Password(password.toByteArray())
        val p3 = Password(password)
        assertArrayEquals(p1.utf8password, p2.utf8password)
        assertArrayEquals(p1.utf8password, p3.utf8password)
    }
}