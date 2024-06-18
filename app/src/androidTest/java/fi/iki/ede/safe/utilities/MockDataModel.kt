package fi.iki.ede.safe.utilities

import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
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