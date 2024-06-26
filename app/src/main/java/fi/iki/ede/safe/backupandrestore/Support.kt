package fi.iki.ede.safe.backupandrestore

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.ATTRIBUTE_PREFIX_CIPHER
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.ATTRIBUTE_PREFIX_IV
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Attributes
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Elements
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DecryptableSiteEntry
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.time.ZoneOffset

internal fun XmlSerializer.attribute(
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
    attr: Pair<Attributes, String>? = null
) = startTag(name)
    .let {
        if (attr?.first != null) {
            it.attribute(attr.first, attr.second)
        }
        this
    }
    .let {
        it.attribute(Attributes.IV, encryptedValue.iv.toHexString())
        it.text(encryptedValue.cipherText.toHexString())
        this
    }
    .endTag(name)


internal fun XmlSerializer.endTag(name: Elements) = endTag(null, name.value)
internal fun XmlSerializer.startTag(name: Elements) = startTag(null, name.value)
internal fun XmlSerializer.attribute(name: Attributes, value: String) =
    attribute(null, name.value, value)

internal fun XmlSerializer.attribute(name: Attributes, prefix: String, value: String) =
    attribute(null, "${prefix}${name.value}", value)

internal fun XmlSerializer.startTagWithIVCipherAttribute(
    name: Elements,
    attr: Pair<Attributes, IVCipherText>? = null
) = startTag(name)
    .let {
        if (attr?.first != null) {
            it.attribute(attr.first, attr.second)
        }
        this
    }

internal fun XmlSerializer.writeSiteEntry(siteEntry: DecryptableSiteEntry) {
    // needed for GPM mapping, won't break bank nor backwards compatibility
    startTag(Elements.SITE_ENTRY).attribute(
        Attributes.SITE_ENTRY_ID,
        siteEntry.id.toString()
    ).let {
        if (siteEntry.deleted > 0) {
            it.attribute(Attributes.SITE_ENTRY_DELETED, siteEntry.deleted.toString())
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

    // only write extensions we we REALLY have them
    if (siteEntry.extensions.values.flatten().isNotEmpty()) {
        startTag(Elements.SITE_ENTRY_EXTENSIONS)
        siteEntry.extensions.forEach { extension ->
            if (extension.value.isNotEmpty()) {
                startTag(Elements.SITE_ENTRY_EXTENSIONS_EXTENSION)
                    .attribute(
                        Attributes.SITE_ENTRY_EXTENSION_NAME,
                        extension.key.extensionName.encrypt()
                    )
                extension.value.forEach {
                    addTagAndCData(
                        Elements.SITE_ENTRY_EXTENSIONS_EXTENSION_VALUE,
                        it.trim().encrypt()
                    )
                }
                endTag(Elements.SITE_ENTRY_EXTENSIONS_EXTENSION)
            }
        }
        endTag(Elements.SITE_ENTRY_EXTENSIONS)
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
                iv.hexToByteArray(),
                text.hexToByteArray()
            )
        )
    }
}

internal fun XmlSerializer.writeGPMEntry(savedGPM: SavedGPM, gpmToPasswords: Set<DBID>) {
    startTag(Elements.IMPORTS_GPM_ITEM)
        .attribute(Attributes.IMPORTS_GPM_ITEM_HASH, savedGPM.hash)
        .attribute(Attributes.IMPORTS_GPM_ITEM_ID, savedGPM.id.toString())
        .attribute(
            Attributes.IMPORTS_GPM_ITEM_MAP_TO_SITE_ENTRY,
            gpmToPasswords.joinToString(",")
        )
        .attribute(Attributes.IMPORTS_GPM_ITEM_NAME, savedGPM.encryptedName)
        .attribute(Attributes.IMPORTS_GPM_ITEM_NOTE, savedGPM.encryptedNote)
        .attribute(Attributes.IMPORTS_GPM_ITEM_PASSWORD, savedGPM.encryptedPassword)
        .attribute(
            Attributes.IMPORTS_GPM_ITEM_STATUS,
            if (savedGPM.flaggedIgnored) "1" else "0"
        )
        .attribute(Attributes.IMPORTS_GPM_ITEM_URL, savedGPM.encryptedUrl)
        .attribute(Attributes.IMPORTS_GPM_ITEM_USERNAME, savedGPM.encryptedUsername)
    endTag(Elements.IMPORTS_GPM_ITEM)
}
