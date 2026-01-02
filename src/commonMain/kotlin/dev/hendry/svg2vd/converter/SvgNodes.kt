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
import dev.hendry.svg2vd.util.TYPE_GENERAL_SCALE
import dev.hendry.svg2vd.util.TYPE_MASK_SCALE
import dev.hendry.svg2vd.util.XmlElement
import dev.hendry.svg2vd.util.formatDouble
import dev.hendry.svg2vd.util.formatHex
import dev.hendry.svg2vd.util.toRadians
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

private const val TRANSFORM_MATRIX = "matrix"
private const val TRANSFORM_TRANSLATE = "translate"
private const val TRANSFORM_SCALE = "scale"
private const val TRANSFORM_ROTATE = "rotate"
private const val TRANSFORM_SKEW_X = "skewx"
private const val TRANSFORM_SKEW_Y = "skewy"

private const val FILL_RULE_NONZERO = "nonzero"
private const val FILL_RULE_EVENODD = "evenodd"
private const val VD_FILL_RULE_NONZERO = "nonZero"
private const val VD_FILL_RULE_EVENODD = "evenOdd"

/**
 * Base class for SVG tree nodes.
 */
sealed class SvgNode(
    val tree: SvgTree,
    val element: XmlElement,
    val name: String?
) {
    val vdAttributesMap = mutableMapOf<String, String>()
    var strokeBeforeFill = false
    var localTransform = AffineTransform()
    var stackedTransform = AffineTransform()

    init {
        for ((attrName, attrValue) in element.attributes) {
            if (attrName in Svg2VectorConverter.presentationMap) {
                fillPresentationAttributesInternal(attrName, attrValue)
            }
            if (attrName == "transform") {
                parseLocalTransform(attrValue)
            }
        }
    }

    protected fun parseLocalTransform(nodeValue: String) {
        val value = nodeValue.replace(",", " ")
        val matrices = value.split("[()]".toRegex())
        var i = 0
        while (i < matrices.size - 1) {
            val parsed = parseOneTransform(matrices[i].trim(), matrices[i + 1].trim())
            if (parsed != null) {
                localTransform.concatenate(parsed)
            }
            i += 2
        }
    }

    private fun parseOneTransform(type: String, data: String): AffineTransform? {
        val numbers = getNumbers(data) ?: return null
        val numLength = numbers.size
        val parsedTransform = AffineTransform()

        when (type.lowercase()) {
            TRANSFORM_MATRIX -> {
                if (numLength != 6) return null
                parsedTransform.setTransform(
                    numbers[0].toDouble(), numbers[1].toDouble(),
                    numbers[2].toDouble(), numbers[3].toDouble(),
                    numbers[4].toDouble(), numbers[5].toDouble()
                )
            }
            TRANSFORM_TRANSLATE -> {
                if (numLength != 1 && numLength != 2) return null
                parsedTransform.translate(
                    numbers[0].toDouble(),
                    if (numLength == 2) numbers[1].toDouble() else 0.0
                )
            }
            TRANSFORM_SCALE -> {
                if (numLength != 1 && numLength != 2) return null
                parsedTransform.scale(
                    numbers[0].toDouble(),
                    numbers[if (numLength == 2) 1 else 0].toDouble()
                )
            }
            TRANSFORM_ROTATE -> {
                if (numLength != 1 && numLength != 3) return null
                parsedTransform.rotate(
                    toRadians(numbers[0].toDouble()),
                    if (numLength == 3) numbers[1].toDouble() else 0.0,
                    if (numLength == 3) numbers[2].toDouble() else 0.0
                )
            }
            TRANSFORM_SKEW_X -> {
                if (numLength != 1) return null
                parsedTransform.shear(tan(toRadians(numbers[0].toDouble())), 0.0)
            }
            TRANSFORM_SKEW_Y -> {
                if (numLength != 1) return null
                parsedTransform.shear(0.0, tan(toRadians(numbers[0].toDouble())))
            }
        }
        return parsedTransform
    }

    private fun getNumbers(data: String): FloatArray? {
        val parts = data.split("\\s+".toRegex())
        if (parts.isEmpty()) return null
        return parts.mapNotNull { it.toFloatOrNull() }.toFloatArray()
    }

    private fun fillPresentationAttributesInternal(name: String, value: String) {
        var processedValue = value
        when (name) {
            Svg2VectorConverter.SVG_PAINT_ORDER -> {
                val order = value.split("\\s+".toRegex())
                val strokePos = order.indexOf(Svg2VectorConverter.SVG_STROKE)
                val fillPos = order.indexOf(Svg2VectorConverter.SVG_FILL)
                strokeBeforeFill = strokePos in 0 until fillPos
                return
            }
            Svg2VectorConverter.SVG_FILL_RULE, Svg2VectorConverter.SVG_CLIP_RULE -> {
                processedValue = when (value) {
                    FILL_RULE_NONZERO -> VD_FILL_RULE_NONZERO
                    FILL_RULE_EVENODD -> VD_FILL_RULE_EVENODD
                    else -> value
                }
            }
            Svg2VectorConverter.SVG_STROKE_WIDTH -> {
                if (value == "0") {
                    vdAttributesMap.remove(Svg2VectorConverter.SVG_STROKE)
                }
            }
        }

        if (processedValue.startsWith("url(")) {
            if (name != Svg2VectorConverter.SVG_FILL && name != Svg2VectorConverter.SVG_STROKE) {
                logError("Unsupported URL value: $processedValue")
                return
            }
        }
        if (processedValue.isNotEmpty()) {
            vdAttributesMap[name] = processedValue
        }
    }

    open fun fillPresentationAttributes(name: String, value: String) {
        fillPresentationAttributesInternal(name, value)
    }

    fun fillEmptyAttributes(parentAttributes: Map<String, String>) {
        for ((name, value) in parentAttributes) {
            if (name !in vdAttributesMap) {
                vdAttributesMap[name] = value
            }
        }
    }

    fun getAttributeValue(attribute: String): String = element.getAttribute(attribute)

    protected fun getHrefId(): String =
        element.getAttribute(Svg2VectorConverter.SVG_HREF)
            .ifEmpty { element.getAttribute(Svg2VectorConverter.SVG_XLINK_HREF) }
            .removePrefix("#")

    protected fun colorSvg2Vd(svgColor: String, errorFallbackColor: String): String? {
        return try {
            SvgColor.colorSvg2Vd(svgColor)
        } catch (e: IllegalArgumentException) {
            logError("Unsupported color format \"$svgColor\"")
            errorFallbackColor
        }
    }

    protected fun logError(s: String) {
        tree.logError(s, element)
    }

    protected fun logWarning(s: String) {
        tree.logWarning(s, element)
    }

    abstract val isGroupNode: Boolean
    abstract fun transformIfNeeded(rootTransform: AffineTransform)
    abstract fun flatten(transform: AffineTransform)
    abstract fun writeXml(writer: StringBuilder, indent: String)
    abstract fun deepCopy(): SvgNode
    open fun validate() {}
}

