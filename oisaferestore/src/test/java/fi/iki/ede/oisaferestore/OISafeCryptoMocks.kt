package fi.iki.ede.oisaferestore

import fi.iki.ede.crypto.SaltedPassword

object OISafeCryptoMocks {
    fun getCryptoMock(algo: Algorithm, saltedPassword: SaltedPassword): OISafeCryptoHelper {
        val ch = OISafeCryptoHelper(algo)
        ch.init(saltedPassword)
        return ch
    }
}