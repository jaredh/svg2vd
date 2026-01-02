package dev.hendry.svg2vd.util

/**
 * Converts a filename to a valid Android drawable resource name.
 *
 * Android resource names must:
 * - Contain only lowercase a-z, 0-9, or underscore
 * - Not start with a number
 *
 * @param name The input filename (without extension)
 * @return A valid Android drawable resource name
 */
fun toValidDrawableName(name: String): String {
    return name.lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trimStart('_')
        .trimEnd('_')
        .ifEmpty { "drawable" }
        .let { if (it[0].isDigit()) "ic_$it" else it }
}
