package dev.hendry.svg2vd.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Converts an angle measured in degrees to an approximately equivalent angle measured in radians.
 */
fun toRadians(degrees: Double): Double = degrees * PI / 180.0

/**
 * Converts an angle measured in radians to an approximately equivalent angle measured in degrees.
 */
fun toDegrees(radians: Double): Double = radians * 180.0 / PI

/**
 * Formats a float value to a string with the specified precision.
 */
fun formatFloat(value: Float, precision: Int = 4): String {
    return formatDouble(value.toDouble(), precision)
}

/**
 * Formats a double value to a string with the specified precision.
 */
fun formatDouble(value: Double, precision: Int = 4): String {
    if (value.isNaN() || value.isInfinite()) {
        return value.toString()
    }

    val multiplier = 10.0.pow(precision)
    val rounded = (value * multiplier).roundToLong() / multiplier

    if (rounded == rounded.toLong().toDouble()) {
        return rounded.toLong().toString()
    }

    val isNegative = rounded < 0
    val absValue = abs(rounded)
    val intPart = absValue.toLong()
    val fracPart = ((absValue - intPart) * multiplier).roundToLong()

    if (fracPart == 0L) {
        return if (isNegative) "-$intPart" else intPart.toString()
    }

    val fracStr = fracPart.toString().padStart(precision, '0').trimEnd('0')

    val prefix = if (isNegative) "-" else ""
    return "$prefix$intPart.$fracStr"
}

/**
 * Formats an integer value as a hex string with the specified number of digits.
 */
fun formatHex(value: Int, digits: Int): String {
    val hex = (value.toLong() and 0xFFFFFFFFL).toString(16).uppercase()
    return hex.padStart(digits, '0')
}
