package fi.iki.ede.safe.utilities

import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

object MockDataModel4AndroidTest {
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

//    fun mockDataModel() {
//        verifyInitialization()
//        require(!DataModel.isMock) { "You MUST not have called mockkObject(DataModel)" }
//        //mockkObject(DataModel)
//        DataModel.attachDBHelper(DBHelperFactory.getDBHelper(mockk<Context>()))
//    }

    private fun verifyInitialization() {
        //require(MockDBHelper.isInitialized()) { "MockDBHelper MUST have been initialized before call here" }
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