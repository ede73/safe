package fi.iki.ede.db

import androidx.room.TypeConverter
import fi.iki.ede.crypto.IVCipherText
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object RoomConverters {
    @TypeConverter
    fun toByteArray(value: IVCipherText?): ByteArray? {
        if (value == null) return null
        if (value.isEmpty()) return ByteArray(0)
        return value.combineIVAndCipherText()
    }

    @TypeConverter
    fun fromByteArray(value: ByteArray?): IVCipherText? {
        if (value == null || value.isEmpty()) return IVCipherText.getEmpty()
        return IVCipherText(16, value)
    }

    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.epochSeconds
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.fromEpochSeconds(it) }
    }
}