/**
 * Represents an SVG leaf element (path, rect, circle, etc.).
 */
class SvgLeafNode(
    tree: SvgTree,
    element: XmlElement,
    name: String?
) : SvgNode(tree, element, name) {

    var pathData: String? = null
    private var fillGradientNode: SvgGradientNode? = null
    private var strokeGradientNode: SvgGradientNode? = null

    override val isGroupNode: Boolean = false

    override fun deepCopy(): SvgLeafNode {
        val newNode = SvgLeafNode(tree, element, name)
        newNode.fillEmptyAttributes(vdAttributesMap)
        newNode.localTransform = AffineTransform(localTransform)
        newNode.pathData = pathData
        return newNode
    }

    override fun transformIfNeeded(rootTransform: AffineTransform) {
        if (pathData.isNullOrEmpty()) return

        val nodes = PathParser.parsePath(pathData!!, PathParser.ParseMode.SVG)
        stackedTransform.preConcatenate(rootTransform)
        val needsConvert = PathNode.hasRelMoveAfterClose(nodes)
        if (!stackedTransform.isIdentity() || needsConvert) {
            PathNode.transform(stackedTransform, nodes)
        }
        pathData = PathNode.nodeListToString(nodes) { tree.formatCoordinate(it) }
    }

    override fun flatten(transform: AffineTransform) {
        stackedTransform.setTransform(transform)
        stackedTransform.concatenate(localTransform)

        if (vdAttributesMap["vector-effect"] != "non-scaling-stroke" &&
            (stackedTransform.getType() and TYPE_MASK_SCALE) != 0) {
            vdAttributesMap[Svg2VectorConverter.SVG_STROKE_WIDTH]?.toDoubleOrNull()?.let { width ->
                val determinant = stackedTransform.getDeterminant()
                if (determinant != 0.0) {
                    val scaledWidth = width * sqrt(abs(determinant))
                    vdAttributesMap[Svg2VectorConverter.SVG_STROKE_WIDTH] = tree.formatCoordinate(scaledWidth)
                }
                if ((stackedTransform.getType() and TYPE_GENERAL_SCALE) != 0) {
                    logWarning("Scaling of the stroke width is approximate")
                }
            }
        }
    }

    override fun writeXml(writer: StringBuilder, indent: String) {
        if (pathData.isNullOrEmpty()) return

        if (strokeBeforeFill) {
            writePathElementWithSuppressed(writer, Svg2VectorConverter.SVG_FILL, indent)
            writePathElementWithSuppressed(writer, Svg2VectorConverter.SVG_STROKE, indent)
        } else {
            writePathElement(writer, indent)
        }
    }

    private fun writePathElementWithSuppressed(writer: StringBuilder, attribute: String, indent: String) {
        val savedValue = vdAttributesMap.put(attribute, "#00000000")
        writePathElement(writer, indent)
        if (savedValue == null) {
            vdAttributesMap.remove(attribute)
        } else {
            vdAttributesMap[attribute] = savedValue
        }
    }

    private fun writePathElement(writer: StringBuilder, indent: String) {
        val fillColor = vdAttributesMap[Svg2VectorConverter.SVG_FILL]
        val strokeColor = vdAttributesMap[Svg2VectorConverter.SVG_STROKE]
        val emptyFill = fillColor == "none" || fillColor == "#00000000"
        val emptyStroke = strokeColor == null || strokeColor == "none"
        if (emptyFill && emptyStroke) return

        parsePathOpacity()

        writer.append(indent).append("<path").append('\n')

        if (fillColor == null && fillGradientNode == null) {
            writer.append(indent).append(CONTINUATION_INDENT)
                .append("android:fillColor=\"#FF000000\"").append('\n')
        }
        if (!emptyStroke && Svg2VectorConverter.SVG_STROKE_WIDTH !in vdAttributesMap && strokeGradientNode == null) {
            writer.append(indent).append(CONTINUATION_INDENT)
                .append("android:strokeWidth=\"1\"").append('\n')
        }

        writer.append(indent).append(CONTINUATION_INDENT)
            .append("android:pathData=\"").append(pathData).append("\"")

        writeAttributeValues(writer, indent)

        if (!hasGradient) {
            writer.append('/')
        }
        writer.append('>').append('\n')

        fillGradientNode?.writeXml(writer, indent + INDENT_UNIT)
        strokeGradientNode?.writeXml(writer, indent + INDENT_UNIT)

        if (hasGradient) {
            writer.append(indent).append("</path>").append('\n')
        }
    }

    private fun writeAttributeValues(writer: StringBuilder, indent: String) {
        for (name in PATH_ATTRIBUTE_ORDER) {
            val svgValue = vdAttributesMap[name] ?: continue
            val attribute = Svg2VectorConverter.presentationMap[name] ?: continue
            if (attribute.isEmpty()) continue

            val trimmedValue = svgValue.trim()
            var vdValue = colorSvg2Vd(trimmedValue, "#000000")

            if (vdValue == null) {
                if (name == Svg2VectorConverter.SVG_FILL || name == Svg2VectorConverter.SVG_STROKE) {
                    val gradientNode = getGradientNode(trimmedValue)
                    if (gradientNode != null) {
                        val copy = gradientNode.deepCopy()
                        copy.svgLeafNode = this
                        if (name == Svg2VectorConverter.SVG_FILL) {
                            copy.gradientUsage = GradientUsage.FILL
                            fillGradientNode = copy
                        } else {
                            copy.gradientUsage = GradientUsage.STROKE
                            strokeGradientNode = copy
                        }
                        continue
                    }
                }

                vdValue = trimmedValue.removeSuffix("px").trim()
            }

            writer.append('\n').append(indent).append(CONTINUATION_INDENT)
                .append(attribute).append("=\"").append(vdValue).append("\"")
        }
    }

    private fun getGradientNode(svgValue: String): SvgGradientNode? =
        svgValue.takeIf { it.startsWith("url(#") && it.endsWith(")") }
            ?.let { tree.getSvgNodeFromId(it.substring(5, it.length - 1)) as? SvgGradientNode }

    private fun parsePathOpacity() {
        val opacity = getOpacityValueFromMap(Svg2VectorConverter.SVG_OPACITY)
        val fillOpacity = getOpacityValueFromMap(Svg2VectorConverter.SVG_FILL_OPACITY)
        val strokeOpacity = getOpacityValueFromMap(Svg2VectorConverter.SVG_STROKE_OPACITY)
        putOpacityValueToMap(Svg2VectorConverter.SVG_FILL_OPACITY, fillOpacity * opacity)
        putOpacityValueToMap(Svg2VectorConverter.SVG_STROKE_OPACITY, strokeOpacity * opacity)
        vdAttributesMap.remove(Svg2VectorConverter.SVG_OPACITY)
    }

    private fun getOpacityValueFromMap(attributeName: String): Double {
        val opacity = vdAttributesMap[attributeName] ?: return 1.0
        val result = if (opacity.endsWith("%")) {
            opacity.removeSuffix("%").toDoubleOrNull()?.div(100.0)
        } else {
            opacity.toDoubleOrNull()
        }
        return (result ?: 1.0).coerceIn(0.0, 1.0)
    }

    private fun putOpacityValueToMap(attributeName: String, opacity: Double) {
        val attributeValue = formatDouble(opacity)
        if (attributeValue == "1") {
            vdAttributesMap.remove(attributeName)
        } else {
            vdAttributesMap[attributeName] = attributeValue
        }
    }

    private val hasGradient: Boolean get() = fillGradientNode != null || strokeGradientNode != null

    companion object {
        const val INDENT_UNIT = "  "
        const val CONTINUATION_INDENT = "    "

        // Canonical order for writing path attributes to match Android SDK's Svg2Vector output.
        // The Android SDK uses HashMap which iterates based on hash bucket order, not insertion order.
        // We use LinkedHashMap (mutableMapOf) which preserves insertion order, so we must explicitly
        // iterate in the order that matches HashMap's iteration for these specific keys.
        // This order was determined empirically by comparing output from the Android SDK.
        private val PATH_ATTRIBUTE_ORDER = listOf(
            Svg2VectorConverter.SVG_STROKE_OPACITY,
            Svg2VectorConverter.SVG_STROKE_WIDTH,
            Svg2VectorConverter.SVG_STROKE_LINE_CAP,
            Svg2VectorConverter.SVG_STROKE_LINE_JOIN,
            Svg2VectorConverter.SVG_STROKE_MITER_LIMIT,
            Svg2VectorConverter.SVG_STROKE,
            Svg2VectorConverter.SVG_FILL_RULE,
            Svg2VectorConverter.SVG_FILL_OPACITY,
            Svg2VectorConverter.SVG_FILL,
        )
    }
}

