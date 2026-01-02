package dev.hendry.svg2vd.util

sealed class XmlNode

class XmlText(val text: String) : XmlNode()
class XmlCData(val text: String) : XmlNode()

/**
 * Simple XML element representation for Kotlin Multiplatform parsing.
 */
class XmlElement(
    val tagName: String,
    val line: Int = 0
) : XmlNode() {
    val attributes: Map<String, String>
        field = mutableMapOf()

    val children: List<XmlNode>
        field = mutableListOf()

    var parent: XmlElement? = null
        internal set

    internal fun setAttribute(name: String, value: String) {
        attributes[name] = value
    }

    internal fun addChild(child: XmlNode) {
        children.add(child)
        if (child is XmlElement) {
            child.parent = this
        }
    }

    fun getAttribute(name: String): String = attributes[name] ?: ""

    fun hasAttribute(name: String): Boolean = name in attributes

    fun getChildElements(): List<XmlElement> = children.filterIsInstance<XmlElement>()

    fun getElementsByTagName(name: String): List<XmlElement> = buildList {
        collectElementsByTagName(name, this)
    }

    private fun collectElementsByTagName(name: String, result: MutableList<XmlElement>) {
        children.filterIsInstance<XmlElement>().forEach { child ->
            if (child.tagName == name) {
                result.add(child)
            }
            child.collectElementsByTagName(name, result)
        }
    }

    fun getTextContent(): String = buildString {
        collectTextContent(this)
    }

    private fun collectTextContent(sb: StringBuilder) {
        for (child in children) {
            when (child) {
                is XmlText -> sb.append(child.text)
                is XmlCData -> sb.append(child.text)
                is XmlElement -> child.collectTextContent(sb)
            }
        }
    }
}

class XmlDocument(
    val root: XmlElement?,
    val errors: List<String> = emptyList()
)

/**
 * Simple XML parser in Kotlin Multiplatform.
 * Supports basic XML including elements, attributes, text, CDATA, and comments.
 */
object XmlParser {

    fun parse(content: String): XmlDocument {
        val errors = mutableListOf<String>()
        val parser = XmlParserState(content, errors)
        val root = parser.parseDocument()
        return XmlDocument(root, errors)
    }

