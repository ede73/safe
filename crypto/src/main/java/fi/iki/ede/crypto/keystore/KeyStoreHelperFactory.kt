package fi.iki.ede.crypto.keystore

object KeyStoreHelperFactory {
    lateinit var provideKeyStoreHelper: KeyStoreHelper
    var getKeyStoreHelper: () -> KeyStoreHelper = { provideKeyStoreHelper }
}