/**
 * Represents an SVG group element.
 */
open class SvgGroupNode(
    tree: SvgTree,
    element: XmlElement,
    name: String?
) : SvgNode(tree, element, name) {

    val children = mutableListOf<SvgNode>()
    var useReferenceNode: SvgNode? = null

    override val isGroupNode: Boolean = true

    override fun deepCopy(): SvgGroupNode {
        val newInstance = SvgGroupNode(tree, element, name)
        newInstance.fillEmptyAttributes(vdAttributesMap)
        newInstance.localTransform = AffineTransform(localTransform)
        for (child in children) {
            newInstance.addChild(child.deepCopy())
        }
        return newInstance
    }

    fun addChild(child: SvgNode) {
        children.add(child)
        child.fillEmptyAttributes(vdAttributesMap)
    }

    fun replaceChild(oldChild: SvgNode, newChild: SvgNode) {
        val index = children.indexOf(oldChild)
        if (index < 0) {
            throw IllegalArgumentException("The child being replaced doesn't belong to this group")
        }
        children[index] = newChild
    }

    fun resolveHref(svgTree: SvgTree): Boolean {
        val id = getHrefId()
        useReferenceNode = if (id.isEmpty()) null else svgTree.getSvgNodeFromId(id)
        if (useReferenceNode == null) {
            if (id.isEmpty() || !svgTree.isIdIgnored(id)) {
                svgTree.logError("Referenced id not found", element)
            }
        } else {
            if (useReferenceNode in svgTree.getPendingUseSet()) {
                return false
            }
        }
        return true
    }

    fun handleUse() {
        val refNode = useReferenceNode ?: return
        val copiedNode = refNode.deepCopy()
        addChild(copiedNode)
        for ((key, value) in vdAttributesMap) {
            copiedNode.fillPresentationAttributes(key, value)
        }
        fillEmptyAttributes(vdAttributesMap)

        val x = parseFloatOrDefault(element.getAttribute("x"), 0f)
        val y = parseFloatOrDefault(element.getAttribute("y"), 0f)
        transformIfNeeded(AffineTransform(1.0, 0.0, 0.0, 1.0, x.toDouble(), y.toDouble()))
    }

    fun findParent(node: SvgNode): SvgGroupNode? {
        if (node in children) return this
        return children.filterIsInstance<SvgGroupNode>()
            .firstNotNullOfOrNull { it.findParent(node) }
    }

    override fun transformIfNeeded(rootTransform: AffineTransform) {
        for (child in children) {
            child.transformIfNeeded(rootTransform)
        }
    }

    override fun flatten(transform: AffineTransform) {
        for (child in children) {
            stackedTransform.setTransform(transform)
            stackedTransform.concatenate(localTransform)
            child.flatten(stackedTransform)
        }
    }

    override fun validate() {
        for (child in children) {
            child.validate()
        }
    }

    override fun writeXml(writer: StringBuilder, indent: String) {
        for (child in children) {
            child.writeXml(writer, indent)
        }
    }

    override fun fillPresentationAttributes(name: String, value: String) {
        super.fillPresentationAttributes(name, value)
        for (child in children) {
            if (name !in child.vdAttributesMap) {
                child.fillPresentationAttributes(name, value)
            }
        }
    }
}

