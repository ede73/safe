package fi.iki.ede.gpm.changeset

import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.ScoringConfig
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.findSimilarity
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import java.net.MalformedURLException
import java.net.URL
import kotlin.math.max
import kotlin.reflect.KProperty1

fun fetchMatchingHashes(importChangeSet: ImportChangeSet) =
    importChangeSet.getUnprocessedIncomingGPMs.flatMap { incomingGPM ->
        importChangeSet.getUnprocessedSavedGPMs
            .filter { existingGPM -> existingGPM.hash == incomingGPM.hash }
            .map { savedGPM -> incomingGPM to ScoredMatch(1.0, savedGPM, true) }
    }.toSet()

// harmonize the names by removing commonly used WWW patterns
fun harmonizePotentialDomainName(input: String): String {
    val top75PercentileTLDPrefixes = listOf(
        "www", "mail", "home", "shop", "blog", "web", "cloud", "info", "store", "my",
        "the", "go", "super", "app", "online", "news", "tech", "site", "wiki", "forum", "app"
    ).joinToString(separator = "|")
    val top86PercentileTLDSuffixes = setOf(
        "com", "net", "org", "info", "xyz", "online", "shop", "top", "pl", "us",
        "site", "store", "biz", "vip", "cfd", "sbs", "app", "club", "pro", "live",
        "ru", "uk", "de", "br", "in", "it", "fr", "au", "jp", "cn", "nl", "eu", "es", "co", "ir"
    ).joinToString(separator = "|")
    val patterns =
        listOf("""^($top75PercentileTLDPrefixes)\.""", """\.(${top86PercentileTLDSuffixes})$""")
    var result = input
    patterns.forEach { pattern ->
        result =
            result.replace(Regex(pattern, RegexOption.IGNORE_CASE), "").replace("^https?://", "")
    }
    return result
}

// WARNING: May create conflicts! mapping 1 to many
fun findSimilarNamesWhereUsernameMatchesAndURLDomainLooksTheSame(
    importChangeSet: ImportChangeSet,
    scoringConfig: ScoringConfig,
    progressReport: (progress: String) -> Unit,
): Set<Pair<IncomingGPM, ScoredMatch>> =
    importChangeSet.getUnprocessedIncomingGPMs.flatMap { incomingGPM ->
        progressReport("Similarity match..")
        importChangeSet.getUnprocessedSavedGPMs.mapNotNull { savedGPM ->
            val score = findSimilarity(
                harmonizePotentialDomainName(incomingGPM.name).toLowerCasedTrimmedString(),
                harmonizePotentialDomainName(savedGPM.cachedDecryptedName).toLowerCasedTrimmedString()
            )
            if (score > scoringConfig.recordNameSimilarityThreshold) ScoredMatch(
                score,
                savedGPM
            ) else null
        }.toSet().mapNotNull { scoredSavedGPMNameMatch ->
            // Also enforce username or domain name match
            val domainAndOrUsernameMatch =
                doesUserNameOrDomainNameMatch(
                    incomingGPM,
                    scoredSavedGPMNameMatch,
                    scoringConfig
                )
            if (domainAndOrUsernameMatch != null) {
                incomingGPM to domainAndOrUsernameMatch
            } else {
                null
            }
        }
    }.toSet()

// return pair of
// 1) GPMEntry lists denoting potentially new unseen rows
// 2) List of pairs where
//    a) first field is GPMEntry property,
//    b) second is a pair of GPMEntry and DBGPMEntry rows
// where only one field has changed, the GPMEntry property denotes the changed field
fun processOneFieldChanges(
    importChangeSet: ImportChangeSet,
    scoringConfig: ScoringConfig,
    progressReport: (progress: String) -> Unit,
) = listOf(
    Pair(IncomingGPM::name, SavedGPM::cachedDecryptedName/*encryptedName*/),
    Pair(IncomingGPM::password, SavedGPM::cachedDecryptedPassword/*encryptedPassword*/),
    Pair(IncomingGPM::username, SavedGPM::cachedDecryptedUsername/*encryptedUsername*/),
    Pair(IncomingGPM::url, SavedGPM::cachedDecryptedUrl/*encryptedUrl*/),
    Pair(IncomingGPM::note, SavedGPM::cachedDecryptedNote/*encryptedNote*/),
).map {
    // for debuggability, this would be nice, but for clarity not
    // we don't care WHICH field it really was...
    //    Pair(
    //        it.first,
    findEntriesWithOnlyChangedPropertyIs(
        importChangeSet,
        it.first,
        it.second,
        scoringConfig,
        progressReport
    )
//    )
}.let {
    // now that we know the tiny changes, filter those out too from new entries
    //Pair(incomingNewOrChangedPasswords.removeMatches(it), it)
    importChangeSet.matchingGPMs.addAll(it.flatten())
}

