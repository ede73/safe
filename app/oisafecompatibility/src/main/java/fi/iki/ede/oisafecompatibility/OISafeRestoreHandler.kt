package fi.iki.ede.oisafecompatibility

import fi.iki.ede.crypto.EncryptedPassword
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedEncryptedPassword
import fi.iki.ede.crypto.hexToByteArray
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

//interface IRestoreDataSet {
//    var ch: OISafeCryptoHelper
//    var version: Int
//    var date: String?
//    var saltedEncryptedPassword: SaltedEncryptedPassword
//    var totalEntries: Int
//    fun newCategory(extractedCategoryName: String)
//    fun storyCategory()
//    fun newEntry()
//    fun storeEntry()
//    fun encryptDecrypted(value: String): IVCipherText
//    fun setDescription(extractedDescription: String)
//    fun setWebsite(extractedWebsite: String)
//    fun setUsername(extractedUsername: String)
//    fun setPassword(extractedPassword: String)
//    fun setNote(extractedNote: String)
//    fun setPasswordChangedDate(passwordChangedDate: String)
//    fun gotSalt(
//        saltedEncryptedMasterkeyOfBackup: SaltedEncryptedPassword,
//        passwordOfBackup: Password
//    ): SaltedEncryptedPassword
//}

@Deprecated("Just for backwards compatibility")
class OISafeRestoreHandler(
    encrypt: (ByteArray) -> IVCipherText,
    private val passwordOfBackup: Password,
) :
    DefaultHandler() {
    private var currentValue: String = ""
    private val parsedData: RestoreDataSet =
        RestoreDataSet(OISafeCryptoHelper(Algorithm.EXTERNAL_OLD), encrypt)

    override fun startElement(
        namespaceURI: String, localName: String,
        qName: String, atts: Attributes
    ) {
        when (qName) {
            // <OISafe version="1" date="Apr 19, 2023 2:02:03 PM Pacific Daylight Time">
            "OISafe" -> { // TODO:
                parsedData.version = atts.getValue("version").toInt()
                parsedData.date = atts.getValue("date")
            }

            // <Category name="..." />
            "Category" -> parsedData.newCategory(atts.getValue("name"))

            "Entry" -> parsedData.newEntry()

        }
        currentValue = ""
    }

    override fun endElement(namespaceURI: String, localName: String, qName: String) {
        when (qName) {
            "OISafe" -> {}
            "MasterKey" -> parsedData.saltedEncryptedPassword =
                SaltedEncryptedPassword(
                    parsedData.saltedEncryptedPassword.salt,
                    EncryptedPassword(currentValue.hexToByteArray())
                )

            "Salt" -> parsedData.saltedEncryptedPassword =
                parsedData.gotSalt(
                    SaltedEncryptedPassword(
                        Salt(currentValue.hexToByteArray()),
                        parsedData.saltedEncryptedPassword.encryptedPassword
                    ), passwordOfBackup
                )

            "Category" -> parsedData.storyCategory()
            // of category
            "Entry" -> parsedData.storeEntry()
            // of Entry
            "Description" -> if (currentValue.isNotEmpty()) parsedData.setDescription(
                currentValue
            )

            "Website" -> if (currentValue.isNotEmpty()) parsedData.setWebsite(currentValue)
            "Username" -> if (currentValue.isNotEmpty()) parsedData.setUsername(currentValue)
            "Password" -> if (currentValue.isNotEmpty()) parsedData.setPassword(currentValue)
            "Note" -> if (currentValue.isNotEmpty()) parsedData.setNote(currentValue)
            "PasswordChangedDate" -> if (currentValue.isNotEmpty()) parsedData.setPasswordChangedDate(
                currentValue
            )
        }
        currentValue = ""
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        currentValue += String(ch, start, length)
    }

    fun parse(streamData: InputStream): RestoreDataSet {
        val xr = SAXParserFactory.newInstance().newSAXParser().xmlReader
        xr.contentHandler = this
        xr.parse(InputSource(streamData))
        return parsedData
    }
}