/**
 * Represents a clip-path element.
 */
class SvgClipPathNode(
    tree: SvgTree,
    element: XmlElement,
    name: String?
) : SvgGroupNode(tree, element, name) {

    val affectedNodes = mutableListOf<SvgNode>()

    override fun deepCopy(): SvgClipPathNode {
        val newInstance = SvgClipPathNode(tree, element, name)
        newInstance.fillEmptyAttributes(vdAttributesMap)
        newInstance.localTransform = AffineTransform(localTransform)
        for (child in children) {
            newInstance.addChild(child.deepCopy())
        }
        for (node in affectedNodes) {
            newInstance.addAffectedNode(node)
        }
        return newInstance
    }

    fun addAffectedNode(child: SvgNode) {
        affectedNodes.add(child)
        child.fillEmptyAttributes(vdAttributesMap)
    }

    override fun flatten(transform: AffineTransform) {
        for (child in children) {
            stackedTransform.setTransform(transform)
            stackedTransform.concatenate(localTransform)
            child.flatten(stackedTransform)
        }

        stackedTransform.setTransform(transform)
        for (child in affectedNodes) {
            child.flatten(stackedTransform)
        }
        stackedTransform.concatenate(localTransform)
    }

    override fun validate() {
        super.validate()
        for (child in affectedNodes) {
            child.validate()
        }

        if (element.tagName == Svg2VectorConverter.SVG_MASK && !isWhiteFill()) {
            logError("Semitransparent mask cannot be represented by a vector drawable")
        }
    }

    private fun isWhiteFill(): Boolean {
        val fillColor = vdAttributesMap["fill"] ?: return false
        val converted = colorSvg2Vd(fillColor, "#000") ?: return false
        return SvgColor.parseColorValue(converted) == 0xFFFFFFFF.toInt()
    }

    override fun transformIfNeeded(rootTransform: AffineTransform) {
        for (child in children + affectedNodes) {
            child.transformIfNeeded(rootTransform)
        }
    }

    override fun writeXml(writer: StringBuilder, indent: String) {
        writer.append(indent).append("<group>").append('\n')
        val incrementedIndent = indent + SvgLeafNode.INDENT_UNIT

        val clipPaths = mutableMapOf<ClipRule, MutableList<String>>()
        collectClipPaths(this, clipPaths)

        for ((clipRule, pathDataList) in clipPaths) {
            writer.append(incrementedIndent).append("<clip-path").append('\n')
            writer.append(incrementedIndent).append(SvgLeafNode.INDENT_UNIT)
                .append(SvgLeafNode.INDENT_UNIT)
                .append("android:pathData=\"")
            for (i in pathDataList.indices) {
                val path = pathDataList[i]
                if (i > 0 && !path.startsWith("M")) {
                    writer.append("M 0,0")
                }
                writer.append(path)
            }
            writer.append("\"")
            if (clipRule == ClipRule.EVEN_ODD) {
                writer.append('\n').append(incrementedIndent)
                    .append(SvgLeafNode.INDENT_UNIT).append(SvgLeafNode.INDENT_UNIT)
                    .append("android:fillType=\"evenOdd\"")
            }
            writer.append("/>").append('\n')
        }

        for (child in affectedNodes) {
            child.writeXml(writer, incrementedIndent)
        }
        writer.append(indent).append("</group>").append('\n')
    }

    private fun collectClipPaths(node: SvgNode, clipPaths: MutableMap<ClipRule, MutableList<String>>) {
        when (node) {
            is SvgLeafNode -> {
                val pathData = node.pathData
                if (!pathData.isNullOrEmpty()) {
                    val clipRule = if (node.vdAttributesMap[Svg2VectorConverter.SVG_CLIP_RULE] == VD_FILL_RULE_EVENODD) {
                        ClipRule.EVEN_ODD
                    } else {
                        ClipRule.NON_ZERO
                    }
                    clipPaths.getOrPut(clipRule) { mutableListOf() }.add(pathData)
                }
            }
            is SvgGroupNode -> {
                for (child in node.children) {
                    collectClipPaths(child, clipPaths)
                }
            }
            is SvgGradientNode -> {
                // Gradients don't contribute to clip paths
            }
        }
    }

    fun setClipPathNodeAttributes() {
        for (node in affectedNodes) {
            localTransform.concatenate(node.localTransform)
        }
    }
}

