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
     * margin is its design, and it must not be cropped down to the logo. The only exception is
     * a square canvas with two large, opposite letterbox bars, covered below.
     */
    @Test
    fun `a square cover is left whole, flat margin and all`() {
        assertTrue(BitmapBorderTrim.isSquare(500, 500))
        assertTrue(BitmapBorderTrim.isSquare(500, 490))
        assertFalse(BitmapBorderTrim.isSquare(480, 360))
    }

    @Test
    fun `square canvas with two substantial horizontal bars is treated as letterboxed`() {
        assertTrue(BitmapBorderTrim.shouldCropSquareLetterbox(
                width = 400, height = 400, top = 52, bottom = 347, left = 0, right = 399))
    }

    @Test
    fun `square sleeve framed on every side is not mistaken for letterboxing`() {
        assertFalse(BitmapBorderTrim.shouldCropSquareLetterbox(
                width = 400, height = 400, top = 52, bottom = 347, left = 52, right = 347))
    }

    @Test
    fun `tiny square edges are not enough to trigger a crop`() {
        assertFalse(BitmapBorderTrim.shouldCropSquareLetterbox(
                width = 400, height = 400, top = 4, bottom = 395, left = 0, right = 399))
    }

    // ---- what a letterbox is, and what only looks like one -----------------------------

    @Test
    fun `a sleeve that fades to black over a few percent is not a letterbox`() {
        // Muse's Panic Station: 269px square, black for 6% above and 9% below its design. Both
        // are flat and both are black, which is exactly what a letterbox looks like - only their
        // depth says they are the sleeve's own margin.
        assertFalse(BitmapBorderTrim.shouldCropSquareLetterbox(
                width = 269, height = 269, top = 16, bottom = 244, left = 0, right = 268))
    }

    @Test
    fun `a thumbnail letterboxed to a tenth per bar is`() {
        // A real art-track thumbnail: 360px square with 43 and 44 black rows around the picture.
        assertTrue(BitmapBorderTrim.shouldCropSquareLetterbox(
                width = 360, height = 360, top = 43, bottom = 315, left = 0, right = 359))
    }

    @Test
    fun `two black bars are one bar`() {
        assertTrue(BitmapBorderTrim.letterboxBarsMatch(360, 360, 43, 315, 0, 359) { _, _ ->
            pixel(0, 0, 0)
        })
        // Noise, not a different bar.
        assertTrue(BitmapBorderTrim.isSameBarColour(pixel(0, 0, 0), pixel(7, 3, 2)))
    }

    /**
     * Every row of a vertical gradient is flat, so the scan reads a violet-to-pink sleeve with a
     * glyph in the middle as two deep bars and no side bars - the signature of a letterbox. The
     * bars are not the same colour, though: that is what saves YouTube Music's Liked Music cover,
     * which was being cut to a square half its size.
     */
    @Test
    fun `a vertical gradient is not two letterbox bars`() {
        fun gradient(y: Int): Int {
            val t = y / 479f
            return pixel(
                    (152 + (240 - 152) * t).toInt(),
                    (110 + (95 - 110) * t).toInt(),
                    (237 + (183 - 237) * t).toInt())
        }
        assertFalse(BitmapBorderTrim.letterboxBarsMatch(480, 480, 110, 355, 0, 479) { _, y ->
            gradient(y)
        })
    }

    @Test
    fun `a horizontal gradient is not two pillarbox bars either`() {
        assertFalse(BitmapBorderTrim.letterboxBarsMatch(480, 480, 0, 479, 110, 355) { x, _ ->
            pixel(x / 2, 60, 255 - x / 2)
        })
    }

    @Test
    fun `the average of a flat line is its colour`() {
        val line = List(40) { pixel(10, 200, 30) }
        assertTrue(BitmapBorderTrim.averageColour(0, line.lastIndex) { line[it] } == pixel(10, 200, 30))
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
