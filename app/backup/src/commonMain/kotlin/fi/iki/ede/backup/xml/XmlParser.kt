package fi.iki.ede.backup.xml

import okio.Buffer
import okio.BufferedSink
import okio.Source
import okio.buffer

interface XmlPullParser {
    val eventType: Int
    val name: String?
    val text: String?

    fun next(): Int
    fun getAttributeValue(namespace: String?, name: String): String?
    fun setInput(source: Source)

    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }
}

interface XmlSerializer {
    fun setOutput(sink: BufferedSink, encoding: String)
    fun startDocument(encoding: String?, standalone: Boolean?): XmlSerializer
    fun endDocument()
    fun startTag(namespace: String?, name: String): XmlSerializer
    fun attribute(namespace: String?, name: String, value: String): XmlSerializer
    fun text(text: String): XmlSerializer
    fun endTag(namespace: String?, name: String): XmlSerializer
}

class XmlPullParserFactory {
    companion object {
        fun newInstance(): XmlPullParserFactory = XmlPullParserFactory()
    }

    fun newPullParser(): XmlPullParser = SimpleXmlPullParser()
    fun newSerializer(): XmlSerializer = SimpleXmlSerializer()
}

class SimpleXmlPullParser : XmlPullParser {
    private var tokens = emptyList<Token>()
    private var tokenIndex = 0

    override var eventType: Int = XmlPullParser.START_DOCUMENT
        private set

    override var name: String? = null
        private set

    override var text: String? = null
        private set

    private var currentAttributes = emptyMap<String, String>()

    override fun setInput(source: Source) {
        val xml = source.buffer().readUtf8()
        tokens = parseXml(xml)
        tokenIndex = 0
        updateState()
    }

    private fun updateState() {
        if (tokenIndex >= tokens.size) {
            eventType = XmlPullParser.END_DOCUMENT
            name = null
            text = null
            currentAttributes = emptyMap()
            return
        }
        when (val token = tokens[tokenIndex]) {
            Token.StartDocument -> {
                eventType = XmlPullParser.START_DOCUMENT
                name = null
                text = null
                currentAttributes = emptyMap()
            }
            Token.EndDocument -> {
                eventType = XmlPullParser.END_DOCUMENT
                name = null
                text = null
                currentAttributes = emptyMap()
            }
            is Token.StartTag -> {
                eventType = XmlPullParser.START_TAG
                name = token.name
                text = null
                currentAttributes = token.attributes
            }
            is Token.EndTag -> {
                eventType = XmlPullParser.END_TAG
                name = token.name
                text = null
                currentAttributes = emptyMap()
            }
            is Token.Text -> {
                eventType = XmlPullParser.TEXT
                name = null
                text = token.value
                currentAttributes = emptyMap()
            }
        }
    }

    override fun next(): Int {
        if (tokenIndex < tokens.size - 1) {
            tokenIndex++
            updateState()
        } else {
            eventType = XmlPullParser.END_DOCUMENT
        }
        return eventType
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        return currentAttributes[name]
    }

    private sealed interface Token {
        object StartDocument : Token
        object EndDocument : Token
        data class StartTag(val name: String, val attributes: Map<String, String>) : Token
        data class EndTag(val name: String) : Token
        data class Text(val value: String) : Token
    }

    private fun parseXml(xml: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0

        fun peek(): Char? = if (pos < xml.length) xml[pos] else null
        fun consume(): Char {
            val c = xml[pos]
            pos++
            return c
        }

        tokens.add(Token.StartDocument)

        while (pos < xml.length) {
            val c = peek() ?: break
            if (c == '<') {
                consume() // '<'
                val nextC = peek()
                if (nextC == '?') {
                    // skip xml declaration
                    while (pos < xml.length) {
                        if (consume() == '?' && peek() == '>') {
                            consume() // '>'
                            break
                        }
                    }
                } else if (nextC == '/') {
                    consume() // '/'
                    // End tag
                    val nameBuilder = StringBuilder()
                    while (pos < xml.length && peek() != '>') {
                        nameBuilder.append(consume())
                    }
                    if (peek() == '>') consume() // '>'
                    tokens.add(Token.EndTag(nameBuilder.toString().trim()))
                } else {
                    // Start tag
                    val tagContentBuilder = StringBuilder()
                    while (pos < xml.length && peek() != '>') {
                        tagContentBuilder.append(consume())
                    }
                    if (peek() == '>') consume() // '>'

                    val tagContent = tagContentBuilder.toString()
                    val isSelfClosing = tagContent.endsWith('/')
                    val cleanContent = if (isSelfClosing) tagContent.dropLast(1) else tagContent

                    val parts = parseTagContent(cleanContent)
                    val name = parts.first
                    val attributes = parts.second

                    tokens.add(Token.StartTag(name, attributes))
                    if (isSelfClosing) {
                        tokens.add(Token.EndTag(name))
                    }
                }
            } else {
                // Text node
                val textBuilder = StringBuilder()
                while (pos < xml.length && peek() != '<') {
                    textBuilder.append(consume())
                }
                val text = textBuilder.toString()
                if (text.isNotBlank()) {
                    tokens.add(Token.Text(unescapeXml(text)))
                }
            }
        }

        tokens.add(Token.EndDocument)
        return tokens
    }

