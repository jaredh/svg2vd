/*
 * Copyright (C) 2018 The Android Open Source Project
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

import kotlin.math.roundToInt

/**
 * Methods for converting SVG color values to vector drawable format.
 */
object SvgColor {

    /**
     * Color table from [Recognized color keyword names](https://www.w3.org/TR/SVG11/types.html#ColorKeywords)
     */
    private val colorMap = mapOf(
        "aliceblue" to "#f0f8ff",
        "antiquewhite" to "#faebd7",
        "aqua" to "#00ffff",
        "aquamarine" to "#7fffd4",
        "azure" to "#f0ffff",
        "beige" to "#f5f5dc",
        "bisque" to "#ffe4c4",
        "black" to "#000000",
        "blanchedalmond" to "#ffebcd",
        "blue" to "#0000ff",
        "blueviolet" to "#8a2be2",
        "brown" to "#a52a2a",
        "burlywood" to "#deb887",
        "cadetblue" to "#5f9ea0",
        "chartreuse" to "#7fff00",
        "chocolate" to "#d2691e",
        "coral" to "#ff7f50",
        "cornflowerblue" to "#6495ed",
        "cornsilk" to "#fff8dc",
        "crimson" to "#dc143c",
        "cyan" to "#00ffff",
        "darkblue" to "#00008b",
        "darkcyan" to "#008b8b",
        "darkgoldenrod" to "#b8860b",
        "darkgray" to "#a9a9a9",
        "darkgrey" to "#a9a9a9",
        "darkgreen" to "#006400",
        "darkkhaki" to "#bdb76b",
        "darkmagenta" to "#8b008b",
        "darkolivegreen" to "#556b2f",
        "darkorange" to "#ff8c00",
        "darkorchid" to "#9932cc",
        "darkred" to "#8b0000",
        "darksalmon" to "#e9967a",
        "darkseagreen" to "#8fbc8f",
        "darkslateblue" to "#483d8b",
        "darkslategray" to "#2f4f4f",
        "darkslategrey" to "#2f4f4f",
        "darkturquoise" to "#00ced1",
        "darkviolet" to "#9400d3",
        "deeppink" to "#ff1493",
        "deepskyblue" to "#00bfff",
        "dimgray" to "#696969",
        "dimgrey" to "#696969",
        "dodgerblue" to "#1e90ff",
        "firebrick" to "#b22222",
        "floralwhite" to "#fffaf0",
        "forestgreen" to "#228b22",
        "fuchsia" to "#ff00ff",
        "gainsboro" to "#dcdcdc",
        "ghostwhite" to "#f8f8ff",
        "gold" to "#ffd700",
        "goldenrod" to "#daa520",
        "gray" to "#808080",
        "grey" to "#808080",
        "green" to "#008000",
        "greenyellow" to "#adff2f",
        "honeydew" to "#f0fff0",
        "hotpink" to "#ff69b4",
        "indianred" to "#cd5c5c",
        "indigo" to "#4b0082",
        "ivory" to "#fffff0",
        "khaki" to "#f0e68c",
        "lavender" to "#e6e6fa",
        "lavenderblush" to "#fff0f5",
        "lawngreen" to "#7cfc00",
        "lemonchiffon" to "#fffacd",
        "lightblue" to "#add8e6",
        "lightcoral" to "#f08080",
        "lightcyan" to "#e0ffff",
        "lightgoldenrodyellow" to "#fafad2",
        "lightgray" to "#d3d3d3",
        "lightgrey" to "#d3d3d3",
        "lightgreen" to "#90ee90",
        "lightpink" to "#ffb6c1",
        "lightsalmon" to "#ffa07a",
        "lightseagreen" to "#20b2aa",
        "lightskyblue" to "#87cefa",
        "lightslategray" to "#778899",
        "lightslategrey" to "#778899",
        "lightsteelblue" to "#b0c4de",
        "lightyellow" to "#ffffe0",
        "lime" to "#00ff00",
        "limegreen" to "#32cd32",
        "linen" to "#faf0e6",
        "magenta" to "#ff00ff",
        "maroon" to "#800000",
        "mediumaquamarine" to "#66cdaa",
        "mediumblue" to "#0000cd",
        "mediumorchid" to "#ba55d3",
        "mediumpurple" to "#9370db",
        "mediumseagreen" to "#3cb371",
        "mediumslateblue" to "#7b68ee",
        "mediumspringgreen" to "#00fa9a",
        "mediumturquoise" to "#48d1cc",
        "mediumvioletred" to "#c71585",
        "midnightblue" to "#191970",
        "mintcream" to "#f5fffa",
        "mistyrose" to "#ffe4e1",
        "moccasin" to "#ffe4b5",
        "navajowhite" to "#ffdead",
        "navy" to "#000080",
        "oldlace" to "#fdf5e6",
        "olive" to "#808000",
        "olivedrab" to "#6b8e23",
        "orange" to "#ffa500",
        "orangered" to "#ff4500",
        "orchid" to "#da70d6",
        "palegoldenrod" to "#eee8aa",
        "palegreen" to "#98fb98",
        "paleturquoise" to "#afeeee",
        "palevioletred" to "#db7093",
        "papayawhip" to "#ffefd5",
        "peachpuff" to "#ffdab9",
        "peru" to "#cd853f",
        "pink" to "#ffc0cb",
        "plum" to "#dda0dd",
        "powderblue" to "#b0e0e6",
        "purple" to "#800080",
        "rebeccapurple" to "#663399",
        "red" to "#ff0000",
        "rosybrown" to "#bc8f8f",
        "royalblue" to "#4169e1",
        "saddlebrown" to "#8b4513",
        "salmon" to "#fa8072",
        "sandybrown" to "#f4a460",
        "seagreen" to "#2e8b57",
        "seashell" to "#fff5ee",
        "sienna" to "#a0522d",
        "silver" to "#c0c0c0",
        "skyblue" to "#87ceeb",
        "slateblue" to "#6a5acd",
        "slategray" to "#708090",
        "slategrey" to "#708090",
        "snow" to "#fffafa",
        "springgreen" to "#00ff7f",
        "steelblue" to "#4682b4",
        "tan" to "#d2b48c",
        "teal" to "#008080",
        "thistle" to "#d8bfd8",
        "tomato" to "#ff6347",
        "turquoise" to "#40e0d0",
        "violet" to "#ee82ee",
        "wheat" to "#f5deb3",
        "white" to "#ffffff",
        "whitesmoke" to "#f5f5f5",
        "yellow" to "#ffff00",
        "yellowgreen" to "#9acd32"
    )

