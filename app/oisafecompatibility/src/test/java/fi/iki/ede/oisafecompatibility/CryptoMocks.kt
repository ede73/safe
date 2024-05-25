package fi.iki.ede.oisafecompatibility

import fi.iki.ede.crypto.SaltedPassword

object CryptoMocks {
    fun getCryptoMock(algo: Algorithm, saltedPassword: SaltedPassword): OISafeCryptoHelper {
        val ch = OISafeCryptoHelper(algo)
        ch.init(saltedPassword)
        return ch
    }
}