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

import dev.hendry.svg2vd.util.XmlElement
import dev.hendry.svg2vd.util.XmlParser
import kotlin.collections.iterator

/**
 * Kotlin Multiplatform SVG to Vector Drawable converter.
 * This is a port of Android's Svg2Vector without any Java API dependencies.
 */
object Svg2VectorConverter {

    // SVG element tags
    const val SVG_CIRCLE = "circle"
    const val SVG_CLIP_PATH = "clipPath"
    const val SVG_DEFS = "defs"
    const val SVG_ELLIPSE = "ellipse"
    const val SVG_G = "g"
    const val SVG_LINE = "line"
    const val SVG_LINEAR_GRADIENT = "linearGradient"
    const val SVG_MASK = "mask"
    const val SVG_PATH = "path"
    const val SVG_POLYGON = "polygon"
    const val SVG_POLYLINE = "polyline"
    const val SVG_RADIAL_GRADIENT = "radialGradient"
    const val SVG_RECT = "rect"
    const val SVG_STOP = "stop"
    const val SVG_STYLE = "style"
    const val SVG_SVG = "svg"
    const val SVG_SWITCH = "switch"
    const val SVG_SYMBOL = "symbol"
    const val SVG_USE = "use"

    // SVG attribute names
    const val SVG_CLIP = "clip"
    const val SVG_CLIP_RULE = "clip-rule"
    const val SVG_D = "d"
    const val SVG_DISPLAY = "display"
    const val SVG_FILL = "fill"
    const val SVG_FILL_OPACITY = "fill-opacity"
    const val SVG_FILL_RULE = "fill-rule"
    const val SVG_HREF = "href"
    const val SVG_OPACITY = "opacity"
    const val SVG_PAINT_ORDER = "paint-order"
    const val SVG_POINTS = "points"
    const val SVG_STROKE = "stroke"
    const val SVG_STROKE_LINE_CAP = "stroke-linecap"
    const val SVG_STROKE_LINE_JOIN = "stroke-linejoin"
    const val SVG_STROKE_MITER_LIMIT = "stroke-miterlimit"
    const val SVG_STROKE_OPACITY = "stroke-opacity"
    const val SVG_STROKE_WIDTH = "stroke-width"
    const val SVG_STYLE_ATTR = "style"
    const val SVG_XLINK_HREF = "xlink:href"

    // Mapping from SVG attributes to VD attributes
    val presentationMap = mapOf(
        SVG_CLIP to "",
        SVG_CLIP_RULE to "",
        "color" to "",
        SVG_DISPLAY to "",
        SVG_FILL to "android:fillColor",
        SVG_FILL_OPACITY to "android:fillAlpha",
        SVG_FILL_RULE to "android:fillType",
        SVG_OPACITY to "",
        SVG_PAINT_ORDER to "",
        SVG_STROKE to "android:strokeColor",
        SVG_STROKE_LINE_CAP to "android:strokeLineCap",
        SVG_STROKE_LINE_JOIN to "android:strokeLineJoin",
        SVG_STROKE_MITER_LIMIT to "android:strokeMiterLimit",
        SVG_STROKE_OPACITY to "android:strokeAlpha",
        SVG_STROKE_WIDTH to "android:strokeWidth",
        "vector-effect" to ""
    )

    val gradientMap = mapOf(
        "gradientType" to "android:type",
        "startColor" to "android:startColor",
        "endColor" to "android:endColor",
        "centerColor" to "android:centerColor",
        "x1" to "android:startX",
        "y1" to "android:startY",
        "x2" to "android:endX",
        "y2" to "android:endY",
        "cx" to "android:centerX",
        "cy" to "android:centerY",
        "r" to "android:gradientRadius",
        "spreadMethod" to "android:tileMode"
    )