/*
// say incoming A maps to x(0.95),y(0.91),z(0.9)
// B maps to z(0.9),y(0.9)
// C maps to x(0.91),z(0.92)
// it might be possible to untangle as
// A->x (0.95!)
// C->z (0.92)
// B->y (0.91)

A   x(0.95),y(0.91),z(0.9)
B   z(0.9),y(0.9)
C   x(0.91),z(0.92)

how about reverse

x(0.95) A
z(0.92) C
x(0.91) C
y(0.91) A
y(0.9)  B
z(0.9)  A
z(0.9)  B

1) remove best candidate x(0.95) A - ie. remove ALL A,X
z(0.92) C
y(0.9)  B
z(0.9)  B

2) remove best candidate z(0.92) C ,ie. remove ALL z,C
y(0.9)  B

map successful
----------------
x(0.95) A
x(0.95) B
x(0.92) C
z(0.91) C
y(0.91) A
y(0.9)  B
z(0.9)  A

1) remove best candidate? uups, we dont know it really, either A or B!?
    - take A(x)
z(0.91) C
y(0.9)  B

done

    - take B(x)
z(0.91) C
y(0.91) A
z(0.9)  A
completion

but different results
---
how about?
x(0.95) A
x(0.93) B
x(0.92) C
z(0.91) C
y(0.91) A
x(0.9)  B
z(0.9)  A

- remove A/x
z(0.91) C

whoops we lost B
though that is fine, we want THE BEST candidate ALWAYS
 */

// each match of IncomingGPM to potentially multiple SavedGPM has a match scroe
// say incoming A maps to x(0.95),y(0.91),z(0.9)
// B maps to z(0.9),y(0.9)
// C maps to x(0.91),z(0.92)
// it might be possible to untangle as
// A->x (0.95!)
// C->z (0.92)
// B->y (0.91)
// that leaves B with z and y equal, but since z was mapped with higher score to C, then gotta be y
//fun findBestCandidates(conflicts: Map<IncomingGPM, Set<ScoredMatch>>): Map<IncomingGPM, ScoredMatch> {
fun resolveMatchConflicts(
    importChangeSet: ImportChangeSet,
    progressReport: (progress: String) -> Unit
) {
    progressReport("Get matching conflicts")
    val bestEffortConflictResolution =
        importChangeSet.getMatchingConflicts.mapValues { (_, matches) ->
            val highestScore = matches.maxByOrNull { it.matchScore }?.matchScore
            matches.filter { it.matchScore == highestScore }.toSet()
        }
    progressReport("Remove all IncomingGPMs from the matching GPMs")
    importChangeSet.matchingGPMs.removeAll { (incomingGPM, scoredMatch) ->
        incomingGPM in bestEffortConflictResolution
    }

    progressReport("BestEffortConflictResolution")
    bestEffortConflictResolution.forEach { (t, u) ->
        u.forEach {
            importChangeSet.matchingGPMs.add(t to it)
        }
    }
}

