package fi.iki.ede.cryptoobjects

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.decrypt

// TODO: Doesn't really belong to this project, does it?
// Addressed PR5 comment: Restored original comment above
@Entity(tableName = "categories")
class DecryptableCategoryEntry {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long? = null

    @ColumnInfo(name = "name")
    var encryptedName = IVCipherText.getEmpty()

    val plainName: String
        get() = encryptedName.decrypt()

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    // Addressed PR5 comment: Restored original comment above
    fun copy(): DecryptableCategoryEntry = DecryptableCategoryEntry().apply {
        id = this@DecryptableCategoryEntry.id
        encryptedName = this@DecryptableCategoryEntry.encryptedName
        containedSiteEntryCount = this@DecryptableCategoryEntry.containedSiteEntryCount
    }

    @Ignore
    var containedSiteEntryCount = 0
}