    /**
     * Converts SVG content to a Vector Drawable XML string.
     *
     * @param svgContent The SVG file content as a string
     * @param fileName Optional filename for error reporting
     * @return A result containing the VD content and any error messages
     */
    fun convert(svgContent: String, fileName: String = ""): ConversionResult {
        val document = XmlParser.parse(svgContent)
        if (document.root == null) {
            return ConversionResult(
                success = false,
                content = null,
                errorMessage = "Failed to parse SVG: ${document.errors.joinToString()}"
            )
        }

        val svgTree = SvgTree()
        svgTree.fileName = fileName

        return try {
            parse(document.root, svgTree)
            svgTree.flatten()
            svgTree.validate()

            val errorMessage = svgTree.errorMessage
            if (errorMessage.isNotEmpty()) {
                ConversionResult(
                    success = false,
                    content = null,
                    errorMessage = errorMessage
                )
            } else {
                val output = svgTree.writeXml()
                ConversionResult(
                    success = true,
                    content = output,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            ConversionResult(
                success = false,
                content = null,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    private fun parse(root: XmlElement, svgTree: SvgTree) {
        if (root.tagName != SVG_SVG) {
            svgTree.logError("Root element must be <svg>", root)
            return
        }

        svgTree.parseDimension(root)

        if (svgTree.viewBox == null) {
            svgTree.logError("Missing viewBox", root)
            return
        }

        val rootGroup = SvgGroupNode(svgTree, root, "root")
        svgTree.root = rootGroup

        traverseSvgAndExtract(svgTree, root, rootGroup)
        resolveGradientReferences(svgTree)
        resolveUseNodes(svgTree)
        handleClipPaths(svgTree)
        applyStyleClasses(svgTree)
    }

    private fun traverseSvgAndExtract(svgTree: SvgTree, element: XmlElement, currentGroup: SvgGroupNode) {
        for (child in element.getChildElements()) {
            val tagName = child.tagName
            val id = child.getAttribute("id")

            when (tagName) {
                SVG_PATH -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processPathNode(leaf, child, svgTree)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_RECT -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processRectNode(leaf, child, svgTree)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_CIRCLE -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processCircleNode(leaf, child, svgTree)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_ELLIPSE -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processEllipseNode(leaf, child, svgTree)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_LINE -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processLineNode(leaf, child, svgTree)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_POLYGON -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processPolyNode(leaf, child, svgTree, true)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_POLYLINE -> {
                    val leaf = SvgLeafNode(svgTree, child, tagName)
                    processPolyNode(leaf, child, svgTree, false)
                    maybeAddId(id, leaf, svgTree)
                    processStyleAndClip(child, leaf, currentGroup, svgTree)
                    currentGroup.addChild(leaf)
                }
                SVG_G, SVG_SWITCH -> {
                    val newGroup = SvgGroupNode(svgTree, child, tagName)
                    maybeAddId(id, newGroup, svgTree)
                    processStyleAndClip(child, newGroup, currentGroup, svgTree)
                    currentGroup.addChild(newGroup)
                    traverseSvgAndExtract(svgTree, child, newGroup)
                }
                SVG_SYMBOL -> {
                    val newGroup = SvgGroupNode(svgTree, child, tagName)
                    maybeAddId(id, newGroup, svgTree)
                    traverseSvgAndExtract(svgTree, child, newGroup)
                }
                SVG_DEFS -> {
                    traverseSvgAndExtract(svgTree, child, currentGroup)
                }
                SVG_CLIP_PATH, SVG_MASK -> {
                    val clipPathNode = SvgClipPathNode(svgTree, child, tagName)
                    maybeAddId(id, clipPathNode, svgTree)
                    traverseSvgAndExtract(svgTree, child, clipPathNode)
                }
                SVG_USE -> {
                    val useGroup = SvgGroupNode(svgTree, child, tagName)
                    maybeAddId(id, useGroup, svgTree)
                    processStyleAndClip(child, useGroup, currentGroup, svgTree)
                    currentGroup.addChild(useGroup)
                    svgTree.addToPendingUseSet(useGroup)
                }
                SVG_LINEAR_GRADIENT -> {
                    val gradientNode = SvgGradientNode(svgTree, child, tagName)
                    gradientNode.vdAttributesMap["gradientType"] = "linear"
                    processGradientNode(gradientNode, child, svgTree)
                    maybeAddId(id, gradientNode, svgTree)
                }
                SVG_RADIAL_GRADIENT -> {
                    val gradientNode = SvgGradientNode(svgTree, child, tagName)
                    gradientNode.vdAttributesMap["gradientType"] = "radial"
                    processGradientNode(gradientNode, child, svgTree)
                    maybeAddId(id, gradientNode, svgTree)
                }
                SVG_STYLE -> {
                    processStyleElement(child, svgTree)
                }
            }
        }
    }

    private fun maybeAddId(id: String, node: SvgNode, svgTree: SvgTree) {
        if (id.isNotEmpty()) {
            svgTree.addIdToMap(id, node)
        }
    }

    private fun processPathNode(leaf: SvgLeafNode, element: XmlElement, svgTree: SvgTree) {
        val d = element.getAttribute(SVG_D)
        if (d.isNotEmpty()) {
            leaf.pathData = d
            svgTree.hasLeafNode = true
        }
        addStyleToPath(leaf, element)
    }

    private fun processRectNode(leaf: SvgLeafNode, element: XmlElement, svgTree: SvgTree) {
        val x = parseFloatOrDefault(element.getAttribute("x"), 0f).toDouble()
        val y = parseFloatOrDefault(element.getAttribute("y"), 0f).toDouble()
        val width = parseFloatOrDefault(element.getAttribute("width"), 0f).toDouble()
        val height = parseFloatOrDefault(element.getAttribute("height"), 0f).toDouble()
        var rx = element.getAttribute("rx").toDoubleOrNull()
        var ry = element.getAttribute("ry").toDoubleOrNull()

        if (rx == null && ry != null) rx = ry
        if (ry == null && rx != null) ry = rx
        rx = rx ?: 0.0
        ry = ry ?: 0.0

        val builder = PathBuilder()
        if (rx > 0 || ry > 0) {
            // Rounded rectangle
            builder.absoluteMoveTo(x + rx, y)
            builder.absoluteHorizontalTo(x + width - rx)
            builder.absoluteArcTo(rx, ry,
                rotation = false,
                largeArc = false,
                sweep = true,
                x = x + width,
                y = y + ry
            )
            builder.absoluteVerticalTo(y + height - ry)
            builder.absoluteArcTo(rx, ry,
                rotation = false,
                largeArc = false,
                sweep = true,
                x = x + width - rx,
                y = y + height
            )
            builder.absoluteHorizontalTo(x + rx)
            builder.absoluteArcTo(rx, ry,
                rotation = false,
                largeArc = false,
                sweep = true,
                x = x,
                y = y + height - ry
            )
            builder.absoluteVerticalTo(y + ry)
            builder.absoluteArcTo(rx, ry,
                rotation = false,
                largeArc = false,
                sweep = true,
                x = x + rx,
                y = y
            )
        } else {
            builder.absoluteMoveTo(x, y)
            builder.absoluteHorizontalTo(x + width)
            builder.absoluteVerticalTo(y + height)
            builder.absoluteHorizontalTo(x)
            builder.absoluteVerticalTo(y)
        }
        builder.absoluteClose()

        leaf.pathData = builder.toString()
        svgTree.hasLeafNode = true
        addStyleToPath(leaf, element)
    }

    private fun processCircleNode(leaf: SvgLeafNode, element: XmlElement, svgTree: SvgTree) {
        val cx = parseFloatOrDefault(element.getAttribute("cx"), 0f).toDouble()
        val cy = parseFloatOrDefault(element.getAttribute("cy"), 0f).toDouble()
        val r = parseFloatOrDefault(element.getAttribute("r"), 0f).toDouble()

        // Match Android SDK format: "M cx,cy m -r,0 a r,r 0 1,1 2r,0 a r,r 0 1,1 -2r,0"
        val builder = PathBuilder()
        builder.absoluteMoveTo(cx, cy)
        builder.relativeMoveTo(-r, 0.0)
        builder.relativeArcTo(r, r,
            rotation = false,
            largeArc = true,
            sweep = true,
            x = 2 * r,
            y = 0.0
        )
        builder.relativeArcTo(r, r,
            rotation = false,
            largeArc = true,
            sweep = true,
            x = -2 * r,
            y = 0.0
        )

        leaf.pathData = builder.toString()
        svgTree.hasLeafNode = true
        addStyleToPath(leaf, element)
    }

    private fun processEllipseNode(leaf: SvgLeafNode, element: XmlElement, svgTree: SvgTree) {
        val cx = parseFloatOrDefault(element.getAttribute("cx"), 0f).toDouble()
        val cy = parseFloatOrDefault(element.getAttribute("cy"), 0f).toDouble()
        val rx = parseFloatOrDefault(element.getAttribute("rx"), 0f).toDouble()
        val ry = parseFloatOrDefault(element.getAttribute("ry"), 0f).toDouble()

        // Match Android SDK format: "M cx-rx,cy a rx,ry 0 1,0 2rx,0 a rx,ry 0 1,0 -2rx,0 z"
        val builder = PathBuilder()
        builder.absoluteMoveTo(cx - rx, cy)
        builder.relativeArcTo(rx, ry,
            rotation = false,
            largeArc = true,
            sweep = false,
            x = 2 * rx,
            y = 0.0
        )
        builder.relativeArcTo(rx, ry,
            rotation = false,
            largeArc = true,
            sweep = false,
            x = -2 * rx,
            y = 0.0
        )
        builder.relativeClose()

        leaf.pathData = builder.toString()
        svgTree.hasLeafNode = true
        addStyleToPath(leaf, element)
    }

    private fun processLineNode(leaf: SvgLeafNode, element: XmlElement, svgTree: SvgTree) {
        val x1 = parseFloatOrDefault(element.getAttribute("x1"), 0f).toDouble()
        val y1 = parseFloatOrDefault(element.getAttribute("y1"), 0f).toDouble()
        val x2 = parseFloatOrDefault(element.getAttribute("x2"), 0f).toDouble()
        val y2 = parseFloatOrDefault(element.getAttribute("y2"), 0f).toDouble()

        val builder = PathBuilder()
        builder.absoluteMoveTo(x1, y1)
        builder.absoluteLineTo(x2, y2)

        leaf.pathData = builder.toString()
        svgTree.hasLeafNode = true
        addStyleToPath(leaf, element)
    }

    private fun processPolyNode(leaf: SvgLeafNode, element: XmlElement, svgTree: SvgTree, polygon: Boolean) {
        val points = element.getAttribute(SVG_POINTS)
        val parts = points.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }

        val builder = PathBuilder()
        for (i in parts.indices step 2) {
            if (i + 1 >= parts.size) break
            val x = parts[i].toDoubleOrNull() ?: continue
            val y = parts[i + 1].toDoubleOrNull() ?: continue
            if (i == 0) {
                builder.absoluteMoveTo(x, y)
            } else {
                builder.absoluteLineTo(x, y)
            }
        }
        if (polygon) {
            builder.absoluteClose()
        }

        leaf.pathData = builder.toString()
        svgTree.hasLeafNode = true
        addStyleToPath(leaf, element)
    }

    private fun processGradientNode(gradientNode: SvgGradientNode, element: XmlElement, svgTree: SvgTree) {
        svgTree.hasGradient = true

        for ((name, value) in element.attributes) {
            if (name in gradientMap || name == "gradientUnits" || name == "gradientTransform") {
                gradientNode.vdAttributesMap[name] = value
            }
        }

        for (stopElement in element.getChildElements()) {
            if (stopElement.tagName == SVG_STOP) {
                val offset = stopElement.getAttribute("offset")
                var color = stopElement.getAttribute("stop-color")
                var opacity = stopElement.getAttribute("stop-opacity")

                val style = stopElement.getAttribute(SVG_STYLE_ATTR)
                if (style.isNotEmpty()) {
                    for (part in style.split(";")) {
                        val kv = part.split(":").map { it.trim() }
                        if (kv.size == 2) {
                            when (kv[0]) {
                                "stop-color" -> color = kv[1]
                                "stop-opacity" -> opacity = kv[1]
                            }
                        }
                    }
                }

                if (color.isEmpty()) color = "#000000"
                if (opacity.isEmpty()) opacity = "1"

                // Convert offset to 0-1 range
                val normalizedOffset = if (offset.endsWith("%")) {
                    (offset.dropLast(1).toDoubleOrNull() ?: 0.0) / 100
                } else {
                    offset.toDoubleOrNull() ?: 0.0
                }

                gradientNode.addGradientStop(color, normalizedOffset.toString(), opacity)
            }
        }

        // Check for href reference
        val href = element.getAttribute(SVG_HREF).ifEmpty { element.getAttribute(SVG_XLINK_HREF) }
        if (href.isNotEmpty()) {
            svgTree.addToPendingGradientRefSet(gradientNode)
        }
    }

    private fun processStyleElement(element: XmlElement, svgTree: SvgTree) {
        val content = element.getTextContent()
        parseStyleContent(content, svgTree)
    }

    private fun parseStyleContent(content: String, svgTree: SvgTree) {
        val regex = Regex("\\.([a-zA-Z][a-zA-Z0-9_-]*)\\s*\\{([^}]*)\\}")
        for (match in regex.findAll(content)) {
            val className = match.groupValues[1]
            val styleContent = match.groupValues[2].trim()
            svgTree.addStyleClassToTree(className, styleContent)
        }
    }

    private fun processStyleAndClip(element: XmlElement, child: SvgNode, currentGroup: SvgGroupNode, svgTree: SvgTree) {
        // Handle style attribute
        val style = element.getAttribute(SVG_STYLE_ATTR)
        if (style.isNotEmpty()) {
            addStyleToPath(child, style)
        }

        // Handle class attribute
        val classAttr = element.getAttribute("class")
        if (classAttr.isNotEmpty()) {
            for (className in classAttr.split("\\s+".toRegex())) {
                svgTree.addAffectedNodeToStyleClass(className, child)
            }
        }

        // Handle clip-path reference
        val clipPath = element.getAttribute("clip-path")
        if (clipPath.isNotEmpty() && clipPath.startsWith("url(#") && clipPath.endsWith(")")) {
            val clipId = clipPath.substring(5, clipPath.length - 1)
            svgTree.addClipPathAffectedNode(child, currentGroup, clipId)
        }
    }

    private fun addStyleToPath(node: SvgNode, element: XmlElement) {
        val style = element.getAttribute(SVG_STYLE_ATTR)
        if (style.isNotEmpty()) {
            addStyleToPath(node, style)
        }
    }

    private fun addStyleToPath(node: SvgNode, styleContent: String) {
        for (part in styleContent.split(";")) {
            val kv = part.split(":").map { it.trim() }
            if (kv.size == 2) {
                val name = kv[0]
                val value = kv[1]
                if (name in presentationMap) {
                    node.fillPresentationAttributes(name, value)
                }
            }
        }
    }

    private fun resolveGradientReferences(svgTree: SvgTree) {
        val pendingSet = svgTree.getPendingGradientRefSet().toMutableSet()
        var previousSize = pendingSet.size + 1
        while (pendingSet.isNotEmpty() && pendingSet.size < previousSize) {
            previousSize = pendingSet.size
            val iterator = pendingSet.iterator()
            while (iterator.hasNext()) {
                val gradientNode = iterator.next()
                if (gradientNode.resolveHref(svgTree)) {
                    iterator.remove()
                }
            }
        }
    }

    private fun resolveUseNodes(svgTree: SvgTree) {
        val pendingSet = svgTree.getPendingUseSet().toMutableSet()
        var previousSize = pendingSet.size + 1
        while (pendingSet.isNotEmpty() && pendingSet.size < previousSize) {
            previousSize = pendingSet.size
            val iterator = pendingSet.iterator()
            while (iterator.hasNext()) {
                val useGroup = iterator.next()
                if (useGroup.resolveHref(svgTree)) {
                    useGroup.handleUse()
                    iterator.remove()
                }
            }
        }
    }

    private fun handleClipPaths(svgTree: SvgTree) {
        for ((node, value) in svgTree.getClipPathAffectedNodesSet()) {
            val (parentGroup, clipId) = value
            val clipPathNode = svgTree.getSvgNodeFromId(clipId)
            if (clipPathNode is SvgClipPathNode) {
                parentGroup.replaceChild(node, clipPathNode)
                clipPathNode.addAffectedNode(node)
                clipPathNode.setClipPathNodeAttributes()
            }
        }
    }

    private fun applyStyleClasses(svgTree: SvgTree) {
        for ((className, nodes) in svgTree.getStyleAffectedNodes()) {
            val styleContent = svgTree.getStyleClassAttr(className) ?: continue
            for (node in nodes) {
                addStyleToPath(node, styleContent)
            }
        }
    }
}

data class ConversionResult(
    val success: Boolean,
    val content: String?,
    val errorMessage: String?
)
