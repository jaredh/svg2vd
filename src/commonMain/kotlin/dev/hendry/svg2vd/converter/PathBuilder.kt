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

import dev.hendry.svg2vd.util.formatDouble

/**
 * Builds a string for SVG/VectorDrawable path data.
 */
class PathBuilder {
    private val pathData = StringBuilder()

    fun absoluteMoveTo(x: Double, y: Double) = apply {
        pathData.append('M').appendPoint(x, y)
    }

    fun relativeMoveTo(x: Double, y: Double) = apply {
        pathData.append('m').appendPoint(x, y)
    }

    fun absoluteLineTo(x: Double, y: Double) = apply {
        pathData.append('L').appendPoint(x, y)
    }

    fun relativeLineTo(x: Double, y: Double) = apply {
        pathData.append('l').appendPoint(x, y)
    }

    fun absoluteVerticalTo(v: Double) = apply {
        pathData.append('V').append(formatDouble(v))
    }

    fun relativeVerticalTo(v: Double) = apply {
        pathData.append('v').append(formatDouble(v))
    }

    fun absoluteHorizontalTo(h: Double) = apply {
        pathData.append('H').append(formatDouble(h))
    }

    fun relativeHorizontalTo(h: Double) = apply {
        pathData.append('h').append(formatDouble(h))
    }

    fun absoluteCurveTo(
        cp1x: Double,
        cp1y: Double,
        cp2x: Double,
        cp2y: Double,
        x: Double,
        y: Double
    ) = apply {
        pathData.append('C').appendPoint(cp1x, cp1y).append(',')
            .appendPoint(cp2x, cp2y).append(',')
            .appendPoint(x, y)
    }

    fun relativeCurveTo(
        cp1x: Double,
        cp1y: Double,
        cp2x: Double,
        cp2y: Double,
        x: Double,
        y: Double
    ) = apply {
        pathData.append('c').appendPoint(cp1x, cp1y).append(',')
            .appendPoint(cp2x, cp2y).append(',')
            .appendPoint(x, y)
    }

    fun absoluteSmoothCurveTo(cp2x: Double, cp2y: Double, x: Double, y: Double) = apply {
        pathData.append('S').appendPoint(cp2x, cp2y).append(',').appendPoint(x, y)
    }

    fun relativeSmoothCurveTo(cp2x: Double, cp2y: Double, x: Double, y: Double) = apply {
        pathData.append('s').appendPoint(cp2x, cp2y).append(',').appendPoint(x, y)
    }

    fun absoluteQuadraticCurveTo(cp1x: Double, cp1y: Double, x: Double, y: Double) = apply {
        pathData.append('Q').appendPoint(cp1x, cp1y).append(',').appendPoint(x, y)
    }

    fun relativeQuadraticCurveTo(cp1x: Double, cp1y: Double, x: Double, y: Double) = apply {
        pathData.append('q').appendPoint(cp1x, cp1y).append(',').appendPoint(x, y)
    }

    fun absoluteSmoothQuadraticCurveTo(x: Double, y: Double) = apply {
        pathData.append('T').appendPoint(x, y)
    }

    fun relativeSmoothQuadraticCurveTo(x: Double, y: Double) = apply {
        pathData.append('t').appendPoint(x, y)
    }

    // Note: rotation should be Double per SVG spec, but kept as Boolean to match Android SDK source
    // See: com.android.ide.common.vectordrawable.PathBuilder
    fun absoluteArcTo(
        rx: Double,
        ry: Double,
        rotation: Boolean,
        largeArc: Boolean,
        sweep: Boolean,
        x: Double,
        y: Double
    ) = apply {
        pathData.append('A').appendPoint(rx, ry).append(',')
            .append(rotation.toPathFlag()).append(',')
            .append(largeArc.toPathFlag()).append(',')
            .append(sweep.toPathFlag()).append(',')
            .appendPoint(x, y)
    }

    fun relativeArcTo(
        rx: Double,
        ry: Double,
        rotation: Boolean,
        largeArc: Boolean,
        sweep: Boolean,
        x: Double,
        y: Double
    ) = apply {
        pathData.append('a').appendPoint(rx, ry).append(',')
            .append(rotation.toPathFlag()).append(',')
            .append(largeArc.toPathFlag()).append(',')
            .append(sweep.toPathFlag()).append(',')
            .appendPoint(x, y)
    }

    // Note: Z and z are identical per SVG spec, but both kept for Android SDK compatibility
    fun absoluteClose() = apply {
        pathData.append('Z')
    }

    fun relativeClose() = apply {
        pathData.append('z')
    }

    val isEmpty: Boolean get() = pathData.isEmpty()

    override fun toString(): String = pathData.toString()
}

private fun Boolean.toPathFlag(): Char = if (this) '1' else '0'

private fun StringBuilder.appendPoint(x: Double, y: Double): StringBuilder =
    append(formatDouble(x)).append(',').append(formatDouble(y))
