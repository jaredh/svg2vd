package dev.hendry.svg2vd.converter

import dev.hendry.svg2vd.util.AffineTransform
import dev.hendry.svg2vd.util.PointF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests validating standard 2D affine transformation mathematics.
 *
 * An affine transformation is represented as:
 * ```
 * | x' |   | m00  m01  m02 |   | x |
 * | y' | = | m10  m11  m12 | × | y |
 * | 1  |   |  0    0    1  |   | 1 |
 * ```
 *
 * Which expands to:
 * - x' = m00*x + m01*y + m02
 * - y' = m10*x + m11*y + m12
 */
class AffineTransformTest {

    private fun assertNear(expected: Double, actual: Double, epsilon: Double = 1e-9, message: String = "") {
        assertTrue(abs(expected - actual) < epsilon, "$message Expected $expected but was $actual (diff: ${abs(expected - actual)})")
    }

    private fun transformPoint(t: AffineTransform, x: Double, y: Double): Pair<Double, Double> {
        val src = doubleArrayOf(x, y)
        val dst = DoubleArray(2)
        t.transform(src, 0, dst, 0, 1)
        return dst[0] to dst[1]
    }

    @Test
    fun transformAppliesMatrixFormula() {
        // Verify: x' = m00*x + m01*y + m02, y' = m10*x + m11*y + m12
        val t = AffineTransform(
            m00 = 2.0, m10 = 0.5,
            m01 = 0.3, m11 = 3.0,
            m02 = 10.0, m12 = 20.0
        )

        val x = 5.0
        val y = 7.0
        val (xPrime, yPrime) = transformPoint(t, x, y)

        val expectedX = 2.0 * x + 0.3 * y + 10.0  // 10 + 2.1 + 10 = 22.1
        val expectedY = 0.5 * x + 3.0 * y + 20.0  // 2.5 + 21 + 20 = 43.5

        assertNear(expectedX, xPrime, message = "x' = m00*x + m01*y + m02")
        assertNear(expectedY, yPrime, message = "y' = m10*x + m11*y + m12")
    }

    @Test
    fun translationMatrix() {
        // Translation by (tx, ty) is:
        // | 1  0  tx |
        // | 0  1  ty |
        val tx = 15.0
        val ty = -25.0

        val t = AffineTransform()
        t.translate(tx, ty)

        assertNear(1.0, t.m00, message = "Translation preserves x scale")
        assertNear(0.0, t.m01, message = "Translation has no x shear")
        assertNear(tx, t.m02, message = "Translation x component")
        assertNear(0.0, t.m10, message = "Translation has no y shear")
        assertNear(1.0, t.m11, message = "Translation preserves y scale")
        assertNear(ty, t.m12, message = "Translation y component")
    }

    @Test
    fun translationMovesPoints() {
        val t = AffineTransform()
        t.translate(100.0, -50.0)

        val (x, y) = transformPoint(t, 10.0, 20.0)

        assertNear(110.0, x, message = "x + tx")
        assertNear(-30.0, y, message = "y + ty")
    }

    @Test
    fun scalingMatrix() {
        // Scaling by (sx, sy) is:
        // | sx  0   0 |
        // | 0   sy  0 |
        val sx = 2.5
        val sy = 0.5

        val t = AffineTransform()
        t.scale(sx, sy)

        assertNear(sx, t.m00, message = "Scale x component")
        assertNear(0.0, t.m01, message = "Scale has no x shear")
        assertNear(0.0, t.m02, message = "Scale has no translation")
        assertNear(0.0, t.m10, message = "Scale has no y shear")
        assertNear(sy, t.m11, message = "Scale y component")
        assertNear(0.0, t.m12, message = "Scale has no translation")
    }

    @Test
    fun scalingMultipliesCoordinates() {
        val t = AffineTransform()
        t.scale(3.0, 2.0)

        val (x, y) = transformPoint(t, 10.0, 20.0)

        assertNear(30.0, x, message = "x * sx")
        assertNear(40.0, y, message = "y * sy")
    }

    @Test
    fun uniformScalingPreservesAngles() {
        // Uniform scaling preserves the angle between vectors
        val t = AffineTransform()
        t.scale(5.0, 5.0)

        // Two perpendicular vectors
        val v1 = transformPoint(t, 1.0, 0.0)
        val v2 = transformPoint(t, 0.0, 1.0)

        // Dot product of perpendicular vectors is 0
        val dotProduct = v1.first * v2.first + v1.second * v2.second
        assertNear(0.0, dotProduct, message = "Uniform scale preserves perpendicularity")
    }