    /**
     * Converts an SVG color value to "#RRGGBB" or "#AARRGGBB" format used by vector drawables.
     * The input color value can be "none" and RGB value, e.g. "rgb(255, 0, 0)",
     * "rgba(255, 0, 0, 127)", or a color name defined in the SVG spec.
     *
     * @param svgColorValue the SVG color value to convert
     * @return the converted value, or null if the given value cannot be interpreted as color
     * @throws IllegalArgumentException if the supplied SVG color value has invalid or unsupported format
     */
    fun colorSvg2Vd(svgColorValue: String): String? {
        val color = svgColorValue.trim()

        if (color.startsWith("#")) {
            // Convert RGBA to ARGB
            return when (color.length) {
                5 -> "#${color.substring(4)}${color.substring(1, 4)}"
                9 -> "#${color.substring(7)}${color.substring(1, 7)}"
                else -> color
            }
        }

        if (color == "none") {
            return "#00000000"
        }

        if (color.startsWith("rgb(") && color.endsWith(")")) {
            val rgb = color.substring(4, color.length - 1)
            val rgbValues = rgb.split(",")
            if (rgbValues.size != 3) {
                throw IllegalArgumentException(svgColorValue)
            }
            return buildString {
                append("#")
                for (value in rgbValues) {
                    val component = getColorComponent(value.trim(), svgColorValue)
                    append(component.toString(16).padStart(2, '0').uppercase())
                }
            }
        }

        if (color.startsWith("rgba(") && color.endsWith(")")) {
            val rgba = color.substring(5, color.length - 1)
            val rgbValues = rgba.split(",")
            if (rgbValues.size != 4) {
                throw IllegalArgumentException(svgColorValue)
            }
            return buildString {
                append("#")
                // Alpha comes first in ARGB
                val alpha = getColorComponent(rgbValues[3].trim(), svgColorValue)
                append(alpha.toString(16).padStart(2, '0').uppercase())
                for (value in rgbValues.take(3)) {
                    val component = getColorComponent(value.trim(), svgColorValue)
                    append(component.toString(16).padStart(2, '0').uppercase())
                }
            }
        }

        return colorMap[color.lowercase()]
    }

    private fun getColorComponent(colorComponent: String, svgColorValue: String): Int {
        return try {
            if (colorComponent.endsWith("%")) {
                val value = colorComponent.dropLast(1).toFloat()
                clampColor((value * 255f / 100f).roundToInt())
            } else {
                clampColor(colorComponent.toInt())
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(svgColorValue)
        }
    }

    private fun clampColor(value: Int): Int = value.coerceIn(0, 255)

    /**
     * Parses a color value string and returns the integer representation.
     */
    fun parseColorValue(colorValue: String): Int {
        val color = colorValue.trim()
        if (color.startsWith("#")) {
            return when (color.length) {
                4 -> {
                    // #RGB -> #FFRRGGBB
                    val r = color[1].digitToInt(16)
                    val g = color[2].digitToInt(16)
                    val b = color[3].digitToInt(16)
                    (0xFF shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
                }
                5 -> {
                    // #RGBA -> #AARRGGBB
                    val r = color[1].digitToInt(16)
                    val g = color[2].digitToInt(16)
                    val b = color[3].digitToInt(16)
                    val a = color[4].digitToInt(16)
                    ((a * 17) shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
                }
                7 -> {
                    // #RRGGBB -> #FFRRGGBB
                    val rgb = color.substring(1).toLong(16).toInt()
                    (0xFF shl 24) or rgb
                }
                9 -> {
                    // #AARRGGBB
                    color.substring(1).toLong(16).toInt()
                }
                else -> 0xFF000000.toInt()
            }
        }
        val namedColor = colorMap[color.lowercase()]
        return if (namedColor != null) parseColorValue(namedColor) else 0xFF000000.toInt()
    }

    /**
     * Multiplies the alpha value into the alpha channel of the color.
     */
    fun applyAlpha(color: Int, alpha: Float): Int {
        val alphaBytes = (color ushr 24) and 0xff
        val newAlpha = (alphaBytes * alpha).toInt()
        return (color and 0x00FFFFFF) or (newAlpha shl 24)
    }
}
