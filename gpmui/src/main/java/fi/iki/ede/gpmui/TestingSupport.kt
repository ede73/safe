package fi.iki.ede.gpmui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

import fi.iki.ede.crypto.keystore.IKeyStoreHelper
import fi.iki.ede.crypto.keystore.KMPKey

fun Modifier.testTag(tag: TestTag) = semantics(
    properties = {
        // Make sure we don't leak stuff to production
        if (BuildConfig.DEBUG) {
            testTag = tag.name
        }
    }
)

fun SemanticsNodeInteractionsProvider.onAllNodesWithTag(
    testTag: TestTag,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteractionCollection = onAllNodes(hasTestTag(testTag.name), useUnmergedTree)

fun SemanticsNodeInteractionsProvider.onNodeWithTag(
    testTag: TestTag,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = onNode(hasTestTag(testTag.name), useUnmergedTree)

internal object GpmUiMockKeyStoreHelper {
    fun init() {
        try {
            KeyStoreHelperFactory.provideKeyStoreHelper
        } catch (e: UninitializedPropertyAccessException) {
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
        }

        KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.getKeyStoreHelper().decrypterProvider = { it.cipherText }

        try {
            val classLoader = Thread.currentThread().contextClassLoader ?: GpmUiMockKeyStoreHelper::class.java.classLoader
            val prefClass = Class.forName("fi.iki.ede.preferences.Preferences", true, classLoader)
            val instance = prefClass.getField("INSTANCE").get(null)
            
            // 1. Mock sharedPreferences
            val sharedPrefsClass = Class.forName("android.content.SharedPreferences", true, classLoader)
            val mockPrefs = java.lang.reflect.Proxy.newProxyInstance(
                sharedPrefsClass.classLoader,
                arrayOf(sharedPrefsClass),
                object : java.lang.reflect.InvocationHandler {
                    override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any>?): Any? {
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

            // 2. Mock dataStore
            try {
                val dataStoreClass = Class.forName("androidx.datastore.core.DataStore", true, classLoader)
                val mockDataStore = java.lang.reflect.Proxy.newProxyInstance(
                    dataStoreClass.classLoader,
                    arrayOf(dataStoreClass),
                    object : java.lang.reflect.InvocationHandler {
                        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any>?): Any? {
                            if (method.name == "getData") {
                                val flowClass = Class.forName("kotlinx.coroutines.flow.Flow", true, classLoader)
                                return java.lang.reflect.Proxy.newProxyInstance(
                                    flowClass.classLoader,
                                    arrayOf(flowClass),
                                    object : java.lang.reflect.InvocationHandler {
                                        override fun invoke(p: Any, m: java.lang.reflect.Method, a: Array<out Any>?): Any? {
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

        } catch (ignored: Throwable) {}
    }
}