package dev.hendry.svg2vd.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XmlParserTest {

    @Test
    fun `WHEN parsing simple element THEN returns root with tag name`() {
        val doc = XmlParser.parse("<root/>")
        val root = assertNotNull(doc.root)
        assertEquals("root", root.tagName)
        assertTrue(doc.errors.isEmpty())
    }

    @Test
    fun `WHEN parsing element with attributes THEN attributes are accessible`() {
        val doc = XmlParser.parse("""<rect x="10" y="20" width="100" height="50"/>""")
        val root = assertNotNull(doc.root)
        assertEquals("rect", root.tagName)
        assertEquals("10", root.getAttribute("x"))
        assertEquals("20", root.getAttribute("y"))
        assertEquals("100", root.getAttribute("width"))
        assertEquals("50", root.getAttribute("height"))
    }

    @Test
    fun `WHEN parsing nested elements THEN hierarchy is preserved`() {
        val doc = XmlParser.parse("""
            <svg>
                <g>
                    <rect/>
                </g>
            </svg>
        """.trimIndent())

        val root = assertNotNull(doc.root)
        assertEquals("svg", root.tagName)

        val g = assertNotNull(root.getChildElements().firstOrNull())
        assertEquals("g", g.tagName)

        val rect = assertNotNull(g.getChildElements().firstOrNull())
        assertEquals("rect", rect.tagName)
    }

    @Test
    fun `WHEN parsing text content THEN getTextContent returns it`() {
        val doc = XmlParser.parse("<title>Hello World</title>")
        val root = assertNotNull(doc.root)
        assertEquals("Hello World", root.getTextContent())
    }

    @Test
    fun `WHEN parsing CDATA section THEN content is preserved`() {
        val doc = XmlParser.parse("<style><![CDATA[.cls { fill: red; }]]></style>")
        val root = assertNotNull(doc.root)
        assertEquals(".cls { fill: red; }", root.getTextContent())
    }

    @Test
    fun `WHEN parsing XML declaration THEN it is skipped`() {
        val doc = XmlParser.parse("""<?xml version="1.0" encoding="UTF-8"?><root/>""")
        val root = assertNotNull(doc.root)
        assertEquals("root", root.tagName)
        assertTrue(doc.errors.isEmpty())
    }

    @Test
    fun `WHEN parsing DOCTYPE THEN it is skipped`() {
        val doc = XmlParser.parse("""<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"><svg/>""")
        val root = assertNotNull(doc.root)
        assertEquals("svg", root.tagName)
        assertTrue(doc.errors.isEmpty())
    }

    @Test
    fun `WHEN parsing DOCTYPE with internal subset THEN it is skipped`() {
        val doc = XmlParser.parse("""<!DOCTYPE svg [<!ENTITY test "value">]><svg/>""")
        val root = assertNotNull(doc.root)
        assertEquals("svg", root.tagName)
        assertTrue(doc.errors.isEmpty())
    }

    @Test
    fun `WHEN parsing comments THEN they are skipped`() {
        val doc = XmlParser.parse("""
            <svg>
                <!-- This is a comment -->
                <rect/>
            </svg>
        """.trimIndent())

        val root = assertNotNull(doc.root)
        val children = root.getChildElements()
        assertEquals(1, children.size)
        assertEquals("rect", children.first().tagName)
    }

    @Test
    fun `WHEN parsing standard XML entities THEN they are decoded`() {
        val doc = XmlParser.parse("<text>&lt; &gt; &amp; &apos; &quot;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("< > & ' \"", root.getTextContent())
    }

    @Test
    fun `WHEN parsing decimal numeric entities THEN they are decoded`() {
        val doc = XmlParser.parse("<text>&#65;&#66;&#67;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("ABC", root.getTextContent())
    }

    @Test
    fun `WHEN parsing hex numeric entities THEN they are decoded`() {
        val doc = XmlParser.parse("<text>&#x41;&#x42;&#x43;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("ABC", root.getTextContent())
    }

    @Test
    fun `WHEN parsing supplementary unicode entity THEN surrogate pair is produced`() {
        // U+1F600 (😀) requires surrogate pair in UTF-16
        val doc = XmlParser.parse("<text>&#x1F600;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("\uD83D\uDE00", root.getTextContent()) // 😀
    }

    @Test
    fun `WHEN parsing entity in attribute THEN it is decoded`() {
        val doc = XmlParser.parse("""<a href="foo&amp;bar"/>""")
        val root = assertNotNull(doc.root)
        assertEquals("foo&bar", root.getAttribute("href"))
    }

    @Test
    fun `WHEN parsing single quoted attributes THEN they are handled`() {
        val doc = XmlParser.parse("<rect x='10' y='20'/>")
        val root = assertNotNull(doc.root)
        assertEquals("10", root.getAttribute("x"))
        assertEquals("20", root.getAttribute("y"))
    }

    @Test
    fun `WHEN parsing namespaced attributes THEN full name is preserved`() {
        val doc = XmlParser.parse("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"/>""")
        val root = assertNotNull(doc.root)
        assertEquals("http://www.w3.org/2000/svg", root.getAttribute("xmlns"))
        assertEquals("http://www.w3.org/1999/xlink", root.getAttribute("xmlns:xlink"))
    }

    @Test
    fun `WHEN parsing nested elements THEN parent references are set`() {
        val doc = XmlParser.parse("""
            <svg>
                <g>
                    <rect/>
                </g>
            </svg>
        """.trimIndent())

        val rect = assertNotNull(doc.root?.getElementsByTagName("rect")?.firstOrNull())
        val g = assertNotNull(rect.parent)
        val svg = assertNotNull(g.parent)
        assertEquals("g", g.tagName)
        assertEquals("svg", svg.tagName)
        assertNull(svg.parent)
    }

    @Test
    fun `WHEN calling getElementsByTagName THEN nested elements are found`() {
        val doc = XmlParser.parse("""
            <svg>
                <g>
                    <rect id="r1"/>
                    <g>
                        <rect id="r2"/>
                    </g>
                </g>
                <rect id="r3"/>
            </svg>
        """.trimIndent())

        val root = assertNotNull(doc.root)
        val rects = root.getElementsByTagName("rect")
        assertEquals(3, rects.size)
        assertEquals("r1", rects[0].getAttribute("id"))
        assertEquals("r2", rects[1].getAttribute("id"))
        assertEquals("r3", rects[2].getAttribute("id"))
    }

    @Test
    fun `WHEN parsing mixed content THEN all text is concatenated`() {
        val doc = XmlParser.parse("<text>Hello <tspan>World</tspan>!</text>")
        val root = assertNotNull(doc.root)
        assertEquals("Hello World!", root.getTextContent())
    }

    @Test
    fun `WHEN parsing empty document THEN returns null root with error`() {
        val doc = XmlParser.parse("")
        assertNull(doc.root)
        assertTrue(doc.errors.isNotEmpty())
    }

    @Test
    fun `WHEN parsing whitespace only THEN returns null root with error`() {
        val doc = XmlParser.parse("   \n\t  ")
        assertNull(doc.root)
        assertTrue(doc.errors.isNotEmpty())
    }

    @Test
    fun `WHEN parsing mismatched tags THEN error is reported`() {
        val doc = XmlParser.parse("<svg><g></svg></g>")
        assertTrue(doc.errors.any { "Mismatched" in it })
    }

    @Test
    fun `WHEN parsing multiline document THEN line numbers are tracked`() {
        val doc = XmlParser.parse("""
            <svg>
                <rect/>
            </svg>
        """.trimIndent())

        val rect = assertNotNull(doc.root?.getElementsByTagName("rect")?.firstOrNull())
        assertEquals(2, rect.line)
    }

    @Test
    fun `WHEN parsing invalid numeric entity THEN original is returned`() {
        val doc = XmlParser.parse("<text>&#xZZZ;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("&#xZZZ;", root.getTextContent())
    }

    @Test
    fun `WHEN parsing unknown entity THEN original is returned`() {
        val doc = XmlParser.parse("<text>&nbsp;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("&nbsp;", root.getTextContent())
    }

    @Test
    fun `WHEN parsing entity above unicode max THEN fallback is returned`() {
        // Code point 0x110000 is above Unicode max (0x10FFFF)
        val doc = XmlParser.parse("<text>&#x110000;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("&#x110000;", root.getTextContent())
    }

    @Test
    fun `WHEN parsing negative numeric entity THEN fallback is returned`() {
        val doc = XmlParser.parse("<text>&#-1;</text>")
        val root = assertNotNull(doc.root)
        assertEquals("&#-1;", root.getTextContent())
    }

    @Test
    fun `WHEN parsing max valid unicode entity THEN surrogate pair is produced`() {
        // U+10FFFF is the max valid Unicode code point
        val doc = XmlParser.parse("<text>&#x10FFFF;</text>")
        val root = assertNotNull(doc.root)
        assertEquals(2, root.getTextContent().length) // surrogate pair
    }

    @Test
    fun `WHEN parsing real SVG document THEN all parts are accessible`() {
        val svg = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
                <path d="M12 2L2 22h20z" fill="#0000FF"/>
            </svg>
        """.trimIndent()

        val doc = XmlParser.parse(svg)
        val root = assertNotNull(doc.root)
        assertEquals("svg", root.tagName)
        assertEquals("0 0 24 24", root.getAttribute("viewBox"))

        val path = assertNotNull(root.getElementsByTagName("path").firstOrNull())
        assertEquals("M12 2L2 22h20z", path.getAttribute("d"))
        assertEquals("#0000FF", path.getAttribute("fill"))
    }
}
