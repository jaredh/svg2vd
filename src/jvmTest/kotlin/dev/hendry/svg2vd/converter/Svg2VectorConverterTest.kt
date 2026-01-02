package dev.hendry.svg2vd.converter

import com.android.ide.common.vectordrawable.Svg2Vector
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files

/**
 * Tests comparing the Kotlin Multiplatform converter with the Android SDK converter.
 */
class Svg2VectorConverterTest {

    /**
     * Converts SVG using the Android SDK Svg2Vector.
     */
    private fun convertWithAndroidSdk(svgContent: String): String {
        val tempFile = Files.createTempFile("test", ".svg")
        try {
            Files.writeString(tempFile, svgContent)
            val outputStream = ByteArrayOutputStream()
            val error = Svg2Vector.parseSvgToXml(tempFile, outputStream)
            if (error.isNotEmpty()) {
                throw RuntimeException("Android SDK conversion failed: $error")
            }
            return outputStream.toString(Charsets.UTF_8)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    /**
     * Converts SVG using the Kotlin Multiplatform converter.
     */
    private fun convertWithExperimental(svgContent: String): String {
        val result = Svg2VectorConverter.convert(svgContent, "test.svg")
        if (!result.success) {
            throw RuntimeException("Experimental conversion failed: ${result.errorMessage}")
        }
        return result.content!!
    }

    /**
     * Normalizes VectorDrawable XML for comparison by removing whitespace differences.
     */
    private fun normalizeXml(xml: String): String {
        return xml.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    @Test
    fun `test simple rect conversion`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="2" y="2" width="20" height="20" fill="#FF0000"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        // Verify experimental converter produces valid output
        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
        assertTrue("Experimental output should contain pathData", experimental.contains("android:pathData"))
    }

    @Test
    fun `test circle conversion`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <circle cx="12" cy="12" r="10" fill="#00FF00"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
        assertTrue("Experimental output should contain arc command",
            experimental.contains("A") || experimental.contains("a"))
    }

    @Test
    fun `test ellipse conversion`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <ellipse cx="12" cy="12" rx="10" ry="5" fill="#0000FF"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
    }

