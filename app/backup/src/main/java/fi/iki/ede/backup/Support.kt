package fi.iki.ede.backup

import fi.iki.ede.backup.ExportConfig.Companion.ATTRIBUTE_PREFIX_CIPHER
import fi.iki.ede.backup.ExportConfig.Companion.ATTRIBUTE_PREFIX_IV
import fi.iki.ede.backup.ExportConfig.Companion.Attributes
import fi.iki.ede.backup.ExportConfig.Companion.Elements
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.SavedGPM
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.time.ZoneOffset

internal fun XmlSerializer.encryptedAttribute(
    name: Attributes,
    encryptedValue: IVCipherText,
): XmlSerializer {
    this.attribute(
        null,
        "$ATTRIBUTE_PREFIX_IV${name.value}",
        encryptedValue.iv.toHexString()
    )
    this.attribute(
        null,
        "$ATTRIBUTE_PREFIX_CIPHER${name.value}",
        encryptedValue.cipherText.toHexString()
    )
    return this
}

internal fun XmlSerializer.addTagAndCData(
    name: Elements,
    encryptedValue: IVCipherText,
    plainTextAttribute: Pair<Attributes, String>? = null
) = startTag(name)
    .let {
        if (plainTextAttribute?.first != null) {
            it.plainTextAttribute(plainTextAttribute.first, plainTextAttribute.second)
        }
        this
    }
    .let {
        // cipher text goes to CData, IV to the attribute..
        it.plainTextAttribute(Attributes.IV, encryptedValue.iv.toHexString())
        it.text(encryptedValue.cipherText.toHexString())
        this
    }
    .endTag(name)


internal fun XmlSerializer.endTag(name: Elements) = endTag(null, name.value)
internal fun XmlSerializer.startTag(name: Elements) = startTag(null, name.value)
internal fun XmlSerializer.plainTextAttribute(name: Attributes, value: String) =
    attribute(null, name.value, value)

internal fun XmlSerializer.prefixedPlainTextAttribute(
    name: Attributes,
    prefix: String,
    value: String
) =
    attribute(null, "${prefix}${name.value}", value)

internal fun XmlSerializer.startTagWithIVCipherAttribute(
    name: Elements,
    attr: Pair<Attributes, IVCipherText>? = null
) = startTag(name)
    .let {
        if (attr?.first != null) {
            it.encryptedAttribute(attr.first, attr.second)
        }
        this
    }

internal fun XmlSerializer.writeSiteEntry(siteEntry: DecryptableSiteEntry) {
    // needed for GPM mapping, won't break bank nor backwards compatibility
    startTag(Elements.SITE_ENTRY).plainTextAttribute(
        Attributes.SITE_ENTRY_ID,
        siteEntry.id.toString()
    ).let {
        if (siteEntry.deleted > 0) {
            it.plainTextAttribute(
                Attributes.SITE_ENTRY_DELETED,
                siteEntry.deleted.toString()
            )
        }
    }
    addTagAndCData(Elements.SITE_ENTRY_DESCRIPTION, siteEntry.description)
    addTagAndCData(Elements.SITE_ENTRY_WEBSITE, siteEntry.website)
    addTagAndCData(Elements.SITE_ENTRY_USERNAME, siteEntry.username)

    addTagAndCData(
        Elements.SITE_ENTRY_PASSWORD, siteEntry.password,
        siteEntry.passwordChangedDate?.let {
            Pair(
                Attributes.SITE_ENTRY_PASSWORD_CHANGED,
                it.withZoneSameInstant(ZoneOffset.UTC).toEpochSecond().toString()
            )
        }
    )

    addTagAndCData(Elements.SITE_ENTRY_NOTE, siteEntry.note)

    if (siteEntry.photo != IVCipherText.getEmpty()) {
        addTagAndCData(Elements.SITE_ENTRY_PHOTO, siteEntry.photo)
    }

    if (siteEntry.extensions != IVCipherText.getEmpty()) {
        addTagAndCData(Elements.SITE_ENTRY_EXTENSION, siteEntry.extensions)
    }

    endTag(Elements.SITE_ENTRY)
}

internal fun XmlPullParser.getAttributeValue(
    attribute: Attributes,
    prefix: String? = null
): String {
    val attrName = if (prefix == null) attribute.value else "$prefix${attribute.value}"
    return getAttributeValue(null, attrName) ?: ""
}

// After some recent update while restoring, date parsing fails due to non breakable space
// Wasn't able to track IN THE EMULATOR where it comes from
internal fun XmlPullParser.getTrimmedAttributeValue(
    attribute: Attributes,
    prefix: String? = null
): String =
    getAttributeValue(attribute, prefix).trim()

internal inline fun <reified T : Enum<T>, V> valueOrNull(value: V, valueSelector: (T) -> V): T? =
    enumValues<T>().find { valueSelector(it) == value }

internal fun XmlPullParser.getEncryptedAttribute(name: Attributes): IVCipherText {
    val iv = getTrimmedAttributeValue(name, ATTRIBUTE_PREFIX_IV)
    val cipher = getTrimmedAttributeValue(name, ATTRIBUTE_PREFIX_CIPHER)
    if (iv.isNotBlank() && cipher.isNotBlank()) {
        return IVCipherText(iv.hexToByteArray(), cipher.hexToByteArray())
    }
    return IVCipherText.getEmpty()
}

internal fun XmlPullParser.maybeGetText(gotTextNode: (encryptedText: IVCipherText) -> Unit) {
    val iv = getTrimmedAttributeValue(ExportConfig.Companion.Attributes.IV)
    next()
    if (eventType == XmlPullParser.TEXT && text != null && iv.isNotBlank()) {
        gotTextNode.invoke(
            IVCipherText(
                iv.trim().hexToByteArray(),
                text.trim().hexToByteArray()
            )
        )
    }
}

internal fun XmlSerializer.writeGPMEntry(savedGPM: SavedGPM, gpmToPasswords: Set<DBID>) {
    startTag(Elements.IMPORTS_GPM_ITEM)
        .plainTextAttribute(Attributes.IMPORTS_GPM_ITEM_HASH, savedGPM.hash)
        .plainTextAttribute(Attributes.IMPORTS_GPM_ITEM_ID, savedGPM.id.toString())
        .plainTextAttribute(
            Attributes.IMPORTS_GPM_ITEM_MAP_TO_SITE_ENTRY,
            gpmToPasswords.joinToString(",")
        )
        .encryptedAttribute(Attributes.IMPORTS_GPM_ITEM_NAME, savedGPM.encryptedName)
        .encryptedAttribute(Attributes.IMPORTS_GPM_ITEM_NOTE, savedGPM.encryptedNote)
        .encryptedAttribute(
            Attributes.IMPORTS_GPM_ITEM_PASSWORD,
            savedGPM.encryptedPassword
        )
        .plainTextAttribute(
            Attributes.IMPORTS_GPM_ITEM_STATUS,
            if (savedGPM.flaggedIgnored) "1" else "0"
        )
        .encryptedAttribute(Attributes.IMPORTS_GPM_ITEM_URL, savedGPM.encryptedUrl)
        .encryptedAttribute(
            Attributes.IMPORTS_GPM_ITEM_USERNAME,
            savedGPM.encryptedUsername
        )
    endTag(Elements.IMPORTS_GPM_ITEM)
}