enum class ClipRule { NON_ZERO, EVEN_ODD }

/**
 * Represents a gradient element.
 */
class SvgGradientNode(
    tree: SvgTree,
    element: XmlElement,
    name: String?
) : SvgNode(tree, element, name) {

    val gradientStops = mutableListOf<GradientStop>()
    var svgLeafNode: SvgLeafNode? = null
    var gradientUsage: GradientUsage = GradientUsage.FILL

    override val isGroupNode: Boolean = false

    override fun deepCopy(): SvgGradientNode {
        val newInstance = SvgGradientNode(tree, element, name)
        newInstance.fillEmptyAttributes(vdAttributesMap)
        newInstance.localTransform = AffineTransform(localTransform)
        if (newInstance.gradientStops.isEmpty()) {
            for (stop in gradientStops) {
                newInstance.addGradientStop(stop.color, stop.offset, stop.opacity)
            }
        }
        return newInstance
    }

    fun resolveHref(svgTree: SvgTree): Boolean {
        val id = getHrefId()
        val referencedNode = if (id.isEmpty()) null else svgTree.getSvgNodeFromId(id)
        return when {
            referencedNode is SvgGradientNode -> {
                if (referencedNode in svgTree.getPendingGradientRefSet()) {
                    false
                } else {
                    copyFrom(referencedNode)
                    true
                }
            }
            referencedNode == null -> {
                if (id.isEmpty() || !svgTree.isIdIgnored(id)) {
                    svgTree.logError("Referenced id not found", element)
                }
                true
            }
            else -> {
                svgTree.logError("Referenced element is not a gradient", element)
                true
            }
        }
    }

    private fun copyFrom(from: SvgGradientNode) {
        fillEmptyAttributes(from.vdAttributesMap)
        localTransform = AffineTransform(from.localTransform)
        if (gradientStops.isEmpty()) {
            for (stop in from.gradientStops) {
                addGradientStop(stop.color, stop.offset, stop.opacity)
            }
        }
    }

    fun addGradientStop(color: String, offset: String, opacity: String) {
        val stop = GradientStop(color, offset)
        stop.opacity = opacity
        gradientStops.add(stop)
    }

    override fun transformIfNeeded(rootTransform: AffineTransform) {
        // Transformation is done in writeXml
    }

    override fun flatten(transform: AffineTransform) {
        stackedTransform.setTransform(transform)
        stackedTransform.concatenate(localTransform)
    }

    override fun writeXml(writer: StringBuilder, indent: String) {
        if (gradientStops.isEmpty()) {
            logError("Gradient has no stop info")
            return
        }

        // Simplified gradient output - using bounding box calculations would require path parsing
        val gradientUnit = vdAttributesMap["gradientUnits"]
        val isUserSpaceOnUse = gradientUnit == "userSpaceOnUse"

        writer.append(indent)
        if (gradientUsage == GradientUsage.FILL) {
            writer.append("<aapt:attr name=\"android:fillColor\">")
        } else {
            writer.append("<aapt:attr name=\"android:strokeColor\">")
        }
        writer.append('\n')

        writer.append(indent).append(SvgLeafNode.INDENT_UNIT).append("<gradient ")

        val gradientType = vdAttributesMap["gradientType"] ?: "linear"

        for ((svgAttr, gradientAttr) in Svg2VectorConverter.gradientMap) {
            if (gradientAttr.isEmpty()) continue
            val svgValue = vdAttributesMap[svgAttr]?.trim() ?: continue

            val vdValue = when (svgAttr) {
                "spreadMethod" -> when (svgValue) {
                    "pad" -> "clamp"
                    "reflect" -> "mirror"
                    "repeat" -> "repeat"
                    else -> "clamp"
                }
                else -> colorSvg2Vd(svgValue, "#000000") ?: svgValue
            }

            writer.append('\n').append(indent).append(SvgLeafNode.INDENT_UNIT)
                .append(SvgLeafNode.CONTINUATION_INDENT)
                .append(gradientAttr).append("=\"").append(vdValue).append("\"")
        }

        writer.append('>').append('\n')

        writeGradientStops(writer, indent + SvgLeafNode.INDENT_UNIT + SvgLeafNode.INDENT_UNIT)

        writer.append(indent).append(SvgLeafNode.INDENT_UNIT).append("</gradient>").append('\n')
        writer.append(indent).append("</aapt:attr>").append('\n')
    }

    private fun writeGradientStops(writer: StringBuilder, indent: String) {
        for (stop in gradientStops) {
            val color = stop.color
            val opacity = stop.opacity.toFloatOrNull() ?: 1f
            val colorInt = SvgColor.applyAlpha(SvgColor.parseColorValue(color), opacity)
            val colorHex = "#${formatHex(colorInt, 8)}"

            writer.append(indent)
                .append("<item android:offset=\"")
                .append(trimInsignificantZeros(stop.offset))
                .append("\" android:color=\"")
                .append(colorHex)
                .append("\"/>").append('\n')

            if (gradientStops.size == 1) {
                logWarning("Gradient has only one color stop")
                writer.append(indent)
                    .append("<item android:offset=\"1\" android:color=\"")
                    .append(colorHex)
                    .append("\"/>").append('\n')
            }
        }
    }
}

enum class GradientUsage { FILL, STROKE }

data class GradientStop(
    val color: String,
    val offset: String
) {
    var opacity: String = "1"
}

fun parseFloatOrDefault(value: String, defaultValue: Float): Float =
    value.toFloatOrNull() ?: defaultValue

fun trimInsignificantZeros(value: String): String {
    if (!value.contains('.')) return value
    return value.trimEnd('0').trimEnd('.')
}
