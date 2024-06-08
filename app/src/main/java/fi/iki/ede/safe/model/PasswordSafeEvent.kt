package fi.iki.ede.safe.model

import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry

// TODO: hot events not actually used right now(never?)
sealed class PasswordSafeEvent {
    sealed class CategoryEvent : PasswordSafeEvent() {
        data class Added(val category: DecryptableCategoryEntry) : CategoryEvent()
        data class Deleted(val category: DecryptableCategoryEntry) : CategoryEvent()
        data class Updated(val category: DecryptableCategoryEntry) : CategoryEvent()
    }

    sealed class PasswordEvent : PasswordSafeEvent() {
        data class Added(
            val category: DecryptableCategoryEntry,
            val password: DecryptableSiteEntry
        ) : PasswordEvent()

        data class Updated(
            val category: DecryptableCategoryEntry,
            val password: DecryptableSiteEntry
        ) : PasswordEvent()

        data class Removed(
            val category: DecryptableCategoryEntry,
            val password: DecryptableSiteEntry
        ) : PasswordEvent()
    }
}