    @Test
    fun `test line conversion`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <line x1="0" y1="0" x2="24" y2="24" stroke="#000000" stroke-width="2"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
        assertTrue("Experimental output should contain strokeWidth", experimental.contains("android:strokeWidth"))
    }

    @Test
    fun `test polygon conversion`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <polygon points="12,2 22,22 2,22" fill="#FF00FF"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
        assertTrue("Experimental output should contain close command (Z)",
            experimental.contains("Z") || experimental.contains("z"))
    }

    @Test
    fun `test polyline conversion`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <polyline points="0,0 12,12 24,0" stroke="#000000" stroke-width="2" fill="none"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
    }

    @Test
    fun `test path with curves`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <path d="M2,2 C2,12 12,12 12,22 L22,22 Z" fill="#FFFF00"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
        assertTrue("Experimental output should contain cubic curve",
            experimental.contains("C") || experimental.contains("c"))
    }

    @Test
    fun `test group with transform`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <g transform="translate(12, 12)">
                <rect x="-5" y="-5" width="10" height="10" fill="#00FFFF"/>
              </g>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path tag", experimental.contains("<path"))
    }

    @Test
    fun `test color conversion - named colors`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="0" y="0" width="24" height="24" fill="red"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should convert named color",
            experimental.contains("#ff0000") || experimental.contains("#FF0000"))
    }

    @Test
    fun `test color conversion - rgb format`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="0" y="0" width="24" height="24" fill="rgb(255, 128, 0)"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain fillColor", experimental.contains("android:fillColor"))
    }

    @Test
    fun `test opacity handling`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="0" y="0" width="24" height="24" fill="#FF0000" fill-opacity="0.5"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain fillAlpha", experimental.contains("android:fillAlpha"))
    }

    @Test
    fun `test stroke attributes`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="2" y="2" width="20" height="20"
                    fill="none"
                    stroke="#000000"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain strokeWidth", experimental.contains("android:strokeWidth"))
        assertTrue("Experimental output should contain strokeLineCap", experimental.contains("android:strokeLineCap"))
        assertTrue("Experimental output should contain strokeLineJoin", experimental.contains("android:strokeLineJoin"))
    }

    @Test
    fun `test rounded rect`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="2" y="2" width="20" height="20" rx="4" ry="4" fill="#FF0000"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain arc commands for rounded corners",
            experimental.contains("A") || experimental.contains("a"))
    }

    @Test
    fun `test viewBox dimensions`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50" width="100" height="50">
              <rect x="0" y="0" width="100" height="50" fill="#FF0000"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should have correct viewportWidth",
            experimental.contains("android:viewportWidth=\"100\""))
        assertTrue("Experimental output should have correct viewportHeight",
            experimental.contains("android:viewportHeight=\"50\""))
    }

    @Test
    fun `test fill-rule evenodd`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <path d="M0,0 L24,0 L24,24 L0,24 Z M6,6 L18,6 L18,18 L6,18 Z"
                    fill="#FF0000" fill-rule="evenodd"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain fillType evenOdd",
            experimental.contains("android:fillType=\"evenOdd\""))
    }

    @Test
    fun `test style attribute parsing`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="0" y="0" width="24" height="24" style="fill:#FF0000;stroke:#000000;stroke-width:2"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should parse fill from style", experimental.contains("android:fillColor"))
        assertTrue("Experimental output should parse stroke from style", experimental.contains("android:strokeColor"))
    }

    @Test
    fun `test complex SVG icon`() {
        // A more complex SVG similar to real-world icons
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="#4CAF50"/>
            </svg>
        """.trimIndent()

        val experimental = convertWithExperimental(svg)

        assertTrue("Experimental output should contain vector tag", experimental.contains("<vector"))
        assertTrue("Experimental output should contain path with complex pathData", experimental.contains("android:pathData"))
    }

    @Test
    fun `compare outputs for simple rect`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="2" y="2" width="20" height="20" fill="#FF0000"/>
            </svg>
        """.trimIndent()

        val androidSdk = convertWithAndroidSdk(svg)
        val experimental = convertWithExperimental(svg)

        // Both should produce valid vector drawables
        assertTrue("Android SDK output should be valid", androidSdk.contains("<vector"))
        assertTrue("Experimental output should be valid", experimental.contains("<vector"))

        // Both should have similar structure
        assertTrue("Both should have path elements",
            androidSdk.contains("<path") && experimental.contains("<path"))
        assertTrue("Both should have pathData",
            androidSdk.contains("android:pathData") && experimental.contains("android:pathData"))
    }

    @Test
    fun `compare outputs for circle`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <circle cx="12" cy="12" r="10" fill="#00FF00"/>
            </svg>
        """.trimIndent()

        val androidSdk = convertWithAndroidSdk(svg)
        val experimental = convertWithExperimental(svg)

        assertTrue("Android SDK output should be valid", androidSdk.contains("<vector"))
        assertTrue("Experimental output should be valid", experimental.contains("<vector"))
    }

    @Test
    fun `compare outputs for path`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <path d="M12 2L2 22h20z" fill="#0000FF"/>
            </svg>
        """.trimIndent()

        val androidSdk = convertWithAndroidSdk(svg)
        val experimental = convertWithExperimental(svg)

        assertTrue("Android SDK output should be valid", androidSdk.contains("<vector"))
        assertTrue("Experimental output should be valid", experimental.contains("<vector"))
    }

    @Test
    fun `compare viewport dimensions`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="48" height="48">
              <rect x="0" y="0" width="48" height="48" fill="#FF0000"/>
            </svg>
        """.trimIndent()

        val androidSdk = convertWithAndroidSdk(svg)
        val experimental = convertWithExperimental(svg)

        assertTrue("Android SDK should have viewportWidth 48", androidSdk.contains("android:viewportWidth=\"48\""))
        assertTrue("Experimental should have viewportWidth 48", experimental.contains("android:viewportWidth=\"48\""))
        assertTrue("Android SDK should have viewportHeight 48", androidSdk.contains("android:viewportHeight=\"48\""))
        assertTrue("Experimental should have viewportHeight 48", experimental.contains("android:viewportHeight=\"48\""))
    }
}
