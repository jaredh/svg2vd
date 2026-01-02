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
import dev.hendry.svg2vd.util.PointF
import dev.hendry.svg2vd.util.TYPE_IDENTITY
import dev.hendry.svg2vd.util.TYPE_TRANSLATION
import dev.hendry.svg2vd.util.formatFloat
import dev.hendry.svg2vd.util.toDegrees
import dev.hendry.svg2vd.util.toRadians
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// SVG Path Commands - Relative
private const val CLOSE = 'z'
private const val MOVE = 'm'
private const val LINE = 'l'
private const val HORIZONTAL = 'h'
private const val VERTICAL = 'v'
private const val CUBIC = 'c'
private const val SMOOTH_CUBIC = 's'
private const val QUADRATIC = 'q'
private const val SMOOTH_QUADRATIC = 't'
private const val ARC = 'a'

// SVG Path Commands - Absolute
private const val CLOSE_ABS = 'Z'
private const val MOVE_ABS = 'M'
private const val LINE_ABS = 'L'
private const val HORIZONTAL_ABS = 'H'
private const val VERTICAL_ABS = 'V'
private const val CUBIC_ABS = 'C'
private const val SMOOTH_CUBIC_ABS = 'S'
private const val QUADRATIC_ABS = 'Q'
private const val SMOOTH_QUADRATIC_ABS = 'T'
private const val ARC_ABS = 'A'

private const val INIT_TYPE = ' '
private const val EXPONENT_LOWER = 'e'
private const val EXPONENT_UPPER = 'E'

private val COMMAND_STEP_MAP = mapOf(
    CLOSE to 2, CLOSE_ABS to 2,
    MOVE to 2, MOVE_ABS to 2,
    LINE to 2, LINE_ABS to 2,
    SMOOTH_QUADRATIC to 2, SMOOTH_QUADRATIC_ABS to 2,
    HORIZONTAL to 1, HORIZONTAL_ABS to 1,
    VERTICAL to 1, VERTICAL_ABS to 1,
    CUBIC to 6, CUBIC_ABS to 6,
    SMOOTH_CUBIC to 4, SMOOTH_CUBIC_ABS to 4,
    QUADRATIC to 4, QUADRATIC_ABS to 4,
    ARC to 7, ARC_ABS to 7
)

/**
 * Represents one segment of the path data, e.g. "l 0,0 1,1".
 */
