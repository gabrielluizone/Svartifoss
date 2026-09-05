package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeScreenshotsTest {

    @Test
    fun `a watch-sized screenshot is re-encoded rather than resampled`() {
        // The three common round framebuffers. 450 is the target, so only the larger one moves.
        assertEquals(450, CommunityThemeScreenshots.targetSize(450))
        assertEquals(450, CommunityThemeScreenshots.targetSize(454))
        assertEquals(396, CommunityThemeScreenshots.targetSize(396))
    }

    @Test
    fun `a small screenshot is never enlarged`() {
        // Enlarging invents detail and only costs bytes, so a smaller watch keeps its own size.
        assertEquals(192, CommunityThemeScreenshots.targetSize(192))
        assertEquals(
                CommunityThemeScreenshots.MIN_PIXELS,
                CommunityThemeScreenshots.targetSize(CommunityThemeScreenshots.MIN_PIXELS))
    }

    @Test
    fun `a source too small to submit is refused here rather than at the write`() {
        assertEquals(0, CommunityThemeScreenshots.targetSize(CommunityThemeScreenshots.MIN_PIXELS - 1))
        assertEquals(0, CommunityThemeScreenshots.targetSize(0))
        assertEquals(0, CommunityThemeScreenshots.targetSize(-10))
    }

    @Test
    fun `sampling bounds a gallery-sized picture without softening it`() {
        // A 12-megapixel photograph decodes at a quarter of its edge and still clears the target.
        assertEquals(8, CommunityThemeScreenshots.sampleSize(4032))
        assertEquals(2, CommunityThemeScreenshots.sampleSize(1080))
        // Never sampled past the target: the scale that follows does that better.
        assertEquals(1, CommunityThemeScreenshots.sampleSize(899))
        assertEquals(2, CommunityThemeScreenshots.sampleSize(900))
        assertEquals(1, CommunityThemeScreenshots.sampleSize(450))
        assertEquals(1, CommunityThemeScreenshots.sampleSize(64))
    }

    @Test
    fun `sampling always leaves at least the target edge available`() {
        for (side in listOf(450, 451, 900, 901, 1799, 1800, 4032, 5000)) {
            val sampled = side / CommunityThemeScreenshots.sampleSize(side)
            assertTrue(
                    "sampling $side left $sampled, below the target edge",
                    sampled >= CommunityThemeScreenshots.TARGET_PIXELS)
        }
    }

    @Test
    fun `the crop is centred on either axis and free on a square`() {
        // Landscape: the sides are trimmed evenly.
        assertEquals(75, CommunityThemeScreenshots.cropLeft(600, 450))
        assertEquals(0, CommunityThemeScreenshots.cropTop(600, 450))
        // Portrait: the top and bottom are.
        assertEquals(0, CommunityThemeScreenshots.cropLeft(450, 800))
        assertEquals(175, CommunityThemeScreenshots.cropTop(450, 800))
        // An already-square watch screenshot, which is the ordinary case, is untouched.
        assertEquals(0, CommunityThemeScreenshots.cropLeft(450, 450))
        assertEquals(0, CommunityThemeScreenshots.cropTop(450, 450))
        // An odd remainder cannot push the crop off the image.
        assertEquals(0, CommunityThemeScreenshots.cropLeft(451, 450))
        assertEquals(0, CommunityThemeScreenshots.cropTop(450, 451))
    }

    @Test
    fun `the encoding envelope matches what the rules will accept`() {
        assertTrue(CommunityThemeScreenshots.isSubmittableEncoding("UklGRhoAAABXRUJQ"))
        assertTrue(CommunityThemeScreenshots.isSubmittableEncoding("UklGRhoAAABXRUJ="))
        assertTrue(CommunityThemeScreenshots.isSubmittableEncoding("UklGRhoAAABXRU=="))
        assertFalse(CommunityThemeScreenshots.isSubmittableEncoding(""))
        assertFalse(CommunityThemeScreenshots.isSubmittableEncoding("AA"))
        assertFalse(CommunityThemeScreenshots.isSubmittableEncoding("not base64!"))
        // Line-wrapped base64 is the trap: Base64.encodeToString wraps unless NO_WRAP is passed,
        // and the rules' character class refuses the newline.
        assertFalse(CommunityThemeScreenshots.isSubmittableEncoding("UklGRhoA\nAABXRUJQ"))
        assertFalse(CommunityThemeScreenshots.isSubmittableEncoding(
                "A".repeat(CommunityThemeScreenshots.MAX_BASE64_LENGTH + 1)))
    }

    @Test
    fun `the surface vocabulary matches the one the publisher and the rules carry`() {
        assertEquals(listOf("player"), CommunityThemeScreenshots.SURFACES)
    }

    @Test
    fun `the quality ladder descends and starts where an ordinary screenshot fits`() {
        val ladder = CommunityThemeScreenshots.QUALITY_LADDER
        assertTrue("the ladder needs a first rung", ladder.isNotEmpty())
        assertEquals(ladder.sortedDescending(), ladder)
        assertTrue(ladder.all { it in 1..100 })
    }
}
