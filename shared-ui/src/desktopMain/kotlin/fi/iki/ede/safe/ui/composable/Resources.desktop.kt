package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object DesktopResources {
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        loadResources()
    }

    private fun loadResources() {
        val userLang = Locale.getDefault().language
        val baseDir = File("app/src/main/res")
        
        val defaultStringsFile = File(baseDir, "values/strings.xml")
        if (defaultStringsFile.exists()) {
            parseFile(defaultStringsFile)
        }
        
        if (userLang == "fi") {
            val fiStringsFile = File(baseDir, "values-fi/strings.xml")
            if (fiStringsFile.exists()) {
                parseFile(fiStringsFile)
            }
        }
    }

    private fun parseFile(file: File) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            doc.documentElement.normalize()
            
            val stringNodes = doc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val element = stringNodes.item(i) as Element
                val name = element.getAttribute("name")
                val value = element.textContent
                strings[name] = value
            }
            
            val pluralNodes = doc.getElementsByTagName("plurals")
            for (i in 0 until pluralNodes.length) {
                val element = pluralNodes.item(i) as Element
                val name = element.getAttribute("name")
                val itemMap = mutableMapOf<String, String>()
                val itemNodes = element.getElementsByTagName("item")
                for (j in 0 until itemNodes.length) {
                    val itemElement = itemNodes.item(j) as Element
                    val quantity = itemElement.getAttribute("quantity")
                    val value = itemElement.textContent
                    itemMap[quantity] = value
                }
                plurals[name] = itemMap
            }
        } catch (e: Exception) {
            // Fallback to defaults
        }
    }

    fun getString(id: String): String {
        return strings[id] ?: getFallbackString(id)
    }

    fun getPluralString(id: String, quantity: Int): String {
        val itemMap = plurals[id]
        val quantityStr = when (quantity) {
            0 -> "zero"
            1 -> "one"
            2 -> "two"
            else -> "other"
        }
        return itemMap?.get(quantityStr) ?: itemMap?.get("other") ?: getFallbackPlural(id, quantityStr)
    }

    private fun getFallbackString(id: String): String = when (id) {
        "generic_ok" -> "OK!"
        "category_list_edit_category" -> "Edit category name"
        "category_list_delete" -> "Delete %s?"
        "category_list_delete_confirm" -> "Are you sure you want to delete %s?"
        "category_list_edit" -> "Edit category %s"
        else -> id
    }

    private fun getFallbackPlural(id: String, quantityStr: String): String = when (id) {
        "password_list_password_age_days" -> when (quantityStr) {
            "zero" -> "(brand new)"
            "one" -> "%d day"
            else -> "%d days"
        }
        "password_list_password_age_months" -> when (quantityStr) {
            "one" -> "%d month"
            else -> "%d months"
        }
        "password_list_password_age_years" -> when (quantityStr) {
            "one" -> "%d year"
            else -> "%d years"
        }
        "category_passwords_count" -> when (quantityStr) {
            "one" -> "%d password"
            else -> "%d passwords"
        }
        else -> "%d $id"
    }
}

@Composable
actual fun getString(id: String): String {
    return DesktopResources.getString(id)
}

@Composable
actual fun getString(id: String, formatArg: String): String {
    val raw = DesktopResources.getString(id)
    return raw.replace("%s", formatArg).replace("%1\$s", formatArg)
}

@Composable
actual fun getPluralString(id: String, quantity: Int, formatArg: Int): String {
    val raw = DesktopResources.getPluralString(id, quantity)
    return raw.replace("%d", formatArg.toString()).replace("%1\$d", formatArg.toString())
}
