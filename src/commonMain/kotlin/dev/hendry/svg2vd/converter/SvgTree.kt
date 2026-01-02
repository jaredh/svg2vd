/*
 * Copyright (C) 2015 The Android Open Source Project
 * Kotlin Multiplatform port Copyright (C) 2025 Jared Hendry
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.hendry.svg2vd.converter

import dev.hendry.svg2vd.util.AffineTransform
import dev.hendry.svg2vd.util.XmlElement
import dev.hendry.svg2vd.util.formatDouble
import kotlin.collections.iterator
import kotlin.math.max

/**
 * Represents the SVG file as an internal data structure tree.
 */
class SvgTree {

    companion object {
        private const val HEAD = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\""
        private const val AAPT_BOUND = "xmlns:aapt=\"http://schemas.android.com/aapt\""
        private val UNIT_REGEX = Regex("em|ex|px|in|cm|mm|pt|pc")
        private val WHITESPACE_OR_COMMA_REGEX = Regex("[\\s,]+")
        const val SVG_WIDTH = "width"
        const val SVG_HEIGHT = "height"
        const val SVG_VIEW_BOX = "viewBox"
        const val INDENT_UNIT = "  "
        const val CONTINUATION_INDENT = "    "
    }

    var width: Float = -1f
        private set
    var height: Float = -1f
        private set
    private val rootTransform = AffineTransform()
    var viewBox: FloatArray? = null
        private set

    var root: SvgGroupNode? = null
    var fileName: String = ""

    private val logMessages = mutableListOf<LogMessage>()
    var hasLeafNode: Boolean = false
    var hasGradient: Boolean = false

    private val idMap = mutableMapOf<String, SvgNode>()
    private val ignoredIds = mutableSetOf<String>()
    private val pendingUseGroupSet = mutableSetOf<SvgGroupNode>()
    private val pendingGradientRefSet = mutableSetOf<SvgGradientNode>()
    private val clipPathAffectedNodes = mutableMapOf<SvgNode, Pair<SvgGroupNode, String>>()
    private val styleAffectedNodes = mutableMapOf<String, MutableSet<SvgNode>>()
    private val styleClassAttributeMap = mutableMapOf<String, String>()

    private var coordinateFormat: CoordinateFormat? = null

    enum class SvgLogLevel { ERROR, WARNING }

    private data class LogMessage(
        val level: SvgLogLevel,
        val line: Int,
        val message: String
    ) : Comparable<LogMessage> {
        val formattedMessage: String
            get() = "${level.name}${if (line == 0) "" else " @ line $line"}: $message"

        override fun compareTo(other: LogMessage): Int =
            compareValuesBy(this, other, { it.level }, { it.line }, { it.message })
    }

    val viewportWidth: Float get() = viewBox?.get(2) ?: -1f
    val viewportHeight: Float get() = viewBox?.get(3) ?: -1f

    fun flatten() {
        root?.flatten(AffineTransform())
    }

    fun validate() {
        root?.validate()
        if (logMessages.isEmpty() && !hasLeafNode) {
            logError("No vector content found", null)
        }
    }

    fun normalize() {
        val vb = viewBox ?: return
        rootTransform.preConcatenate(
            AffineTransform(
                1.0,
                0.0,
                0.0,
                1.0,
                -vb[0].toDouble(),
                -vb[1].toDouble()
            )
        )
        transform(rootTransform)
    }

    private fun transform(rootTransform: AffineTransform) {
        root?.transformIfNeeded(rootTransform)
    }

    fun parseDimension(element: XmlElement) {
        var widthType = SizeType.PIXEL
        var heightType = SizeType.PIXEL

        for ((name, value) in element.attributes) {
            val trimmedValue = value.trim()
            var subStringSize = trimmedValue.length
            var currentType = SizeType.PIXEL

            val unit = trimmedValue.takeLast(2)
            if (unit.matches(UNIT_REGEX)) {
                subStringSize -= 2
            } else if (trimmedValue.endsWith("%")) {
                subStringSize -= 1
                currentType = SizeType.PERCENTAGE
            }

            when (name) {
                SVG_WIDTH -> {
                    width = trimmedValue.take(subStringSize).toFloatOrNull() ?: -1f
                    widthType = currentType
                }
                SVG_HEIGHT -> {
                    height = trimmedValue.take(subStringSize).toFloatOrNull() ?: -1f
                    heightType = currentType
                }
                SVG_VIEW_BOX -> {
                    val vb = FloatArray(4)
                    val parts = trimmedValue.split(WHITESPACE_OR_COMMA_REGEX)
                    parts.take(4).forEachIndexed { j, part ->
                        vb[j] = part.toFloatOrNull() ?: 0f
                    }
                    viewBox = vb
                }
            }
        }

        // Set up viewBox from width/height if not present
        val vb = viewBox
        if (vb == null && width > 0 && height > 0) {
            viewBox = floatArrayOf(0f, 0f, width, height)
        } else if (vb != null && (width < 0 || height < 0)) {
            width = vb[2]
            height = vb[3]
        }

        // Handle percentage dimensions
        viewBox?.let { box ->
            if (widthType == SizeType.PERCENTAGE && width > 0) {
                width = box[2] * width / 100
            }
            if (heightType == SizeType.PERCENTAGE && height > 0) {
                height = box[3] * height / 100
            }
        }
    }