class PathNode(
    var type: Char,
    var params: FloatArray
) {
    companion object {
        fun hasRelMoveAfterClose(nodes: Array<PathNode>): Boolean {
            var preType = INIT_TYPE
            for (node in nodes) {
                if ((preType == CLOSE || preType == CLOSE_ABS) && node.type == MOVE) {
                    return true
                }
                preType = node.type
            }
            return false
        }

        fun nodeListToString(nodes: Array<PathNode>, formatCoordinate: (Double) -> String): String = buildString {
            for (node in nodes) {
                append(node.type)
                val paramCount = node.params.size
                var implicitLineTo = false
                var lineToType = INIT_TYPE
                if ((node.type == MOVE || node.type == MOVE_ABS) && paramCount > 2) {
                    implicitLineTo = true
                    lineToType = if (node.type == MOVE) LINE else LINE_ABS
                }
                repeat(paramCount) { paramIndex ->
                    if (paramIndex > 0) {
                        append(if (paramIndex % 2 != 0) "," else " ")
                    }
                    if (implicitLineTo && paramIndex == 2) {
                        append(lineToType)
                    }
                    val param = node.params[paramIndex]
                    if (!param.isFinite()) {
                        throw IllegalArgumentException("Invalid number: $param")
                    }
                    append(formatCoordinate(param.toDouble()))
                }
            }
        }

        fun transform(totalTransform: AffineTransform, nodes: Array<PathNode>) {
            val currentPoint = PointF()
            val currentSegmentStartPoint = PointF()
            var previousType = INIT_TYPE
            for (n in nodes) {
                n.transformNode(totalTransform, currentPoint, currentSegmentStartPoint, previousType)
                previousType = n.type
            }
        }
    }

    private fun transformNode(
        totalTransform: AffineTransform,
        currentPoint: PointF,
        currentSegmentStartPoint: PointF,
        previousType: Char
    ) {
        val paramsLen = params.size
        val tempParams = FloatArray(2 * paramsLen)
        var currentX = currentPoint.x
        var currentY = currentPoint.y
        var currentSegmentStartX = currentSegmentStartPoint.x
        var currentSegmentStartY = currentSegmentStartPoint.y

        val step = COMMAND_STEP_MAP[type] ?: 2

        when (type) {
            CLOSE, CLOSE_ABS -> {
                currentX = currentSegmentStartX
                currentY = currentSegmentStartY
            }

            MOVE_ABS -> {
                currentSegmentStartX = params[0]
                currentSegmentStartY = params[1]
                currentX = params[paramsLen - 2]
                currentY = params[paramsLen - 1]
                totalTransform.transform(params, 0, params, 0, paramsLen / 2)
            }

            LINE_ABS, SMOOTH_QUADRATIC_ABS, CUBIC_ABS, SMOOTH_CUBIC_ABS, QUADRATIC_ABS -> {
                currentX = params[paramsLen - 2]
                currentY = params[paramsLen - 1]
                totalTransform.transform(params, 0, params, 0, paramsLen / 2)
            }

            MOVE -> {
                if (previousType == CLOSE || previousType == CLOSE_ABS) {
                    type = MOVE_ABS
                    params[0] += currentSegmentStartX
                    params[1] += currentSegmentStartY
                    currentSegmentStartX = params[0]
                    currentSegmentStartY = params[1]
                    var i = step
                    while (i < paramsLen) {
                        params[i] += params[i - step]
                        params[i + 1] += params[i + 1 - step]
                        i += step
                    }
                    currentX = params[paramsLen - 2]
                    currentY = params[paramsLen - 1]
                    totalTransform.transform(params, 0, params, 0, paramsLen / 2)
                } else {
                    val headLen = 2
                    currentX += params[0]
                    currentY += params[1]
                    currentSegmentStartX = currentX
                    currentSegmentStartY = currentY

                    if (previousType == INIT_TYPE) {
                        totalTransform.transform(params, 0, params, 0, headLen / 2)
                    } else if (!isTranslationOnly(totalTransform)) {
                        deltaTransform(totalTransform, params, 0, headLen)
                    }

                    var i = headLen
                    while (i < paramsLen) {
                        currentX += params[i]
                        currentY += params[i + 1]
                        i += step
                    }

                    if (!isTranslationOnly(totalTransform)) {
                        deltaTransform(totalTransform, params, headLen, paramsLen - headLen)
                    }
                }
            }

            LINE, SMOOTH_QUADRATIC, CUBIC, SMOOTH_CUBIC, QUADRATIC -> {
                var i = 0
                while (i < paramsLen - step + 1) {
                    currentX += params[i + step - 2]
                    currentY += params[i + step - 1]
                    i += step
                }
                if (!isTranslationOnly(totalTransform)) {
                    deltaTransform(totalTransform, params, 0, paramsLen)
                }
            }

            HORIZONTAL_ABS -> {
                type = LINE_ABS
                repeat(paramsLen) { coordIndex ->
                    tempParams[coordIndex * 2] = params[coordIndex]
                    tempParams[coordIndex * 2 + 1] = currentY
                    currentX = params[coordIndex]
                }
                totalTransform.transform(tempParams, 0, tempParams, 0, paramsLen)
                params = tempParams
            }

            VERTICAL_ABS -> {
                type = LINE_ABS
                repeat(paramsLen) { coordIndex ->
                    tempParams[coordIndex * 2] = currentX
                    tempParams[coordIndex * 2 + 1] = params[coordIndex]
                    currentY = params[coordIndex]
                }
                totalTransform.transform(tempParams, 0, tempParams, 0, paramsLen)
                params = tempParams
            }

            HORIZONTAL -> {
                repeat(paramsLen) { coordIndex ->
                    currentX += params[coordIndex]
                    tempParams[coordIndex * 2] = params[coordIndex]
                    tempParams[coordIndex * 2 + 1] = 0f
                }
                if (!isTranslationOnly(totalTransform)) {
                    type = LINE
                    deltaTransform(totalTransform, tempParams, 0, 2 * paramsLen)
                    params = tempParams
                }
            }

            VERTICAL -> {
                repeat(paramsLen) { coordIndex ->
                    tempParams[coordIndex * 2] = 0f
                    tempParams[coordIndex * 2 + 1] = params[coordIndex]
                    currentY += params[coordIndex]
                }
                if (!isTranslationOnly(totalTransform)) {
                    type = LINE
                    deltaTransform(totalTransform, tempParams, 0, 2 * paramsLen)
                    params = tempParams
                }
            }

            ARC_ABS -> {
                var i = 0
                while (i < paramsLen - step + 1) {
                    if (!isTranslationOnly(totalTransform)) {
                        val solver = EllipseSolver(
                            totalTransform,
                            currentX.toDouble(), currentY.toDouble(),
                            params[i].toDouble(), params[i + 1].toDouble(), params[i + 2].toDouble(),
                            params[i + 3].toDouble(), params[i + 4].toDouble(),
                            params[i + 5].toDouble(), params[i + 6].toDouble()
                        )
                        params[i] = solver.majorAxis.toFloat()
                        params[i + 1] = solver.minorAxis.toFloat()
                        params[i + 2] = solver.rotationDegree.toFloat()
                        if (solver.directionChanged) {
                            params[i + 4] = 1 - params[i + 4]
                        }
                    }
                    currentX = params[i + 5]
                    currentY = params[i + 6]
                    totalTransform.transform(params, i + 5, params, i + 5, 1)
                    i += step
                }
            }

            ARC -> {
                var i = 0
                while (i < paramsLen - step + 1) {
                    val oldCurrentX = currentX
                    val oldCurrentY = currentY
                    currentX += params[i + 5]
                    currentY += params[i + 6]
                    if (!isTranslationOnly(totalTransform)) {
                        val solver = EllipseSolver(
                            totalTransform,
                            oldCurrentX.toDouble(), oldCurrentY.toDouble(),
                            params[i].toDouble(), params[i + 1].toDouble(), params[i + 2].toDouble(),
                            params[i + 3].toDouble(), params[i + 4].toDouble(),
                            (oldCurrentX + params[i + 5]).toDouble(),
                            (oldCurrentY + params[i + 6]).toDouble()
                        )
                        deltaTransform(totalTransform, params, i + 5, 2)
                        params[i] = solver.majorAxis.toFloat()
                        params[i + 1] = solver.minorAxis.toFloat()
                        params[i + 2] = solver.rotationDegree.toFloat()
                        if (solver.directionChanged) {
                            params[i + 4] = 1 - params[i + 4]
                        }
                    }
                    i += step
                }
            }
        }

        currentPoint.x = currentX
        currentPoint.y = currentY
        currentSegmentStartPoint.x = currentSegmentStartX
        currentSegmentStartPoint.y = currentSegmentStartY
    }

    private fun isTranslationOnly(transform: AffineTransform): Boolean {
        val type = transform.getType()
        return type == TYPE_IDENTITY || type == TYPE_TRANSLATION
    }

    private fun deltaTransform(
        transform: AffineTransform,
        coordinates: FloatArray,
        offset: Int,
        paramsLen: Int
    ) {
        val doubleArray = DoubleArray(paramsLen) { coordinates[it + offset].toDouble() }
        transform.deltaTransform(doubleArray, 0, doubleArray, 0, paramsLen / 2)
        repeat(paramsLen) { index ->
            coordinates[index + offset] = doubleArray[index].toFloat()
        }
    }

    override fun toString(): String = buildString {
        append(type)
        params.forEachIndexed { index, param ->
            append(if (index % 2 == 0) ' ' else ',')
            append(formatFloat(param))
        }
    }
}

