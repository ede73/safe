package fi.iki.ede.safe

import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.CryptoMocks.mockKeyStoreHelper
import fi.iki.ede.safe.DataModelMocks.mockDataModel
import fi.iki.ede.safe.model.DataModel
import junit.framework.TestCase
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DataModelDBTest {
    @Test
    fun datamodel() {
        mockKeyStoreHelper()
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()
        mockDataModel(
            linkedMapOf(
                Pair(
                    DataModelMocks.makeCat(1, ks),
                    listOf(
                        DataModelMocks.makePwd(1, 1, ks),
                        DataModelMocks.makePwd(1, 2, ks)
                    )
                )
            )
        )
        runBlocking {
            DataModel.dump()
        }
        
        TestCase.assertEquals(1, DataModel.getCategories().size)
        runBlocking {
            // ADD a password..this goes to FLOW
            DataModel.addOrUpdatePassword(DataModelMocks.makePwd(1, null, ks))
        }
        // Ah interesting, runBlocking isn't actually blocking that all since INSIDE the function
        // there's .launch(io thread)
        // we should wait for the emit
        DataModel.passwordsSharedFlow.take(1)
        TestCase.assertEquals(3, DataModel.getPasswords().size)
    }
}