package fi.iki.ede.gpm

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.encrypt
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.changeset.resolveMatchConflicts
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConflictResolution {
    private val importChangeSet = ImportChangeSet(emptySet(), emptySet())

    @AfterEach
    fun afterTests() {
    }

    @BeforeEach
    fun beforeTests() {
        importChangeSet.matchingGPMs.clear()
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    }

    @Test
    fun sameConflictResolution() {
        importChangeSet.matchingGPMs.addAll(
            makeScoredConflictMap("A", 0.95 to "x", 0.95 to "y", 0.95 to "z")
        )

        resolveMatchConflicts(importChangeSet) { }
        val resolved = importChangeSet.matchingGPMsAsMap
        assertTrue(resolved.keys.size == 1)
        assertTrue(resolved.getScoredMatches("A").size == 3)
    }

    @Test
    fun easyConflictResolution() {
        importChangeSet.matchingGPMs.addAll(
            makeScoredConflictMap("A", 0.95 to "x", 0.91 to "y", 0.9 to "z") +
                    makeScoredConflictMap("B", 0.9 to "z", 0.9 to "y") +
                    makeScoredConflictMap("C", 0.91 to "x", 0.92 to "z"),
        )

//        {IncomingGPM (name=A))=[ ScoredMatch(matchScore=0.91, item=SavedGPM ( id=null, name=y, )), ScoredMatch(matchScore=0.9, item=SavedGPM ( id=null, name=z, ))],
//        IncomingGPM (name=B, url=, username=, password=, node=, hash=))=[ScoredMatch(matchScore=0.9, item=SavedGPM ( id=null, name=z, url=, username=, password=, note=, flaggedIgnored=false, hash=)), ScoredMatch(matchScore=0.9, item=SavedGPM ( id=null, name=y, url=, username=, password=, note=, flaggedIgnored=false, hash=))]
//        , IncomingGPM (name=C, url=, usern))=[ScoredMatch(matchScore=0.91, item=SavedGPM ( id=null, name=x, url=, username=, password=, note=, flaggedIgnored=false, hash=))]}

        resolveMatchConflicts(importChangeSet) {}
        val resolved = importChangeSet.matchingGPMsAsMap
        print(resolved)
        assertTrue(resolved.keys.size == 3) { "Expected 3, got ${resolved.keys.size}" }
        assertTrue(resolved.getScoredMatches("A").size == 1)
        assertTrue(resolved.getScoredMatches("B").size == 2)
        assertTrue(resolved.getScoredMatches("C").size == 1)
    }

    @Test
    fun hardConflictResolution() {
        importChangeSet.matchingGPMs.addAll(
            makeScoredConflictMap("A", 0.95 to "x", 0.95 to "y", 0.95 to "z") +
                    makeScoredConflictMap("B", 0.95 to "z", 0.95 to "y") +
                    makeScoredConflictMap("C", 0.95 to "x", 0.95 to "z"),
        )

        resolveMatchConflicts(importChangeSet) {}
        val resolved = importChangeSet.matchingGPMsAsMap
        assertTrue(resolved.keys.size == 3)
        assertTrue(resolved.getScoredMatches("A").size == 3)
        assertTrue(resolved.getScoredMatches("B").size == 2)
        assertTrue(resolved.getScoredMatches("C").size == 2)
    }

    private fun Map<IncomingGPM, Set<ScoredMatch>>.getScoredMatches(name: String) =
        filter { it.key.name == name }.values.flatten().toSet()

    private fun incoming(name: String) = IncomingGPM.makeFromCSVImport(name, "", "", "", "")

    private fun makeScoredConflictMap(
        incomingName: String,
        vararg xs: Pair<Double, String>
    ): Set<Pair<IncomingGPM, ScoredMatch>> = xs.map { it ->
        incoming(incomingName) to scored(it.first, it.second)
    }.toSet()

    private fun scored(scoredMatch: Double, name: String) =
        ScoredMatch(
            scoredMatch,
            SavedGPM.makeFromEncryptedStringFields(
                null,
                name.encrypt(),
                "".encrypt(),
                "".encrypt(),
                "".encrypt(),
                "".encrypt(),
                false,
                ""
            )
        )

    // TODO: Test also
//    val add = importChangeSet.newAddedOrUnmatchedIncomingGPMs
//    val update = importChangeSet.getNonConflictingGPMs
//    val delete = importChangeSet.nonMatchingSavedGPMsToDelete

}