/**
 * Utility for parsing path data.
 */
object PathParser {

    enum class ParseMode { SVG, ANDROID }

    private class ExtractFloatResult {
        var endPosition: Int = 0
        var explicitSeparator: Boolean = false
    }

    fun parsePath(value: String, mode: ParseMode): Array<PathNode> {
        val trimmed = value.trim()
        val list = mutableListOf<PathNode>()

        var start = 0
        var end = 1
        while (end < trimmed.length) {
            end = nextStart(trimmed, end)
            val s = trimmed.substring(start, end)
            val currentCommand = s[0]
            val values = getFloats(s, mode)

            if (start == 0) {
                if (currentCommand != MOVE_ABS && currentCommand != MOVE) {
                    list.add(PathNode(MOVE_ABS, FloatArray(2)))
                }
            }
            list.add(PathNode(currentCommand, values))

            start = end
            end++
        }
        if (end - start == 1 && start < trimmed.length) {
            list.add(PathNode(trimmed[start], FloatArray(0)))
        }
        return list.toTypedArray()
    }

    private fun nextStart(s: String, end: Int): Int {
        var pos = end
        while (pos < s.length) {
            val c = s[pos]
            if (c in 'A'..'Z' && c != EXPONENT_UPPER || c in 'a'..'z' && c != EXPONENT_LOWER) {
                return pos
            }
            pos++
        }
        return pos
    }

