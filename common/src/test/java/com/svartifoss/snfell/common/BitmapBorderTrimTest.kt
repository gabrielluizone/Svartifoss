package com.svartifoss.snfell.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapBorderTrimTest {

    private fun pixel(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

    @Test
    fun `a flat line of identical pixels is uniform`() {
        val line = List(40) { pixel(10, 10, 10) }
        assertTrue(BitmapBorderTrim.isUniformLine(0, line.lastIndex) { line[it] })
    }

    @Test
    fun `small noise within the threshold still reads as uniform`() {
        // A real "flat" bar is rarely bit-identical (compression artifacts, dithering) - the
        // threshold exists precisely so this still counts as a border.
        val line = List(40) { i -> pixel(10 + (i % 3), 10, 10) }
        assertTrue(BitmapBorderTrim.isUniformLine(0, line.lastIndex) { line[it] })
    }

    @Test
    fun `a real photo edge with varied colour is not uniform`() {
        // Sampling is every 4th index (0, 4, 8, ...), so the differing pixels must land on one of
        // those positions to be seen at all - matches how the function is actually walked.
        val line = MutableList(12) { pixel(10, 10, 10) }
        line[4] = pixel(200, 40, 90)
        line[8] = pixel(30, 220, 15)
        assertFalse(BitmapBorderTrim.isUniformLine(0, line.lastIndex) { line[it] })
    }

    @Test
    fun `a jump in a single channel past the threshold breaks uniformity`() {
        val line = MutableList(8) { pixel(10, 10, 10) }
        line[4] = pixel(10, 10, 200)
        assertFalse(BitmapBorderTrim.isUniformLine(0, line.lastIndex) { line[it] })
    }

    @Test
    fun `only every fourth pixel is sampled`() {
        // A single outlier landing between sampled positions must not be able to flip the result -
        // this is what keeps the border walk cheap on a full-size cover.
        val line = MutableList(40) { pixel(10, 10, 10) }
        line[1] = pixel(255, 255, 255)
        assertTrue(BitmapBorderTrim.isUniformLine(0, line.lastIndex) { line[it] })
    }

    // ---- shape rule --------------------------------------------------------------

    /** The case the trim exists for: a 4:3 thumbnail with bars around a square cover. */
    @Test
    fun `a pillarboxed thumbnail is cropped to its square cover`() {
        assertTrue(BitmapBorderTrim.shouldCrop(480, 360, 360, 360))
    }

    /**
     * The case that went wrong: a square sleeve with a plain field around a small logo. Its
     * margin is its design, and it must not be cropped down to the logo - so a square picture is
     * never trimmed at all.
     */
    @Test
    fun `a square cover is left whole, flat margin and all`() {
        assertTrue(BitmapBorderTrim.isSquare(500, 500))
        assertTrue(BitmapBorderTrim.isSquare(500, 490))
        assertFalse(BitmapBorderTrim.isSquare(480, 360))
    }

    /** A landscape photo with a flat sky gets wider when its top is cut, not squarer. */
    @Test
    fun `a crop that moves away from square is refused`() {
        assertFalse(BitmapBorderTrim.shouldCrop(480, 360, 480, 300))
    }

    @Test
    fun `a degenerate or empty crop is refused`() {
        assertFalse(BitmapBorderTrim.shouldCrop(480, 360, 480, 360))
        assertFalse(BitmapBorderTrim.shouldCrop(480, 360, 10, 10))
    }
}
