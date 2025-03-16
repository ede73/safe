package fi.iki.ede.gpm.changeset

import fi.iki.ede.gpm.debug
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.logger.Logger
import java.security.MessageDigest

private const val TAG = "ImportChangeSet"

// Record the 3 way (?4 way?) mutation of incoming data and existing data
data class ImportChangeSet(
    private val allIncomingGPMs: Set<IncomingGPM>,
    private val allSavedGPMs: Set<SavedGPM>,
    // overlap (passwords whose hash match perfectly or there's 1 field change)
    val matchingGPMs: MutableSet<Pair<IncomingGPM, ScoredMatch>> = mutableSetOf()
) {
    // TODO: theoretically this keeps conflicts at bay, ONCE we have a scored match, it is not right though, basically we should try to match everything and at the end choose BEST candidates
    val getUnprocessedIncomingGPMs: Set<IncomingGPM>
        get() = allIncomingGPMs.subtract(matchingGPMs.map { it.first }.toSet())

    // TODO: theoretically this keeps conflicts at bay, ONCE we have a scored match, it is not right though, basically we should try to match everything and at the end choose BEST candidates
    val getUnprocessedSavedGPMs: Set<SavedGPM>
        get() = allSavedGPMs.subtract(matchingGPMs.map { it.second.item }.toSet())

    // TODO: theoretically this keeps conflicts at bay, ONCE we have a scored match, it is not right though, basically we should try to match everything and at the end choose BEST candidates
    val nonMatchingSavedGPMsToDelete: Set<SavedGPM>
        get() = allSavedGPMs.subtract(matchingGPMs.map { it.second.item }.toSet())

    // TODO: theoretically this keeps conflicts at bay, ONCE we have a scored match, it is not right though, basically we should try to match everything and at the end choose BEST candidates
    val newAddedOrUnmatchedIncomingGPMs: Set<IncomingGPM>
        get() = allIncomingGPMs.subtract(matchingGPMs.map { it.first }.toSet())

    val matchingGPMsAsMap
        get() = matchingGPMs
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, matches) -> matches.toSet() }

    val getNonConflictingGPMs
        get() = matchingGPMs.groupBy({ it.first }, { it.second }).filter { it.value.size == 1 }
            .mapValues { (_, list) -> list.first() }

    // Since we add in various processing phases matches to the
    // matchingPasswords, it is possible at one point a IncomingGPM to match multiple SavedGPMs
    // of course this cannot happen, so we need to resolve conflicts
    // (or make the import undetermined w/ regards to conflicts and NOT delete/update conflicting lines)
    val getMatchingConflicts: Map<IncomingGPM, Set<ScoredMatch>>
        get() {
            val preprocess = mutableMapOf<IncomingGPM, MutableSet<ScoredMatch>>()
            matchingGPMs.forEach { (incoming, saved) ->
                // TODO:
                preprocess.getOrPut(incoming) { mutableSetOf() }.add(saved)
            }
            return preprocess.filter { it.value.size > 1 }.map { it.key to it.value.toSet() }
                .toMap()
        }
}

fun printImportReport(
    importChangeSet: ImportChangeSet
) {
    Logger.d(TAG, "=======================")
    Logger.d(TAG, "====== IMPORT REPORT ==")
    Logger.d(TAG, "=======================")

    Logger.d(TAG, "New unseen entries(or no match found):")
//    importChangeSet.getUnprocessedIncomingGPMs.forEach {
//        Logger.d(TAG,"\t$it")
//    }

    Logger.d(
        TAG,
        "Known entries with only hash or 1-field-changed against identifiable existing DB entry:"
    )
//    importChangeSet.matchingGPMs.forEach {
//        Logger.d(TAG,"\t${it.first} matches ${it.second}")
//    }

    Logger.d(TAG, "Conflicts: input line matches MORE than 1 entry:")
//    importChangeSet.getMatchingConflicts.forEach { it ->
//        Logger.d(TAG,"\t${it.key} matches:")
//        it.value.forEach { scoredSavedGPM ->
//            Logger.d(TAG,"\t\t${scoredSavedGPM.matchScore * 100}% $scoredSavedGPM.item")
//        }
//    }
}

fun calculateSha128(fields: List<String>, s: String): String {
    debug {
        //print("Calculate hash (for $s) of (${fields.joinToString(",")}) =")
    }
    val hash =
        MessageDigest.getInstance("SHA-1").digest(fields.joinToString(separator = "").toByteArray())
            .joinToString("") { "%02x".format(it) }
    debug {
        // Logger.d(TAG,hash)
    }
    return hash
}
