package fi.iki.ede.crypto.keystore

import java.lang.reflect.Proxy
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import fi.iki.ede.crypto.IVCipherText

object MockKeyStoreHelper {
    fun init() {
        KeyStoreHelperFactory.provideKeyStoreHelper = object : IKeyStoreHelper {
            override fun testingDeleteKeys_DO_NOT_USE() {}
            override fun rotateKeys() {}
            override fun getOrCreateBiokey(): KMPKey = object : KMPKey {
                override fun getAlgorithm(): String = "RAW"
                override fun getFormat(): String = "RAW"
                override fun getEncoded(): ByteArray = byteArrayOf()
            }
            override var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray = { iv, _ -> iv.cipherText }
            override var decrypterProvider: (IVCipherText) -> ByteArray = { it.cipherText }
            override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { bytes, _ -> IVCipherText(bytes, bytes) }
            override var encrypterProvider: (ByteArray) -> IVCipherText = { IVCipherText(it, it) }
        }

        try {
            val classLoader = Thread.currentThread().contextClassLoader ?: MockKeyStoreHelper::class.java.classLoader
            val prefClass = Class.forName("fi.iki.ede.preferences.Preferences", true, classLoader)
            val instance = prefClass.getField("INSTANCE").get(null)
            
            // 1. Mock sharedPreferences
            val sharedPrefsClass = Class.forName("android.content.SharedPreferences", true, classLoader)
            // Addressed PR12 comment: Imported java.lang.reflect classes and removed FQCNs
            val mockPrefs = Proxy.newProxyInstance(
                sharedPrefsClass.classLoader,
                arrayOf(sharedPrefsClass),
                object : InvocationHandler {
                    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
                        if (method.name.startsWith("get") && args != null && args.isNotEmpty()) {
                            return args.last()
                        }
                        if (method.name == "contains") return false
                        return null
                    }
                }
            )

            var setSuccess = false
            try {
                val field = prefClass.getDeclaredField("sharedPreferences")
                field.isAccessible = true
                field.set(instance, mockPrefs)
                setSuccess = true
            } catch (ignored: Throwable) {}

            if (!setSuccess) {
                try {
                    val setMethod = prefClass.getMethod("setSharedPreferences", sharedPrefsClass)
                    setMethod.invoke(instance, mockPrefs)
                    setSuccess = true
                } catch (ignored: Throwable) {}
            }

            if (!setSuccess) {
                throw IllegalStateException("Could not set sharedPreferences field or setter on Preferences object")
            }

            // 2. Mock dataStore
            try {
                val dataStoreClass = Class.forName("androidx.datastore.core.DataStore", true, classLoader)
                // Addressed PR12 comment: Cleaned up FQCNs for java.lang.reflect classes
                val mockDataStore = Proxy.newProxyInstance(
                    dataStoreClass.classLoader,
                    arrayOf(dataStoreClass),
                    object : InvocationHandler {
                        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
                            if (method.name == "getData") {
                                val flowClass = Class.forName("kotlinx.coroutines.flow.Flow", true, classLoader)
                                return Proxy.newProxyInstance(
                                    flowClass.classLoader,
                                    arrayOf(flowClass),
                                    object : InvocationHandler {
                                        override fun invoke(p: Any, m: Method, a: Array<out Any>?): Any? {
                                            if (m.name == "collect") {
                                                return null
                                            }
                                            return null
                                        }
                                    }
                                )
                            }
                            return null
                        }
                    }
                )

                var dataStoreSetSuccess = false
                try {
                    val field = prefClass.getDeclaredField("dataStore")
                    field.isAccessible = true
                    field.set(instance, mockDataStore)
                    dataStoreSetSuccess = true
                } catch (ignored: Throwable) {}

                if (!dataStoreSetSuccess) {
                    try {
                        val setMethod = prefClass.getMethod("setDataStore", dataStoreClass)
                        setMethod.invoke(instance, mockDataStore)
                    } catch (ignored: Throwable) {}
                }
            } catch (ignored: Throwable) {}

        } catch (e: Throwable) {
            // Print to error stream and rethrow to make sure layoutlib reports it clearly
            System.err.println("MockKeyStoreHelper failed to initialize mock Preferences: " + e.stackTraceToString())
            throw RuntimeException("MockKeyStoreHelper failed to initialize mock Preferences", e)
        }
    }
}