private fun doesUserNameOrDomainNameMatch(
    incomingGPM: IncomingGPM,
    scoredSavedGPM: ScoredMatch,
    scoringConfig: ScoringConfig
): ScoredMatch? {
    val incomingUsername = incomingGPM.username.toLowerCasedTrimmedString()
    val savedUsername = scoredSavedGPM.item.cachedDecryptedUsername.toLowerCasedTrimmedString()
    val userNameMatches = incomingUsername == savedUsername

    // of course there's a chance that username was changed, but assuming recent enuf import, 1-field-change should caught that
    if (!userNameMatches) {
        return null
    }
    val incomingUrl = parseUrl(incomingGPM.url.toLowerCasedTrimmedString())
    val savedUrl = parseUrl(scoredSavedGPM.item.cachedDecryptedUrl.toLowerCasedTrimmedString())
    val bothHaveDomains = (incomingUrl != null && savedUrl != null)

    val domainMatchSimilarityScore = if (bothHaveDomains) findSimilarity(
        incomingUrl!!.host.toLowerCasedTrimmedString(),
        savedUrl!!.host.toLowerCasedTrimmedString(),
    ) else 0.0

    if (domainMatchSimilarityScore > scoringConfig.urlDomainMatchThresholdIfUsernameMatches) {
        // username AND the domain part parsed from URL matches
        // including call site name (similarity) match, let's increase odds by 10%
        // so username matches as well as URL domain part, including fact that both have URL set
        return ScoredMatch(
            max(
                1.0,
                scoredSavedGPM.matchScore + scoringConfig.scoreIncrementIfUsernameAndUrlDomainMatch
            ),
            scoredSavedGPM.item
        )
    }
    return scoredSavedGPM
}

private fun findEntriesWithOnlyChangedPropertyIs(
    importChangeSet: ImportChangeSet,
    firstProperty: KProperty1<IncomingGPM, String>,
    secondProperty: KProperty1<SavedGPM, String>,//IVCipherText>,
    scoringConfig: ScoringConfig,
    progressReport: (progress: String) -> Unit
): Set<Pair<IncomingGPM, ScoredMatch>> {
    var start = 0f
    // runs five times, so gotta pump the progress to upper level!
    val size = importChangeSet.getUnprocessedIncomingGPMs.size
    val rsize = importChangeSet.getUnprocessedSavedGPMs.size
    return importChangeSet.getUnprocessedIncomingGPMs.mapNotNull { incomingGPMs ->
        start++
        progressReport("$start / $size (from $rsize encrypted)")
        importChangeSet.getUnprocessedSavedGPMs.firstOrNull { savedGPMs ->
            hasOnlyOneFieldChange(incomingGPMs, savedGPMs, firstProperty, secondProperty)
        }?.let { dbGpmEntry ->
            incomingGPMs to ScoredMatch(
                scoringConfig.oneFieldChangeScore,
                dbGpmEntry
            )
        }
    }.toSet()
}

private fun hasOnlyOneFieldChange(
    first: IncomingGPM,
    second: SavedGPM,
    firstProperty: KProperty1<IncomingGPM, String>,
    secondProperty: KProperty1<SavedGPM, String>,//IVCipherText>
): Boolean {
// List of all comparable properties in GPMEntry and their corresponding encrypted versions in DBGPMEntry
    val properties = listOf(
        IncomingGPM::name to SavedGPM::cachedDecryptedName, //encryptedName,
        IncomingGPM::url to SavedGPM::cachedDecryptedUrl, // encryptedUrl,
        IncomingGPM::username to SavedGPM::cachedDecryptedUsername,// encryptedUsername,
        IncomingGPM::password to SavedGPM::cachedDecryptedPassword,// encryptedPassword
    )

    // Check if the specified properties are different
    // TODO: Could we refer to decrypted properties? we do know the pop name, but makes code more complex
    val isSpecifiedPropertyDifferent =
        firstProperty.get(first).toLowerCasedTrimmedString() != secondProperty.get(second)
            .toLowerCasedTrimmedString()//.decrypt() // TODO: decrypt

    // Check if all other properties are the same
    val areOtherPropertiesSame = properties.all { (gpmProp, dbGpmProp) ->
        // Skip the comparison for the specified property
        if (gpmProp == firstProperty && dbGpmProp == secondProperty) true
        // TODO: Could we refer to decrypted properties? we do know the pop name, but makes code more complex
        else gpmProp.get(first).toLowerCasedTrimmedString() == dbGpmProp.get(second)
            //.decrypt() // TODO: decrypt
            .toLowerCasedTrimmedString()
    }

    // Return true if the specified property is different and all other properties are the same
    return isSpecifiedPropertyDifferent && areOtherPropertiesSame
}

private fun parseUrl(potentialUrl: LowerCaseTrimmedString): URL? {
    return try {
        URL(potentialUrl.lowercasedTrimmed)
    } catch (e: MalformedURLException) {
        try {
            URL("https://${potentialUrl.lowercasedTrimmed}")
        } catch (e: MalformedURLException) {
            null
        }
    }
}
