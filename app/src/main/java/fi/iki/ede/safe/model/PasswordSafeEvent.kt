package fi.iki.ede.safe.model

// TODO: hot events not actually used right now(never?)
sealed class PasswordSafeEvent {
    sealed class CategoryEvent : PasswordSafeEvent() {
        data class Added(val category: DecryptableCategoryEntry) : CategoryEvent()
        data class Deleted(val category: DecryptableCategoryEntry) : CategoryEvent()
        data class Updated(val category: DecryptableCategoryEntry) : CategoryEvent()
    }

    sealed class SiteEntryEvent : PasswordSafeEvent() {
        data class Added(
            val category: DecryptableCategoryEntry,
            val password: DecryptableSiteEntry
        ) : SiteEntryEvent()

        data class Updated(
            val category: DecryptableCategoryEntry,
            val password: DecryptableSiteEntry
        ) : SiteEntryEvent()

        data class Removed(
            val category: DecryptableCategoryEntry,
            val password: DecryptableSiteEntry
        ) : SiteEntryEvent()
    }
}