    private fun getFloats(s: String, parseMode: ParseMode): FloatArray {
        val command = s[0]
        if (command == CLOSE || command == CLOSE_ABS) {
            return FloatArray(0)
        }

        try {
            val arcCommand = command == ARC || command == ARC_ABS
            val results = FloatArray(s.length)
            var count = 0
            var startPosition = 1
            val result = ExtractFloatResult()

            while (startPosition < s.length) {
                val flagMode = parseMode == ParseMode.SVG && arcCommand &&
                        (count % 7 == 3 || count % 7 == 4)
                extract(s, startPosition, flagMode, result)
                val endPosition = result.endPosition

                if (startPosition < endPosition) {
                    results[count++] = s.substring(startPosition, endPosition).toFloat()
                }

                startPosition = if (result.explicitSeparator) endPosition + 1 else endPosition
            }

            if (arcCommand) {
                var i = 0
                while (i < count - 1) {
                    results[i] = abs(results[i])
                    results[i + 1] = abs(results[i + 1])
                    i += 7
                }
            }

            return results.copyOf(count)
        } catch (e: NumberFormatException) {
            throw RuntimeException("Error in parsing \"$s\"", e)
        }
    }

    private fun extract(s: String, start: Int, flagMode: Boolean, result: ExtractFloatResult) {
        var foundSeparator = false
        result.explicitSeparator = false
        var secondDot = false
        var isExponential = false
        var currentIndex = start

        while (currentIndex < s.length) {
            val isPrevExponential = isExponential
            isExponential = false
            val currentChar = s[currentIndex]

            when (currentChar) {
                ' ', ',' -> {
                    foundSeparator = true
                    result.explicitSeparator = true
                }
                '-' -> {
                    if (currentIndex != start && !isPrevExponential) {
                        foundSeparator = true
                    }
                }
                '.' -> {
                    if (secondDot) {
                        foundSeparator = true
                    } else {
                        secondDot = true
                    }
                }
                EXPONENT_LOWER, EXPONENT_UPPER -> isExponential = true
            }

            if (foundSeparator || flagMode && currentIndex > start) {
                break
            }
            currentIndex++
        }

        result.endPosition = currentIndex
    }
}

/**
 * Solver for ellipse arc transformations.
 */
class EllipseSolver(
    transform: AffineTransform,
    x1: Double,
    y1: Double,
    rx: Double,
    ry: Double,
    rotationDegrees: Double,
    largeArc: Double,
    sweep: Double,
    x2: Double,
    y2: Double
) {
    var majorAxis: Double = rx
        private set
    var minorAxis: Double = ry
        private set
    var rotationDegree: Double = rotationDegrees
        private set
    var directionChanged: Boolean = false
        private set

    init {
        val rotation = toRadians(rotationDegrees)
        val cosR = cos(rotation)
        val sinR = sin(rotation)

        val det = transform.getDeterminant()
        if (det < 0) {
            directionChanged = true
        }

        val scale = sqrt(abs(det))
        majorAxis = rx * scale
        minorAxis = ry * scale

        val newAngle = atan2(
            transform.m10 * cosR + transform.m11 * sinR,
            transform.m00 * cosR + transform.m01 * sinR
        )
        rotationDegree = toDegrees(newAngle)
    }
}
