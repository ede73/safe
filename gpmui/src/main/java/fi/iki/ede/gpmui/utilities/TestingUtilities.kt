package fi.iki.ede.gpmui.utilities

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.cryptoobjects.encrypter
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM

fun makeSavedForTesting(id: Long, name: String): SavedGPM {
    return SavedGPM.makeFromEncryptedStringFields(
        id,
        encrypter(name.toByteArray()),
        IVCipherText.getEmpty(),
        IVCipherText.getEmpty(),
        IVCipherText.getEmpty(),
        IVCipherText.getEmpty(),
        false,
        ""
    )
}

fun makeIncomingForTesting(name: String): IncomingGPM {
    return IncomingGPM.makeFromCSVImport(name, "", "", "", "")
}

fun makeFakeImportForTesting(): ImportChangeSet {
    val incoming = setOf(
        makeIncomingForTesting("Incoming1"),
        makeIncomingForTesting("Incoming2"),
        makeIncomingForTesting("Incoming3"),
        makeIncomingForTesting("Incoming4"),
        makeIncomingForTesting("Incoming5"),
        makeIncomingForTesting("Incoming6"),
        makeIncomingForTesting("Incoming7"),
        makeIncomingForTesting("Incoming8"),
        makeIncomingForTesting("Incoming9"),
        makeIncomingForTesting("Incoming10"),
        makeIncomingForTesting("Incoming11"),
        makeIncomingForTesting("Incoming12"),
        makeIncomingForTesting("Incoming13"),
        makeIncomingForTesting("Incoming14"),
        makeIncomingForTesting("Incoming15"),
    )
    val saved = setOf(
        makeSavedForTesting(1, "Saved1"),
        makeSavedForTesting(2, "Saved2"),
        makeSavedForTesting(2, "Saved3"),
        makeSavedForTesting(2, "Saved4"),
        makeSavedForTesting(2, "Saved5"),
        makeSavedForTesting(2, "Saved6"),
        makeSavedForTesting(2, "Saved7"),
        makeSavedForTesting(2, "Saved8"),
        makeSavedForTesting(2, "Saved9"),
        makeSavedForTesting(2, "Saved10"),
        makeSavedForTesting(2, "Saved11"),
        makeSavedForTesting(2, "Saved12"),
    )
    val incomingAndConflict = makeIncomingForTesting("Incoming3")
    val a = incomingAndConflict to ScoredMatch(
        0.5,
        makeSavedForTesting(3, "Saved3"), true
    )
    val b = makeIncomingForTesting("Incoming4") to ScoredMatch(
        0.7,
        makeSavedForTesting(4, "Saved4"),
        false
    )
    val c = incomingAndConflict to ScoredMatch(
        0.7,
        makeSavedForTesting(4, "Saved5"), false
    )
    val matches = mutableSetOf(a, b, c)
    // iterate ALL matchingGPMs ie.  overlap (passwords whose hash match perfectly or there's 1 field change)
    // and add them to map Map<IncomingGPM, Set<ScoredMatch>>
    //any incomingGPM with >1 ScoredMatch is a conflict
    return ImportChangeSet(incoming, saved, matches)
}