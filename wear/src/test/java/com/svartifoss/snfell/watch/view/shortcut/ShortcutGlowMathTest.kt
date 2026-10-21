package com.svartifoss.snfell.watch.view.shortcut

import com.svartifoss.snfell.common.AdaptiveTextContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pins the backdrop's arithmetic: the blur does what a blur does (spreads, conserves light, treats
 * a transparent pixel as black), the tone holds highlights down without changing colour, and the
 * ground under a line of text is read through the same masks the screen draws.
 */
class ShortcutGlowMathTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 0xFF) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun channel(pixel: Int, shift: Int) = (pixel shr shift) and 0xFF

    private fun solid(width: Int, height: Int, color: Int) = IntArray(width * height) { color }

    @Test
    fun `the kernel is a normalised, symmetric bell`() {
        val kernel = ShortcutGlowMath.kernel(2f)
        assertEquals(1f, kernel.sum(), 1e-4f)
        assertEquals(1, kernel.size % 2)
        for (i in kernel.indices) assertEquals(kernel[i], kernel[kernel.size - 1 - i], 1e-6f)
        assertEquals(kernel.max(), kernel[kernel.size / 2], 0f)
    }

    @Test
    fun `no sigma is the identity`() {
        assertEquals(listOf(1f), ShortcutGlowMath.kernel(0f).toList())
        assertEquals(listOf(1f), ShortcutGlowMath.kernel(-3f).toList())
        val pixels = intArrayOf(argb(10, 20, 30), argb(200, 100, 50), argb(0, 0, 0), argb(255, 255, 255))
        assertEquals(pixels.toList(), ShortcutGlowMath.blur(pixels, 2, 2, 0f).toList())
    }

    @Test
    fun `a flat picture stays flat`() {
        val color = argb(120, 60, 200)
        val blurred = ShortcutGlowMath.blur(solid(16, 16, color), 16, 16, 3f)
        blurred.forEach { assertEquals(color, it) }
    }

    @Test
    fun `an impulse spreads symmetrically and keeps its light`() {
        val size = 41
        val pixels = solid(size, size, argb(0, 0, 0))
        val middle = size / 2
        pixels[middle * size + middle] = argb(255, 255, 255)
        val blurred = ShortcutGlowMath.blur(pixels, size, size, 3f)

        val centre = channel(blurred[middle * size + middle], 16)
        assertTrue("the middle stays the brightest", blurred.all { channel(it, 16) <= centre })
        for (d in 1..6) {
            assertEquals(channel(blurred[middle * size + middle - d], 16),
                    channel(blurred[middle * size + middle + d], 16))
            assertEquals(channel(blurred[(middle - d) * size + middle], 16),
                    channel(blurred[(middle + d) * size + middle], 16))
        }
        // 255 of light went in. Rounding each pixel to a whole level loses a little at the tails.
        val light = blurred.sumOf { channel(it, 16) }
        assertTrue("light kept: $light", light in 230..280)
    }

    @Test
    fun `a hard edge becomes a gradient`() {
        val size = 32
        val pixels = IntArray(size * size) { i ->
            if (i % size < size / 2) argb(0, 0, 0) else argb(255, 255, 255)
        }
        val blurred = ShortcutGlowMath.blur(pixels, size, size, 3f)
        val row = size / 2
        val levels = (0 until size).map { channel(blurred[row * size + it], 16) }
        assertTrue("black far from the edge", levels.first() < 5)
        assertTrue("white far from the edge", levels.last() > 250)
        assertTrue("monotone across the edge", levels.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("the edge itself is a mid level", levels[size / 2 - 1] in 60..200)
    }

    @Test
    fun `a transparent pixel is black, whatever colour it carries`() {
        val pixels = solid(8, 8, argb(255, 255, 255, a = 0))
        ShortcutGlowMath.blur(pixels, 8, 8, 2f).forEach { assertEquals(argb(0, 0, 0), it) }
        // Half-transparent white is half as bright as white on the black screen under it.
        val half = ShortcutGlowMath.blur(solid(8, 8, argb(255, 255, 255, a = 128)), 8, 8, 2f)
        half.forEach { assertEquals(128, channel(it, 16)) }
    }

    @Test
    fun `the result is always opaque`() {
        ShortcutGlowMath.blur(solid(4, 4, argb(9, 9, 9, a = 7)), 4, 4, 1f)
                .forEach { assertEquals(0xFF, (it ushr 24) and 0xFF) }
    }

    @Test
    fun `no saturation change, no curve and no highlight hold is the identity`() {
        val pixels = intArrayOf(argb(10, 20, 30), argb(200, 100, 50), argb(255, 255, 255), argb(0, 0, 0))
        val toned = ShortcutGlowMath.tone(pixels, saturation = 1f, gamma = 1f, ceiling = 1f)
        pixels.indices.forEach { i ->
            listOf(16, 8, 0).forEach { shift ->
                assertTrue("pixel $i", abs(channel(toned[i], shift) - channel(pixels[i], shift)) <= 1)
            }
        }
    }

    @Test
    fun `the curve leaves black and white where they are and takes the middle down`() {
        val toned = ShortcutGlowMath.tone(
                intArrayOf(argb(0, 0, 0), argb(255, 255, 255), argb(128, 128, 128)),
                saturation = 1f, gamma = 1.5f, ceiling = 1f)
        assertEquals(argb(0, 0, 0), toned[0])
        assertEquals(argb(255, 255, 255), toned[1])
        assertTrue("the middle goes down: ${channel(toned[2], 16)}", channel(toned[2], 16) in 40..110)
    }

    @Test
    fun `saturation leaves a grey grey and makes a colour stronger`() {
        val grey = ShortcutGlowMath.tone(intArrayOf(argb(90, 90, 90)), saturation = 1.8f, gamma = 1f)[0]
        assertEquals(channel(grey, 16), channel(grey, 8))
        assertEquals(channel(grey, 8), channel(grey, 0))

        val boosted = ShortcutGlowMath.tone(intArrayOf(argb(200, 100, 100)), saturation = 1.5f, gamma = 1f,
                ceiling = 1f)[0]
        assertTrue("more spread between red and green: ${channel(boosted, 16)} / ${channel(boosted, 8)}",
                channel(boosted, 16) - channel(boosted, 8) > 200 - 100)
    }

    @Test
    fun `an extreme saturation stays inside the range and opaque`() {
        val toned = ShortcutGlowMath.tone(
                intArrayOf(argb(255, 0, 0), argb(0, 255, 0), argb(0, 0, 255), argb(250, 250, 5, a = 3)),
                saturation = 4f, gamma = 1.55f)
        toned.forEach { pixel ->
            assertEquals(0xFF, (pixel ushr 24) and 0xFF)
            listOf(16, 8, 0).forEach { assertTrue(channel(pixel, it) in 0..255) }
        }
    }

    @Test
    fun `the highlight hold is smooth, rising and never past its ceiling`() {
        val knee = .2f
        val ceiling = .32f
        assertEquals(.1f, ShortcutGlowMath.heldLuminance(.1f, knee, ceiling), 0f)
        assertEquals(knee, ShortcutGlowMath.heldLuminance(knee, knee, ceiling), 1e-6f)
        val held = (0..100).map { ShortcutGlowMath.heldLuminance(it / 100f, knee, ceiling) }
        assertTrue("rising: $held", held.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("under the ceiling: ${held.last()}", held.all { it <= ceiling })
        // Just above the knee it still rises as fast as it did below it - no visible edge.
        val step = ShortcutGlowMath.heldLuminance(knee + .001f, knee, ceiling) - knee
        assertEquals(.001f, step, .0002f)
    }

    @Test
    fun `a white cover becomes a light grey, and colour keeps its hue`() {
        val toned = ShortcutGlowMath.tone(intArrayOf(argb(255, 255, 255), argb(230, 40, 40)))
        val white = toned[0]
        assertEquals(channel(white, 16), channel(white, 8))
        assertEquals(channel(white, 8), channel(white, 0))
        assertTrue("held under the ceiling: ${channel(white, 16)}",
                AdaptiveTextContrast.relativeLuminance(white) <= ShortcutGlowMath.HIGHLIGHT_CEILING + .01f)
        val red = toned[1]
        assertTrue("still red", channel(red, 16) > 3 * channel(red, 8) && channel(red, 16) > 3 * channel(red, 0))
    }

    @Test
    fun `the shipped constants make sense together`() {
        assertTrue(ShortcutGlowMath.GLOW_ALPHA in 0f..1f)
        assertTrue(ShortcutGlowMath.GAMMA >= 1f)
        assertTrue(ShortcutGlowMath.SATURATION >= 1f)
        assertTrue(ShortcutGlowMath.HIGHLIGHT_KNEE < ShortcutGlowMath.HIGHLIGHT_CEILING)
        assertTrue(ShortcutGlowMath.HIGHLIGHT_CEILING < 1f)
        // A subtle blur: the cover softens, and its shapes are still its shapes.
        assertTrue(ShortcutGlowMath.SIGMA_PX / ShortcutGlowMath.SAMPLE_PX in 0.02f..0.05f)
    }

    private val side = ShortcutGlowMath.SAMPLE_PX

    @Test
    fun `the ground of a black backdrop is black, and so is an empty band`() {
        val black = solid(side, side, argb(0, 0, 0))
        assertEquals(argb(0, 0, 0), ShortcutGlowMath.groundColor(black, side, .3f, .3f, .7f, .35f))
        val white = solid(side, side, argb(255, 255, 255))
        assertEquals(argb(0, 0, 0), ShortcutGlowMath.groundColor(white, side, .5f, .5f, .5f, .6f))
    }

    @Test
    fun `the ground follows the masks the screen draws`() {
        val white = solid(side, side, argb(255, 255, 255))
        fun luminance(top: Float, bottom: Float) = AdaptiveTextContrast.relativeLuminance(
                ShortcutGlowMath.groundColor(white, side, .4f, top, .6f, bottom))
        val middle = luminance(.30f, .34f)
        assertTrue("lit between the name and the buttons: $middle", middle > .5f)
        assertTrue("the clock's edge is darker", luminance(.02f, .06f) < middle)
        assertEquals("black under the buttons", 0f, luminance(.84f, .90f), 1e-4f)
    }

    @Test
    fun `the ground reads the part of the backdrop it is asked about`() {
        val halves = IntArray(side * side) { i -> if (i % side < side / 2) argb(255, 255, 255) else argb(0, 0, 0) }
        val left = ShortcutGlowMath.groundColor(halves, side, .30f, .30f, .45f, .34f)
        val right = ShortcutGlowMath.groundColor(halves, side, .55f, .30f, .70f, .34f)
        assertTrue(AdaptiveTextContrast.relativeLuminance(left) > .3f)
        assertEquals(argb(0, 0, 0), right)
    }
}
