package fi.iki.ede.safe.ui.utils

import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.cryptoobjects.plainDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Flow<List<DecryptableSiteEntry>>.streamSortedSiteEntries(
    categoryId: Long?
): Flow<List<DecryptableSiteEntry>> = channelFlow {
    collect { allEntries ->
        val catEntries = if (categoryId == null) {
            allEntries
        } else {
            allEntries.filter { it.categoryId == categoryId }
        }

        if (catEntries.isEmpty()) {
            send(emptyList())
            return@collect
        }

        if (catEntries.size <= 25) {
            val sorted = withContext(Dispatchers.Default) {
                catEntries.sortedBy { it.plainDescription.lowercase() }
            }
            send(sorted)
            return@collect
        }

        val currentList = mutableListOf<DecryptableSiteEntry>()
        val comparator = Comparator<DecryptableSiteEntry> { a, b ->
            a.plainDescription.lowercase().compareTo(b.plainDescription.lowercase())
        }

        withContext(Dispatchers.Default) {
            var lastEmitTime = 0L
            val chunkSize = 20

            for (i in catEntries.indices) {
                val item = catEntries[i]

                val idx = currentList.binarySearch(item, comparator)
                val insertionPoint = if (idx < 0) -idx - 1 else idx
                currentList.add(insertionPoint, item)

                val now = Clock.System.now().toEpochMilliseconds()
                if (i == 5 || (now - lastEmitTime > 40 && (i + 1) % chunkSize == 0) || i == catEntries.lastIndex) {
                    lastEmitTime = now
                    send(currentList.toList())
                }
            }
        }
    }
}.flowOn(Dispatchers.Default)
