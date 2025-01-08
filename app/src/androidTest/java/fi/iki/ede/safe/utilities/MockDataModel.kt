package fi.iki.ede.safe.utilities

import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.datamodel.DataModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

object MockDataModel4AndroidTest {
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