    @Test
    fun rotationMatrix() {
        // Rotation by θ is:
        // | cos(θ)  -sin(θ)  0 |
        // | sin(θ)   cos(θ)  0 |
        val theta = PI / 6  // 30 degrees

        val t = AffineTransform()
        t.rotate(theta)

        assertNear(cos(theta), t.m00, message = "Rotation m00 = cos(θ)")
        assertNear(-sin(theta), t.m01, message = "Rotation m01 = -sin(θ)")
        assertNear(sin(theta), t.m10, message = "Rotation m10 = sin(θ)")
        assertNear(cos(theta), t.m11, message = "Rotation m11 = cos(θ)")
    }

    @Test
    fun rotationPreservesDistance() {
        // Rotation is an isometry - preserves distances
        val t = AffineTransform()
        t.rotate(PI / 3)  // 60 degrees

        val originalDistance = sqrt(10.0 * 10.0 + 20.0 * 20.0)
        val (x, y) = transformPoint(t, 10.0, 20.0)
        val newDistance = sqrt(x * x + y * y)

        assertNear(originalDistance, newDistance, message = "Rotation preserves distance from origin")
    }

    @Test
    fun rotation90DegreesSwapsAxes() {
        val t = AffineTransform()
        t.rotate(PI / 2)

        val (x, y) = transformPoint(t, 10.0, 0.0)

        assertNear(0.0, x, message = "90° rotation: x becomes 0")
        assertNear(10.0, y, message = "90° rotation: y becomes original x")
    }

    @Test
    fun rotation180DegreesNegates() {
        val t = AffineTransform()
        t.rotate(PI)

        val (x, y) = transformPoint(t, 10.0, 20.0)

        assertNear(-10.0, x, message = "180° rotation negates x")
        assertNear(-20.0, y, message = "180° rotation negates y")
    }

    @Test
    fun rotationAroundPointFormula() {
        // Rotation around (cx, cy) = Translate(-cx, -cy) → Rotate → Translate(cx, cy)
        val cx = 50.0
        val cy = 50.0
        val theta = PI / 2

        val t = AffineTransform()
        t.rotate(theta, cx, cy)

        // Point at (cx + 10, cy) should end up at (cx, cy + 10)
        val (x, y) = transformPoint(t, cx + 10.0, cy)

        assertNear(cx, x, 1e-6, message = "Rotation around point: x")
        assertNear(cy + 10.0, y, 1e-6, message = "Rotation around point: y")
    }

    @Test
    fun shearMatrix() {
        // Shear by (shx, shy) is:
        // | 1    shx   0 |
        // | shy  1     0 |
        val shx = 0.5
        val shy = 0.25

        val t = AffineTransform()
        t.shear(shx, shy)

        assertNear(1.0, t.m00, message = "Shear preserves x scale")
        assertNear(shx, t.m01, message = "Shear x component")
        assertNear(shy, t.m10, message = "Shear y component")
        assertNear(1.0, t.m11, message = "Shear preserves y scale")
    }

    @Test
    fun horizontalShearFormula() {
        // Horizontal shear: x' = x + shx*y, y' = y
        val shx = 2.0
        val t = AffineTransform()
        t.shear(shx, 0.0)

        val (xPrime, yPrime) = transformPoint(t, 10.0, 5.0)

        assertNear(10.0 + shx * 5.0, xPrime, message = "x' = x + shx*y")
        assertNear(5.0, yPrime, message = "y unchanged in horizontal shear")
    }

    @Test
    fun verticalShearFormula() {
        // Vertical shear: x' = x, y' = y + shy*x
        val shy = 0.5
        val t = AffineTransform()
        t.shear(0.0, shy)

        val (xPrime, yPrime) = transformPoint(t, 10.0, 5.0)

        assertNear(10.0, xPrime, message = "x unchanged in vertical shear")
        assertNear(5.0 + shy * 10.0, yPrime, message = "y' = y + shy*x")
    }

    @Test
    fun concatenationIsMatrixMultiplication() {
        // C = A × B means applying B first, then A
        // For transforms: (A × B)(p) = A(B(p))
        val a = AffineTransform()
        a.scale(2.0, 2.0)

        val b = AffineTransform()
        b.translate(10.0, 0.0)

        val combined = AffineTransform(a)
        combined.concatenate(b)

        // Transform point with combined
        val (x1, y1) = transformPoint(combined, 5.0, 0.0)

        // Transform point stepwise: B first, then A
        val (bx, by) = transformPoint(b, 5.0, 0.0)  // 5 + 10 = 15
        val (ax, ay) = transformPoint(a, bx, by)     // 15 * 2 = 30

        assertNear(ax, x1, message = "Concatenation equals sequential application")
        assertNear(ay, y1)
    }

