package com.svartifoss.snfell.common

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperellipseTest {

    /**
     * The silhouette of the reference icon this shape was fitted to, read row by row off a 960 x 968
     * picture: how far in from the left edge it sits at a fraction of the height, as a fraction of
     * half the width. Pins the exponent: nudging it to a rounder or squarer number moves these.
     */
    @Test
    fun `the outline follows the reference icon`() {
        val reference = mapOf(
                0.02 to 0.5833, 0.05 to 0.4104, 0.075 to 0.3208, 0.10 to 0.2521, 0.15 to 0.1625,
                0.20 to 0.1062, 0.25 to 0.0646, 0.30 to 0.0354, 0.40 to 0.0083)
        reference.forEach { (heightFraction, inset) ->
            val measured = Superellipse.leftInset(
                    y = (heightFraction * 967).toFloat(), width = 2f, height = 968f)
            assertEquals("at $heightFraction of the height", inset, measured.toDouble(), 0.015)
        }
    }

    @Test
    fun `every point sits on the curve and the outline fills its box`() {
        val box = floatArrayOf(10f, 20f, 110f, 120f)
        val points = Superellipse.outline(box[0], box[1], box[2], box[3])
        assertEquals(Superellipse.POINT_COUNT * 2, points.size)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (index in 0 until Superellipse.POINT_COUNT) {
            val x = points[index * 2]
            val y = points[index * 2 + 1]
            val unitX = abs((x - 60f) / 50f).toDouble()
            val unitY = abs((y - 70f) / 50f).toDouble()
            assertEquals(1.0,
                    unitX.pow(Superellipse.EXPONENT.toDouble()) +
                            unitY.pow(Superellipse.EXPONENT.toDouble()),
                    1e-4)
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        assertEquals(10f, minX, 1e-3f)
        assertEquals(110f, maxX, 1e-3f)
        assertEquals(20f, minY, 1e-3f)
        assertEquals(120f, maxY, 1e-3f)
    }

    @Test
    fun `the outline starts at the middle of the top edge and runs clockwise`() {
        val points = Superellipse.outline(0f, 0f, 100f, 100f)
        assertEquals(50f, points[0], 1e-3f)
        assertEquals(0f, points[1], 1e-3f)
        // A quarter of the way round is the middle of the right edge.
        val quarter = Superellipse.SEGMENTS_PER_QUARTER
        assertEquals(100f, points[quarter * 2], 1e-3f)
        assertEquals(50f, points[quarter * 2 + 1], 1e-3f)
    }

    /** A chord between neighbouring points must stay within a tenth of a pixel of the true curve
     *  on a 200 px cover, or the polygon would read as faceted rather than as a curve. */
    @Test
    fun `the polygon is smooth enough to pass for the curve`() {
        val size = 200f
        val points = Superellipse.outline(0f, 0f, size, size)
        val n = Superellipse.EXPONENT.toDouble()
        for (index in 0 until Superellipse.POINT_COUNT) {
            val next = (index + 1) % Superellipse.POINT_COUNT
            val midX = (points[index * 2] + points[next * 2]) / 2f
            val midY = (points[index * 2 + 1] + points[next * 2 + 1]) / 2f
            val unitX = abs((midX - size / 2f) / (size / 2f)).toDouble()
            val unitY = abs((midY - size / 2f) / (size / 2f)).toDouble()
            // The chord's middle sits just inside the curve; how far in, as a distance.
            val radial = (unitX.pow(n) + unitY.pow(n)).pow(1.0 / n)
            val gap = (1.0 - radial) * size / 2.0
            assertTrue("segment $index is $gap px off the curve", gap in -0.01..0.1)
        }
    }

    @Test
    fun `the inset is nothing at the middle and grows towards the corners`() {
        assertEquals(0f, Superellipse.leftInset(50f, 100f, 100f), 1e-3f)
        assertTrue(Superellipse.leftInset(20f, 100f, 100f) < Superellipse.leftInset(5f, 100f, 100f))
        assertEquals(50f, Superellipse.leftInset(0f, 100f, 100f), 1e-3f)
        assertEquals(50f, Superellipse.leftInset(100f, 100f, 100f), 1e-3f)
        assertEquals(0f, Superellipse.leftInset(10f, 0f, 100f), 0f)
    }

    /** What makes it a different shape from the rounded square it replaced rather than a rounder
     *  one: that one ran dead straight for part of each side, this never does. */
    @Test
    fun `it bulges where a rounded rectangle of the same corners runs straight`() {
        val size = 100f
        val cornerRadius = CoverShape.SUPERELLIPSE.topLeft * size
        // Just past where the old shape's corner ends (30 px from the top) it runs dead straight.
        val y = cornerRadius + 2f
        assertTrue(y > cornerRadius)
        assertTrue("inset was ${Superellipse.leftInset(y, size, size)}",
                Superellipse.leftInset(y, size, size) > 0.5f)
    }
}
