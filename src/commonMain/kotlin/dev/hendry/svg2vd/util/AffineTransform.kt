package dev.hendry.svg2vd.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val TYPE_IDENTITY = 0
const val TYPE_TRANSLATION = 1
const val TYPE_UNIFORM_SCALE = 2
const val TYPE_GENERAL_SCALE = 4
const val TYPE_MASK_SCALE = TYPE_UNIFORM_SCALE or TYPE_GENERAL_SCALE
const val TYPE_FLIP = 64
const val TYPE_QUADRANT_ROTATION = 8
const val TYPE_GENERAL_ROTATION = 16

/**
 * Kotlin Multiplatform implementation of 2D affine transformations.
 *
 * The transformation matrix is:
 * ```
 * [ m00  m01  m02 ]   [ x ]   [ m00*x + m01*y + m02 ]
 * [ m10  m11  m12 ] * [ y ] = [ m10*x + m11*y + m12 ]
 * [  0    0    1  ]   [ 1 ]   [          1          ]
 * ```
 */
class AffineTransform(
    var m00: Double = 1.0,
    var m10: Double = 0.0,
    var m01: Double = 0.0,
    var m11: Double = 1.0,
    var m02: Double = 0.0,
    var m12: Double = 0.0
) {
    constructor(other: AffineTransform) : this(
        other.m00,
        other.m10,
        other.m01,
        other.m11,
        other.m02,
        other.m12
    )

    fun setTransform(
        m00: Double,
        m10: Double,
        m01: Double,
        m11: Double,
        m02: Double,
        m12: Double
    ) {
        this.m00 = m00
        this.m10 = m10
        this.m01 = m01
        this.m11 = m11
        this.m02 = m02
        this.m12 = m12
    }

    fun setTransform(other: AffineTransform) {
        setTransform(other.m00, other.m10, other.m01, other.m11, other.m02, other.m12)
    }

    fun translate(translateX: Double, translateY: Double) {
        m02 += translateX * m00 + translateY * m01
        m12 += translateX * m10 + translateY * m11
    }

    fun scale(scaleX: Double, scaleY: Double) {
        m00 *= scaleX
        m10 *= scaleX
        m01 *= scaleY
        m11 *= scaleY
    }

    fun rotate(
        theta: Double,
        anchorX: Double = 0.0,
        anchorY: Double = 0.0
    ) {
        if (anchorX != 0.0 || anchorY != 0.0) {
            translate(anchorX, anchorY)
            rotate(theta)
            translate(-anchorX, -anchorY)
        } else {
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)
            val newM00 = m00 * cosTheta + m01 * sinTheta
            val newM10 = m10 * cosTheta + m11 * sinTheta
            val newM01 = m01 * cosTheta - m00 * sinTheta
            val newM11 = m11 * cosTheta - m10 * sinTheta
            m00 = newM00
            m10 = newM10
            m01 = newM01
            m11 = newM11
        }
    }

    fun shear(shearX: Double, shearY: Double) {
        val newM00 = m00 + m01 * shearY
        val newM10 = m10 + m11 * shearY
        val newM01 = m01 + m00 * shearX
        val newM11 = m11 + m10 * shearX
        m00 = newM00
        m10 = newM10
        m01 = newM01
        m11 = newM11
    }

    fun concatenate(other: AffineTransform) {
        val newM00 = m00 * other.m00 + m01 * other.m10
        val newM10 = m10 * other.m00 + m11 * other.m10
        val newM01 = m00 * other.m01 + m01 * other.m11
        val newM11 = m10 * other.m01 + m11 * other.m11
        val newM02 = m00 * other.m02 + m01 * other.m12 + m02
        val newM12 = m10 * other.m02 + m11 * other.m12 + m12
        m00 = newM00
        m10 = newM10
        m01 = newM01
        m11 = newM11
        m02 = newM02
        m12 = newM12
    }

    fun preConcatenate(other: AffineTransform) {
        val newM00 = other.m00 * m00 + other.m01 * m10
        val newM10 = other.m10 * m00 + other.m11 * m10
        val newM01 = other.m00 * m01 + other.m01 * m11
        val newM11 = other.m10 * m01 + other.m11 * m11
        val newM02 = other.m00 * m02 + other.m01 * m12 + other.m02
        val newM12 = other.m10 * m02 + other.m11 * m12 + other.m12
        m00 = newM00
        m10 = newM10
        m01 = newM01
        m11 = newM11
        m02 = newM02
        m12 = newM12
    }

    fun getDeterminant(): Double = m00 * m11 - m01 * m10

    fun isIdentity(): Boolean =
        m00 == 1.0 && m10 == 0.0 && m01 == 0.0 && m11 == 1.0 && m02 == 0.0 && m12 == 0.0

    fun getType(): Int {
        if (m00 == 1.0 && m10 == 0.0 && m01 == 0.0 && m11 == 1.0) {
            return if (m02 == 0.0 && m12 == 0.0) TYPE_IDENTITY else TYPE_TRANSLATION
        }

        var type = 0
        if (m02 != 0.0 || m12 != 0.0) {
            type = TYPE_TRANSLATION
        }

        val det = getDeterminant()
        if (det < 0) {
            type = type or TYPE_FLIP
        }

        val scaleX = sqrt(m00 * m00 + m10 * m10)
        val scaleY = sqrt(m01 * m01 + m11 * m11)

        if (scaleX != 1.0 || scaleY != 1.0) {
            type = if (abs(scaleX - scaleY) < 1e-9) {
                type or TYPE_UNIFORM_SCALE
            } else {
                type or TYPE_GENERAL_SCALE
            }
        }

        if (m01 != 0.0 || m10 != 0.0) {
            val angle = atan2(m10, m00)
            val angleTest = angle / (PI / 2)
            type = if (abs(angleTest - angleTest.toLong()) < 1e-9) {
                type or TYPE_QUADRANT_ROTATION
            } else {
                type or TYPE_GENERAL_ROTATION
            }
        }

        return type
    }

    /**
     * Transforms an array of source points and stores the results in destination.
     * Points are stored as [x0, y0, x1, y1, ...].
     */
    fun transform(
        sourcePoints: FloatArray,
        sourceOffset: Int,
        destinationPoints: FloatArray,
        destinationOffset: Int,
        numberOfPoints: Int
    ) {
        var sourceIndex = sourceOffset
        var destinationIndex = destinationOffset
        repeat(numberOfPoints) {
            val x = sourcePoints[sourceIndex++].toDouble()
            val y = sourcePoints[sourceIndex++].toDouble()
            destinationPoints[destinationIndex++] = (m00 * x + m01 * y + m02).toFloat()
            destinationPoints[destinationIndex++] = (m10 * x + m11 * y + m12).toFloat()
        }
    }

    fun transform(
        sourcePoints: DoubleArray,
        sourceOffset: Int,
        destinationPoints: DoubleArray,
        destinationOffset: Int,
        numberOfPoints: Int
    ) {
        var sourceIndex = sourceOffset
        var destinationIndex = destinationOffset
        repeat(numberOfPoints) {
            val x = sourcePoints[sourceIndex++]
            val y = sourcePoints[sourceIndex++]
            destinationPoints[destinationIndex++] = m00 * x + m01 * y + m02
            destinationPoints[destinationIndex++] = m10 * x + m11 * y + m12
        }
    }

    /**
     * Transforms the relative distance vector (dx, dy) without translation.
     */
    fun deltaTransform(
        sourcePoints: DoubleArray,
        sourceOffset: Int,
        destinationPoints: DoubleArray,
        destinationOffset: Int,
        numberOfPoints: Int
    ) {
        var sourceIndex = sourceOffset
        var destinationIndex = destinationOffset
        repeat(numberOfPoints) {
            val x = sourcePoints[sourceIndex++]
            val y = sourcePoints[sourceIndex++]
            destinationPoints[destinationIndex++] = m00 * x + m01 * y
            destinationPoints[destinationIndex++] = m10 * x + m11 * y
        }
    }

    fun deltaTransform(point: PointF): PointF {
        return PointF(
            (m00 * point.x + m01 * point.y).toFloat(),
            (m10 * point.x + m11 * point.y).toFloat()
        )
    }

    override fun toString(): String =
        "AffineTransform[[${m00}, ${m01}, ${m02}], [${m10}, ${m11}, ${m12}]]"
}
