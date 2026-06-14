package fi.iki.ede.cryptoobjects

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.decrypt
import kotlin.time.Instant

fun DecryptableSiteEntry.isSame(
    description: String,
    website: String,
    username: IVCipherText,
    password: IVCipherText,
    passwordChangedDate: Instant?,
    note: IVCipherText,
    photo: PlatformBitmap?,
    extensions: Map<String, Set<String>>
) = cachedPlainDescription == description &&
        plainWebsite == website &&
        plainUsername == username.decrypt() &&
        isSamePassword(password) &&
        this.passwordChangedDate == passwordChangedDate &&
        plainNote == note.decrypt() &&
        (photo?.sameAs(plainPhoto) ?: (plainPhoto == null)) &&
        !areMapsDifferent(extensions, this.plainExtensions)

private fun areMapsDifferent(
    map1: Map<String, Set<String>>,
    map2: Map<String, Set<String>>
): Boolean {
    val cleanMap1 = map1.mapValues { (_, value) -> value.filterNot { it.isBlank() }.toSet() }
        .filterNot { it.value.isEmpty() }
    val cleanMap2 = map2.mapValues { (_, value) -> value.filterNot { it.isBlank() }.toSet() }
        .filterNot { it.value.isEmpty() }

    if (cleanMap1.keys != cleanMap2.keys) return true

    return cleanMap1.any { (key, value) ->
        value != cleanMap2[key]
    }
}