    @Test
    fun concatenationIsNonCommutative() {
        // In general, A × B ≠ B × A
        val scale = AffineTransform()
        scale.scale(2.0, 2.0)

        val translate = AffineTransform()
        translate.translate(10.0, 0.0)

        val scaleFirst = AffineTransform(scale)
        scaleFirst.concatenate(translate)  // scale(translate(p))

        val translateFirst = AffineTransform(translate)
        translateFirst.concatenate(scale)  // translate(scale(p))

        val p1 = transformPoint(scaleFirst, 5.0, 0.0)
        val p2 = transformPoint(translateFirst, 5.0, 0.0)

        // scale(translate(5,0)) = scale(15,0) = (30,0)
        // translate(scale(5,0)) = translate(10,0) = (20,0)
        assertNear(30.0, p1.first, message = "Scale after translate")
        assertNear(20.0, p2.first, message = "Translate after scale")
    }

    @Test
    fun preConcatenationOrder() {
        // preConcatenate(T) means: result = T × this
        // So T is applied AFTER this
        val a = AffineTransform()
        a.scale(2.0, 2.0)

        val b = AffineTransform()
        b.translate(10.0, 0.0)

        val combined = AffineTransform(a)
        combined.preConcatenate(b)  // result = B × A, so apply A then B

        val (x, y) = transformPoint(combined, 5.0, 0.0)

        // A(5,0) = (10,0), then B(10,0) = (20,0)
        assertNear(20.0, x, message = "preConcatenate applies outer transform last")
    }

    @Test
    fun determinantFormula() {
        // det = m00*m11 - m01*m10
        val t = AffineTransform(
            m00 = 3.0, m10 = 2.0,
            m01 = 1.0, m11 = 4.0,
            m02 = 0.0, m12 = 0.0
        )

        val expected = 3.0 * 4.0 - 1.0 * 2.0  // 12 - 2 = 10
        assertNear(expected, t.getDeterminant(), message = "det = m00*m11 - m01*m10")
    }

    @Test
    fun determinantOfScaling() {
        // det(scale(sx, sy)) = sx * sy
        val t = AffineTransform()
        t.scale(3.0, 4.0)

        assertNear(12.0, t.getDeterminant(), message = "det(scale) = sx * sy")
    }

    @Test
    fun determinantOfRotation() {
        // Rotation matrices have determinant 1 (preserves area)
        val t = AffineTransform()
        t.rotate(PI / 5)

        assertNear(1.0, t.getDeterminant(), message = "det(rotation) = 1")
    }

    @Test
    fun determinantOfShear() {
        // Pure shear (one axis only) has determinant 1
        val t = AffineTransform()
        t.shear(0.5, 0.0)  // Horizontal shear only

        assertNear(1.0, t.getDeterminant(), message = "det(horizontal shear) = 1")

        // Combined shear: det = 1 - shx*shy
        val t2 = AffineTransform()
        val shx = 0.5
        val shy = 0.3
        t2.shear(shx, shy)

        assertNear(1.0 - shx * shy, t2.getDeterminant(), message = "det(shear) = 1 - shx*shy")
    }

    @Test
    fun determinantOfProduct() {
        // det(A × B) = det(A) * det(B)
        val a = AffineTransform()
        a.scale(2.0, 3.0)  // det = 6

        val b = AffineTransform()
        b.scale(4.0, 5.0)  // det = 20

        val combined = AffineTransform(a)
        combined.concatenate(b)

        assertNear(6.0 * 20.0, combined.getDeterminant(), message = "det(A×B) = det(A) * det(B)")
    }

    @Test
    fun negativeDeterminantIndicatesReflection() {
        val t = AffineTransform()
        t.scale(-1.0, 1.0)  // Reflection across y-axis

        assertTrue(t.getDeterminant() < 0, "Reflection has negative determinant")
    }

    @Test
    fun deltaTransformFormula() {
        // Verify: dx' = m00*dx + m01*dy, dy' = m10*dx + m11*dy (no translation)
        val t = AffineTransform(
            m00 = 2.0, m10 = 0.5,
            m01 = 0.3, m11 = 3.0,
            m02 = 999.0, m12 = 999.0  // Should be ignored
        )

        val dx = 5.0
        val dy = 7.0
        val src = doubleArrayOf(dx, dy)
        val dst = DoubleArray(2)
        t.deltaTransform(src, 0, dst, 0, 1)

        val expectedDx = 2.0 * dx + 0.3 * dy  // 10 + 2.1 = 12.1
        val expectedDy = 0.5 * dx + 3.0 * dy  // 2.5 + 21 = 23.5

        assertNear(expectedDx, dst[0], message = "dx' = m00*dx + m01*dy")
        assertNear(expectedDy, dst[1], message = "dy' = m10*dx + m11*dy")
    }

