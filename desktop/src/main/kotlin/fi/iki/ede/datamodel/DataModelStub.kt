package fi.iki.ede.datamodel

import fi.iki.ede.cryptoobjects.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object DataModel {
    val categoriesStateFlow: Flow<List<DecryptableCategoryEntry>> = flowOf(emptyList())
}
