package fi.iki.ede.crypto

class SaltedEncryptedPassword(val salt: Salt, val encryptedPassword: EncryptedPassword) :
    DisallowedFunctions() {
    fun isEmpty() = salt.isEmpty() || encryptedPassword.isEmpty()

    companion object {
        fun getEmpty(): SaltedEncryptedPassword =
            SaltedEncryptedPassword(Salt.getEmpty(), EncryptedPassword.getEmpty())
    }
}