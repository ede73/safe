package fi.iki.ede.gpm.model

data class ScoringConfig(
    // match score given to records that have all but 1 matching fields
    val oneFieldChangeScore: Double = 0.99,
    // similarity score required to match record names (not usernames!)
    val recordNameSimilarityThreshold: Double = 0.7,
    // url domain name similarity threshold required for positive match
    val urlDomainMatchThresholdIfUsernameMatches: Double = 0.7,
    // IF record name has a similarity match, username matches AND url domains (assuming both have it) matches, we increase score by 0.1
    val scoreIncrementIfUsernameAndUrlDomainMatch: Double = 0.1
)