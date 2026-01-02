package dev.hendry.svg2vd.converter

import com.android.ide.common.vectordrawable.Svg2Vector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Parameterized test that compares the Kotlin Multiplatform converter
 * with the Android SDK Svg2Vector converter using real Material Icons SVG files.
 */
@RunWith(Parameterized::class)
class MaterialIconsComparisonTest(
    private val svgFile: File,
    private val testName: String
) {

    companion object {
        private val MATERIAL_ICONS_DIR = File("src/jvmTest/resources/material-icons/svg")

        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun data(): Collection<Array<Any>> {
            val svgFiles = MATERIAL_ICONS_DIR.walkTopDown()
                .filter { it.isFile && it.extension == "svg" }
                .map { file ->
                    val relativePath = file.relativeTo(MATERIAL_ICONS_DIR).path
                    arrayOf<Any>(file, relativePath)
                }
                .toList()

            require(svgFiles.isNotEmpty()) {
                "No SVG files found in ${MATERIAL_ICONS_DIR.absolutePath}"
            }

            return svgFiles
        }
    }

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
    private fun convertWithExperimental(svgContent: String, fileName: String): String {
        val result = Svg2VectorConverter.convert(svgContent, fileName)
        require(result.success) { "Experimental conversion failed: ${result.errorMessage}" }
        return result.content ?: error("Conversion succeeded but content is null")
    }

    /**
     * Normalizes VectorDrawable XML for comparison by:
     * - Removing whitespace differences
     * - Sorting attributes for consistent comparison
     */
    private fun normalizeXml(xml: String): String {
        return xml.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /**
     * Extracts attributes from a VectorDrawable XML for comparison.
     * Returns a map of attribute name to value.
     */
    private fun extractAttributes(xml: String, tagName: String): Map<String, String> {
        val pattern = "<$tagName\\s+([^>]+)".toRegex()
        val match = pattern.find(xml) ?: return emptyMap()

        val attrPattern = """android:(\w+)="([^"]+)"""".toRegex()
        return attrPattern.findAll(match.groupValues[1])
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `compare vector drawable output`() {
        val svgContent = svgFile.readText()

        val androidSdkOutput = convertWithAndroidSdk(svgContent)
        val experimentalOutput = convertWithExperimental(svgContent, svgFile.name)

        // Both should produce valid vector drawables
        assertTrue(
            "Android SDK output should contain <vector tag for $testName",
            androidSdkOutput.contains("<vector")
        )
        assertTrue(
            "Experimental output should contain <vector tag for $testName",
            experimentalOutput.contains("<vector")
        )

        // Compare vector attributes
        val sdkVectorAttrs = extractAttributes(androidSdkOutput, "vector")
        val expVectorAttrs = extractAttributes(experimentalOutput, "vector")

        // Viewport dimensions must match
        assertEquals(
            "viewportWidth should match for $testName",
            sdkVectorAttrs["viewportWidth"],
            expVectorAttrs["viewportWidth"]
        )
        assertEquals(
            "viewportHeight should match for $testName",
            sdkVectorAttrs["viewportHeight"],
            expVectorAttrs["viewportHeight"]
        )

        // Both should have path elements
        assertTrue(
            "Android SDK output should contain <path for $testName",
            androidSdkOutput.contains("<path")
        )
        assertTrue(
            "Experimental output should contain <path for $testName",
            experimentalOutput.contains("<path")
        )

        // Normalize and compare the full output
        val normalizedSdk = normalizeXml(androidSdkOutput)
        val normalizedExp = normalizeXml(experimentalOutput)

        assertEquals(
            "Vector drawable output should match for $testName",
            normalizedSdk,
            normalizedExp
        )
    }
}
