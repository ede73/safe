package fi.iki.ede.safe.utilities

import android.content.Context
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

object MockDataModel {
    /**
     * Use from:
     *   companion object {
     *         @BeforeClass
     *         @JvmStatic
     *         fun initialize()
     *         MockDataModel.mockAllDataModelNecessities
     *
     * To setup all components, keystore, DBHelper, DataModel to be ready for testing
     */
    fun mockAllDataModelNecessities() {
        MockKeyStore.mockKeyStore()
        MockDBHelper.mockDBHelper(
            initializeMasterKey = fakeEncryptedMasterKey,
            initializeSalt = fakeSalt,
            initialPasswords = emptyList(),
            initialCategories = emptyList()
        )

        mockDataModel()
    }

    fun mockDataModel() {
        verifyInitialization()
        require(!DataModel.isMock) { "You MUST not have called mockkObject(DataModel)" }
        //mockkObject(DataModel)
        DataModel.attachDBHelper(DBHelperFactory.getDBHelper(mockk<Context>()))
    }

    private fun verifyInitialization() {
        require(MockDBHelper.isInitialized()) { "MockDBHelper MUST have been initialized before call here" }
        require(MockKeyStore.isInitialized()) { "MockKeyStore MUST have been initialized before call here" }
    }

    fun beginCollectCategoriesFlow(collector: (MutableList<List<DecryptableCategoryEntry>>, Job) -> Unit) {
        runTest {
            val categoriesEmitted = mutableListOf<List<DecryptableCategoryEntry>>()
            val collectionJob = launch {
                DataModel.categoriesStateFlow.collectLatest { categories ->
                    categoriesEmitted.add(categories)
                }
            }

            collector(categoriesEmitted, collectionJob)

            collectionJob.cancel()
        }
    }
}