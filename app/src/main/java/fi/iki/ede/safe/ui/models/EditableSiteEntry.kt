package fi.iki.ede.safe.ui.models

import android.graphics.Bitmap
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.safe.db.DBID
import java.time.ZonedDateTime

data class EditableSiteEntry(
    val categoryId: DBID,
    val id: DBID? = null,
    // For purposes of editing fields, description and website probably aren't super sensitie
    val description: String = "",
    val website: String = "",
    // Trying to keep these as secure as possible all the time
    val username: IVCipherText = IVCipherText.getEmpty(),
    val password: IVCipherText = IVCipherText.getEmpty(),
    val note: IVCipherText = IVCipherText.getEmpty(),
    // Since we're actually displaying the photo in UI unconditionally
    // it doesn't lessen security having it as bitmap here
    val plainPhoto: Bitmap? = null,
    val passwordChangedDate: ZonedDateTime? = null
)