    @Test
    fun deltaTransformIgnoresTranslation() {
        val t = AffineTransform()
        t.translate(1000.0, 1000.0)
        t.scale(2.0, 3.0)

        val src = doubleArrayOf(10.0, 10.0)
        val dst = DoubleArray(2)
        t.deltaTransform(src, 0, dst, 0, 1)

        assertNear(20.0, dst[0], message = "Delta transform applies scale")
        assertNear(30.0, dst[1], message = "Delta transform ignores translation")
    }

    @Test
    fun deltaTransformAppliesRotation() {
        val t = AffineTransform()
        t.translate(500.0, 500.0)  // Should be ignored
        t.rotate(PI / 2)  // 90 degrees

        val src = doubleArrayOf(10.0, 0.0)
        val dst = DoubleArray(2)
        t.deltaTransform(src, 0, dst, 0, 1)

        // Vector pointing right rotates to point up
        assertNear(0.0, dst[0], message = "Rotated vector x")
        assertNear(10.0, dst[1], message = "Rotated vector y")
    }

    @Test
    fun deltaTransformAppliesShear() {
        val t = AffineTransform()
        t.translate(100.0, 100.0)  // Should be ignored
        t.shear(0.5, 0.0)  // Horizontal shear

        val src = doubleArrayOf(0.0, 10.0)
        val dst = DoubleArray(2)
        t.deltaTransform(src, 0, dst, 0, 1)

        assertNear(5.0, dst[0], message = "Sheared vector x")
        assertNear(10.0, dst[1], message = "Sheared vector y unchanged")
    }

    @Test
    fun deltaTransformPointFOverload() {
        val t = AffineTransform()
        t.translate(999.0, 999.0)  // Should be ignored
        t.scale(2.0, 3.0)

        val result = t.deltaTransform(PointF(10f, 10f))

        assertNear(20.0, result.x.toDouble(), message = "PointF x scaled")
        assertNear(30.0, result.y.toDouble(), message = "PointF y scaled")
    }

    @Test
    fun deltaTransformPreservesVectorLength() {
        // Pure rotation should preserve vector length
        val t = AffineTransform()
        t.translate(100.0, 200.0)  // Should be ignored
        t.rotate(PI / 5)

        val originalLength = sqrt(10.0 * 10.0 + 20.0 * 20.0)

        val src = doubleArrayOf(10.0, 20.0)
        val dst = DoubleArray(2)
        t.deltaTransform(src, 0, dst, 0, 1)

        val newLength = sqrt(dst[0] * dst[0] + dst[1] * dst[1])

        assertNear(originalLength, newLength, message = "Rotation preserves vector length")
    }

    @Test
    fun deltaTransformVsTransformDifference() {
        // Show the difference between transform and deltaTransform
        val t = AffineTransform()
        t.translate(100.0, 50.0)
        t.scale(2.0, 2.0)

        val point = doubleArrayOf(10.0, 10.0)
        val transformResult = DoubleArray(2)
        val deltaResult = DoubleArray(2)

        t.transform(point, 0, transformResult, 0, 1)
        t.deltaTransform(point, 0, deltaResult, 0, 1)

        // transform includes translation: (10*2 + 100, 10*2 + 50) = (120, 70)
        assertNear(120.0, transformResult[0], message = "transform includes translation")
        assertNear(70.0, transformResult[1])

        // deltaTransform excludes translation: (10*2, 10*2) = (20, 20)
        assertNear(20.0, deltaResult[0], message = "deltaTransform excludes translation")
        assertNear(20.0, deltaResult[1])
    }

    @Test
    fun identityMatrixValues() {
        // Identity matrix:
        // | 1  0  0 |
        // | 0  1  0 |
        val t = AffineTransform()

        assertNear(1.0, t.m00)
        assertNear(0.0, t.m01)
        assertNear(0.0, t.m02)
        assertNear(0.0, t.m10)
        assertNear(1.0, t.m11)
        assertNear(0.0, t.m12)
        assertTrue(t.isIdentity())
    }

    @Test
    fun identityIsMultiplicativeIdentity() {
        // I × A = A × I = A
        val a = AffineTransform()
        a.translate(10.0, 20.0)
        a.rotate(PI / 4)
        a.scale(2.0, 3.0)

        val identity = AffineTransform()

        val leftMultiply = AffineTransform(identity)
        leftMultiply.concatenate(a)

        val rightMultiply = AffineTransform(a)
        rightMultiply.concatenate(identity)

        val p = transformPoint(a, 5.0, 7.0)
        val pLeft = transformPoint(leftMultiply, 5.0, 7.0)
        val pRight = transformPoint(rightMultiply, 5.0, 7.0)

        assertNear(p.first, pLeft.first, message = "I × A = A")
        assertNear(p.second, pLeft.second)
        assertNear(p.first, pRight.first, message = "A × I = A")
        assertNear(p.second, pRight.second)
    }
}