    private fun parseTagContent(content: String): Pair<String, Map<String, String>> {
        val trimmed = content.trim()
        val spaceIdx = trimmed.indexOfFirst { it.isWhitespace() }
        if (spaceIdx == -1) {
            return Pair(trimmed, emptyMap())
        }
        val name = trimmed.substring(0, spaceIdx)
        val attrsString = trimmed.substring(spaceIdx).trim()
        val attributes = mutableMapOf<String, String>()

        var i = 0
        while (i < attrsString.length) {
            while (i < attrsString.length && attrsString[i].isWhitespace()) {
                i++
            }
            if (i >= attrsString.length) break

            val nameStart = i
            while (i < attrsString.length && attrsString[i] != '=' && !attrsString[i].isWhitespace()) {
                i++
            }
            val attrName = attrsString.substring(nameStart, i)

            while (i < attrsString.length && (attrsString[i] == '=' || attrsString[i].isWhitespace())) {
                i++
            }

            if (i < attrsString.length) {
                val quote = attrsString[i]
                if (quote == '"' || quote == '\'') {
                    i++
                    val valStart = i
                    while (i < attrsString.length && attrsString[i] != quote) {
                        i++
                    }
                    val attrVal = attrsString.substring(valStart, i)
                    if (i < attrsString.length) i++
                    attributes[attrName] = unescapeXml(attrVal)
                } else {
                    val valStart = i
                    while (i < attrsString.length && !attrsString[i].isWhitespace()) {
                        i++
                    }
                    val attrVal = attrsString.substring(valStart, i)
                    attributes[attrName] = unescapeXml(attrVal)
                }
            }
        }
        return Pair(name, attributes)
    }

    private fun unescapeXml(text: String): String {
        if (!text.contains('&')) return text
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val end = text.indexOf(';', i)
                if (end != -1) {
                    val entity = text.substring(i + 1, end)
                    when {
                        entity == "lt" -> result.append('<')
                        entity == "gt" -> result.append('>')
                        entity == "amp" -> result.append('&')
                        entity == "quot" -> result.append('"')
                        entity == "apos" -> result.append('\'')
                        entity.startsWith("#x") -> {
                            val hex = entity.substring(2)
                            result.append(hex.toInt(16).toChar())
                        }
                        entity.startsWith("#") -> {
                            val dec = entity.substring(1)
                            result.append(dec.toInt().toChar())
                        }
                        else -> result.append('&').append(entity).append(';')
                    }
                    i = end + 1
                } else {
                    result.append(c)
                    i++
                }
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }
}

class SimpleXmlSerializer : XmlSerializer {
    private var sink: BufferedSink? = null
    private val sb = StringBuilder()
    private var pendingTag: String? = null

    override fun setOutput(sink: BufferedSink, encoding: String) {
        this.sink = sink
    }

    override fun startDocument(encoding: String?, standalone: Boolean?): XmlSerializer {
        sb.append("<?xml version=\"1.0\" encoding=\"${encoding ?: "UTF-8"}\"?>\n")
        return this
    }

    override fun startTag(namespace: String?, name: String): XmlSerializer {
        closePendingStartTag()
        sb.append("<").append(name)
        pendingTag = name
        return this
    }

    private fun closePendingStartTag() {
        if (pendingTag != null) {
            sb.append(">")
            pendingTag = null
        }
    }

    override fun attribute(namespace: String?, name: String, value: String): XmlSerializer {
        requireNotNull(pendingTag) { "Attribute must be written inside a start tag" }
        sb.append(" ").append(name).append("=\"").append(escapeXml(value)).append("\"")
        return this
    }

    override fun text(text: String): XmlSerializer {
        closePendingStartTag()
        sb.append(escapeXml(text))
        return this
    }

    override fun endTag(namespace: String?, name: String): XmlSerializer {
        if (pendingTag == name) {
            sb.append("/>")
            pendingTag = null
        } else {
            closePendingStartTag()
            sb.append("</").append(name).append(">")
        }
        return this
    }

    override fun endDocument() {
        closePendingStartTag()
        sink?.let {
            it.writeUtf8(sb.toString())
            it.flush()
        }
    }

    private fun escapeXml(text: String): String {
        val result = StringBuilder()
        for (c in text) {
            when (c) {
                '<' -> result.append("&lt;")
                '>' -> result.append("&gt;")
                '&' -> result.append("&amp;")
                '"' -> result.append("&quot;")
                '\'' -> result.append("&apos;")
                else -> result.append(c)
            }
        }
        return result.toString()
    }
}
