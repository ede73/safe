package fi.iki.ede.gpm.changeset

import fi.iki.ede.gpm.model.*

data class ScoredMatch(val matchScore: Double, val item: SavedGPM, val hashMatch: Boolean = false)