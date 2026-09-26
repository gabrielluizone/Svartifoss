package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawnProgressTest {

    @Test
    fun `the ends of the track are drawn exactly`() {
        assertEquals(0f, DrawnProgress.quantize(0f), 0f)
        assertEquals(1f, DrawnProgress.quantize(1f), 0f)
    }

    @Test
    fun `out of range progress is clamped`() {
        assertEquals(0f, DrawnProgress.quantize(-0.2f), 0f)
        assertEquals(1f, DrawnProgress.quantize(1.4f), 0f)
    }

    /** One animation frame of a three-minute track moves the ring by far less than a step. */
    @Test
    fun `a single frame of ordinary playback is not a visible move`() {
        val oneFrame = 1f / (180f * 60f)
        val start = 0.5f

        assertFalse(DrawnProgress.visiblyMoved(start, start + oneFrame))
    }

    @Test
    fun `the drawn value does keep up with playback`() {
        val oneSecond = 1f / 180f
        val start = 0.25f

        assertTrue(DrawnProgress.visiblyMoved(start, start + oneSecond))
    }

    @Test
    fun `a step is about half a pixel of ring on a large round watch`() {
        val circumferencePx = Math.PI * 480

        assertTrue(circumferencePx / DrawnProgress.STEPS < 0.51)
    }
}
