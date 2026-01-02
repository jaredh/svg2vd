package dev.hendry.svg2vd.util

import kotlin.math.sqrt

data class PointF(var x: Float = 0f, var y: Float = 0f) {
    fun distance(px: Double, py: Double): Double {
        val dx = x - px
        val dy = y - py
        return sqrt(dx * dx + dy * dy)
    }
}