    private class XmlParserState(
        private val content: String,
        private val errors: MutableList<String>
    ) {
        private var pos = 0
        private var line = 1

        fun parseDocument(): XmlElement? {
            skipWhitespace()

            if (lookingAt("<?xml")) {
                skipUntil("?>")
                skip(2)
                skipWhitespace()
            }

            if (lookingAt("<!DOCTYPE")) {
                skipDoctype()
                skipWhitespace()
            }

            return if (lookingAt("<") && !lookingAt("<!--") && !lookingAt("<![CDATA[")) {
                parseElement()
            } else {
                errors.add("No root element found")
                null
            }
        }

        private fun parseElement(): XmlElement? {
            if (!consume('<')) return null

            val startLine = line
            val tagName = parseTagName()
            if (tagName.isEmpty()) {
                errors.add("Line $line: Empty tag name")
                return null
            }

            val element = XmlElement(tagName, line = startLine)

            while (true) {
                skipWhitespace()
                if (lookingAt("/>")) {
                    skip(2)
                    return element
                }
                if (lookingAt(">")) {
                    skip(1)
                    break
                }
                if (isAtEnd()) {
                    errors.add("Line $line: Unexpected end of input in element <$tagName>")
                    return element
                }

                parseAttribute()?.let { (name, value) ->
                    element.setAttribute(name, value)
                }
            }

            while (!isAtEnd()) {
                skipWhitespace()

                if (lookingAt("</")) {
                    // End tag
                    skip(2)
                    val endTagName = parseTagName()
                    skipWhitespace()
                    consume('>')
                    if (endTagName != tagName) {
                        errors.add("Line $line: Mismatched tags: expected </$tagName>, found </$endTagName>")
                    }
                    return element
                }

                if (lookingAt("<!--")) {
                    // Comment
                    skipComment()
                    continue
                }

                if (lookingAt("<![CDATA[")) {
                    parseCData()?.let { element.addChild(XmlCData(it)) }
                    continue
                }

                if (lookingAt("<")) {
                    parseElement()?.let { child ->
                        element.addChild(child)
                    }
                    continue
                }

                // Text content
                val text = parseText()
                if (text.isNotEmpty()) {
                    element.addChild(XmlText(text))
                }
            }

            return element
        }

        private fun parseTagName(): String {
            val start = pos
            while (!isAtEnd() && isNameChar(current())) {
                advance()
            }
            return content.substring(start, pos)
        }

        private fun parseAttribute(): Pair<String, String>? {
            val name = parseAttributeName()
            if (name.isEmpty()) return null

            skipWhitespace()
            if (!consume('=')) {
                errors.add("Line $line: Expected '=' after attribute name '$name'")
                return name to ""
            }
            skipWhitespace()

            val value = parseAttributeValue()
            return name to value
        }

        private fun parseAttributeName(): String {
            val start = pos
            while (!isAtEnd() && isNameChar(current())) {
                advance()
            }
            return content.substring(start, pos)
        }

        private fun parseAttributeValue(): String {
            val quote = current()
            if (quote != '"' && quote != '\'') {
                errors.add("Line $line: Expected quote for attribute value")
                return ""
            }
            advance()

            return buildString {
                while (!isAtEnd() && current() != quote) {
                    if (lookingAt("&")) {
                        append(parseEntity())
                    } else {
                        append(current())
                        advance()
                    }
                }
            }.also { consume(quote) }
        }

        private fun parseText(): String = buildString {
            while (!isAtEnd() && current() != '<') {
                if (lookingAt("&")) {
                    append(parseEntity())
                } else {
                    append(current())
                    advance()
                }
            }
        }

        private fun parseEntity(): String {
            advance() // skip '&'
            val start = pos
            while (!isAtEnd() && current() != ';') {
                advance()
            }
            val entity = content.substring(start, pos)
            consume(';')

            return when {
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "amp" -> "&"
                entity == "apos" -> "'"
                entity == "quot" -> "\""
                entity.startsWith("#x") -> {
                    val codePoint = entity.removePrefix("#x").toIntOrNull(16)
                    codePointToString(codePoint, entity)
                }
                entity.startsWith("#") -> {
                    val codePoint = entity.removePrefix("#").toIntOrNull()
                    codePointToString(codePoint, entity)
                }
                else -> "&$entity;"
            }
        }

        private fun codePointToString(codePoint: Int?, entity: String): String {
            if (codePoint == null || codePoint < 0 || codePoint > 0x10FFFF) {
                return "&$entity;"
            }
            return if (codePoint <= 0xFFFF) {
                codePoint.toChar().toString()
            } else {
                // Supplementary character: convert to surrogate pair
                val highSurrogate = ((codePoint - 0x10000) shr 10) + 0xD800
                val lowSurrogate = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
                "${highSurrogate.toChar()}${lowSurrogate.toChar()}"
            }
        }

        private fun parseCData(): String? {
            if (!lookingAt("<![CDATA[")) return null
            skip(9)
            val start = pos
            while (!isAtEnd() && !lookingAt("]]>")) {
                advance()
            }
            val result = content.substring(start, pos)
            skip(3)
            return result
        }

        private fun skipComment() {
            skip(4) // <!--
            while (!isAtEnd() && !lookingAt("-->")) {
                advance()
            }
            skip(3) // -->
        }

        private fun skipDoctype() {
            var depth = 1
            skip(9) // <!DOCTYPE
            while (!isAtEnd() && depth > 0) {
                when {
                    lookingAt("<") -> {
                        depth++
                        advance()
                    }
                    lookingAt(">") -> {
                        depth--
                        advance()
                    }
                    else -> advance()
                }
            }
        }

        private fun skipWhitespace() {
            while (!isAtEnd() && current().isWhitespace()) {
                advance()
            }
        }

        private fun skipUntil(s: String) {
            while (!isAtEnd() && !lookingAt(s)) {
                advance()
            }
        }

        private fun lookingAt(s: String): Boolean = content.startsWith(s, pos)

        private fun skip(n: Int) {
            repeat(n) { advance() }
        }

        private fun consume(c: Char): Boolean =
            (!isAtEnd() && current() == c).also { if (it) advance() }

        private fun current(): Char = content[pos]

        private fun advance() {
            if (current() == '\n') line++
            pos++
        }

        private fun isAtEnd(): Boolean = pos >= content.length

        private fun isNameChar(c: Char): Boolean =
            c.isLetterOrDigit() || c in "-_.:"
    }
}