    fun parseXValue(value: String): Double = parseCoordinateOrLength(value, viewportWidth.toDouble())
    fun parseYValue(value: String): Double = parseCoordinateOrLength(value, viewportHeight.toDouble())

    private fun parseCoordinateOrLength(value: String, percentageBase: Double): Double =
        if (value.endsWith("%")) {
            value.removeSuffix("%").toDouble() / 100 * percentageBase
        } else {
            value.toDouble()
        }

    fun addIdToMap(id: String, svgNode: SvgNode) {
        idMap[id] = svgNode
    }

    fun getSvgNodeFromId(id: String): SvgNode? = idMap[id]

    fun addToPendingUseSet(useGroup: SvgGroupNode) {
        pendingUseGroupSet.add(useGroup)
    }

    fun getPendingUseSet(): Set<SvgGroupNode> = pendingUseGroupSet

    fun addToPendingGradientRefSet(node: SvgGradientNode) {
        pendingGradientRefSet.add(node)
    }

    fun getPendingGradientRefSet(): Set<SvgGradientNode> = pendingGradientRefSet

    fun addIgnoredId(id: String) {
        ignoredIds.add(id)
    }

    fun isIdIgnored(id: String): Boolean = id in ignoredIds

    fun addClipPathAffectedNode(child: SvgNode, currentGroup: SvgGroupNode, clipPathName: String) {
        clipPathAffectedNodes[child] = currentGroup to clipPathName
    }

    fun getClipPathAffectedNodesSet(): Set<Map.Entry<SvgNode, Pair<SvgGroupNode, String>>> =
        clipPathAffectedNodes.entries

    fun addAffectedNodeToStyleClass(className: String, child: SvgNode) {
        styleAffectedNodes.getOrPut(className) { mutableSetOf() }.add(child)
    }

    fun addStyleClassToTree(className: String, attributes: String) {
        styleClassAttributeMap[className] = attributes
    }

    fun getStyleClassAttr(className: String): String? = styleClassAttributeMap[className]

    fun getStyleAffectedNodes(): Set<Map.Entry<String, Set<SvgNode>>> = styleAffectedNodes.entries

    fun findParent(node: SvgNode): SvgGroupNode? = root?.findParent(node)

    fun formatCoordinate(coordinate: Double): String {
        return trimInsignificantZeros(getCoordinateFormat().format(coordinate))
    }

    private fun getCoordinateFormat(): CoordinateFormat =
        coordinateFormat ?: CoordinateFormat(max(viewportHeight, viewportWidth)).also {
            coordinateFormat = it
        }

    fun logError(s: String, element: XmlElement?) {
        logErrorLine(s, element, SvgLogLevel.ERROR)
    }

    fun logWarning(s: String, element: XmlElement?) {
        logErrorLine(s, element, SvgLogLevel.WARNING)
    }

    private fun logErrorLine(s: String, element: XmlElement?, level: SvgLogLevel) {
        require(s.isNotEmpty())
        val line = element?.line ?: 0
        logMessages.add(LogMessage(level, line, s))
    }

    val errorMessage: String
        get() = logMessages.sorted().joinToString("\n") { it.formattedMessage }

    fun writeXml(): String = buildString {
        val r = root ?: throw IllegalStateException("SvgTree is not fully initialized")

        append(HEAD).append('\n')

        if (hasGradient) {
            append(CONTINUATION_INDENT).append(AAPT_BOUND).append('\n')
        }

        append(CONTINUATION_INDENT)
            .append("android:width=\"")
            .append(formatCoordinate(width.toDouble()))
            .append("dp\"").append('\n')

        append(CONTINUATION_INDENT)
            .append("android:height=\"")
            .append(formatCoordinate(height.toDouble()))
            .append("dp\"").append('\n')

        append(CONTINUATION_INDENT)
            .append("android:viewportWidth=\"")
            .append(formatCoordinate(viewportWidth.toDouble()))
            .append("\"").append('\n')

        append(CONTINUATION_INDENT)
            .append("android:viewportHeight=\"")
            .append(formatCoordinate(viewportHeight.toDouble()))
            .append("\">").append('\n')

        normalize()
        r.writeXml(this, INDENT_UNIT)

        append("</vector>").append('\n')
    }

    private enum class SizeType { PIXEL, PERCENTAGE }
}

/**
 * Formats coordinates with appropriate precision.
 */
class CoordinateFormat(maxDimension: Float) {
    private val decimalPlaces: Int = when {
        maxDimension >= 1000 -> 1
        maxDimension >= 100 -> 2
        maxDimension >= 10 -> 3
        else -> 4
    }

    fun format(value: Double): String {
        return formatDouble(value, decimalPlaces)
    }
}
