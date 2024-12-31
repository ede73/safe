package fi.iki.ede.safe

import fi.iki.ede.crypto.KeystoreHelperMock4UnitTests
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.DataModelMocks.mockDataModelFor_UNIT_TESTS_ONLY
import fi.iki.ede.safe.model.DataModel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class DataModelDBTest {

    @Before
    fun before() {
        mockkObject(fi.iki.ede.preferences.Preferences)
        every { fi.iki.ede.preferences.Preferences.setLastModified() } returns Unit
        every { fi.iki.ede.preferences.Preferences.storeAllExtensions(any()) } returns Unit
    }

    @After
    fun after() {
        unmockkObject(fi.iki.ede.preferences.Preferences)
    }

    @Test
    fun dataModel() {
        KeystoreHelperMock4UnitTests.mock()
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()
        mockDataModelFor_UNIT_TESTS_ONLY(
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

        TestCase.assertEquals(1, DataModel.categoriesStateFlow.value.size)
        runBlocking {
            // ADD a password..this goes to FLOW
            DataModel.addOrUpdateSiteEntry(DataModelMocks.makePwd(1, null, ks))
        }
        // Ah interesting, runBlocking isn't actually blocking that all since INSIDE the function
        // there's .launch(io thread)
        // we should wait for the emit
        DataModel.siteEntriesStateFlow.take(1)
        TestCase.assertEquals(3, DataModel.siteEntriesStateFlow.value.size)
    }
}