package fi.iki.ede.crypto.keystore

object KeyStoreHelperFactory {
    lateinit var provideKeyStoreHelper: IKeyStoreHelper
    var getKeyStoreHelper: () -> IKeyStoreHelper = { provideKeyStoreHelper }
}
