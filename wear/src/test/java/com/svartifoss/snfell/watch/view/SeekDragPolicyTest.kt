package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekDragPolicyTest {

    private val ringRadius = 96f
    private val touchBand = 12f

    @Test
    fun `a finger on the ring is never inside the cancel zone`() {
        // The band is where the whole gesture lives; if the zone reached it, an ordinary scrub
        // would arm its own cancel and the seek would fail with nothing on screen explaining why.
        assertFalse(SeekDragPolicy.isInsideCancelZone(ringRadius, ringRadius, touchBand))
        assertFalse(
                SeekDragPolicy.isInsideCancelZone(ringRadius - touchBand, ringRadius, touchBand))
    }

    @Test
    fun `the middle of the screen cancels`() {
        assertTrue(SeekDragPolicy.isInsideCancelZone(0f, ringRadius, touchBand))
        assertTrue(SeekDragPolicy.isInsideCancelZone(ringRadius * .3f, ringRadius, touchBand))
    }

    @Test
    fun `a shaky finger just off the ring does not cancel`() {
        // Drifting a few pixels inward is a shaky wrist, not a change of mind.
        assertFalse(
                SeekDragPolicy.isInsideCancelZone(ringRadius * .7f, ringRadius, touchBand))
    }

    @Test
    fun `the zone collapses rather than overlapping the band on a tiny ring`() {
        // Disabling the gesture is the safe answer: a ring this small has no room for the
        // affordance either, and an overlapping zone would break seeking itself.
        val tiny = 20f
        assertEquals(0f, SeekDragPolicy.cancelZoneRadius(tiny, touchBand), 0f)
        assertFalse(SeekDragPolicy.isInsideCancelZone(0f, tiny, touchBand))
    }

    @Test
    fun `the preview walks back to the live position as the cancel reveals`() {
        val finger = 0.9f
        val origin = 0.2f
        assertEquals(finger, SeekDragPolicy.previewProgress(finger, origin, 0f), 1e-4f)
        assertEquals(0.55f, SeekDragPolicy.previewProgress(finger, origin, 0.5f), 1e-4f)
        assertEquals(origin, SeekDragPolicy.previewProgress(finger, origin, 1f), 1e-4f)
    }

    @Test
    fun `an out of range reveal cannot push the preview past either end`() {
        assertEquals(0.9f, SeekDragPolicy.previewProgress(0.9f, 0.2f, -1f), 1e-4f)
        assertEquals(0.2f, SeekDragPolicy.previewProgress(0.9f, 0.2f, 4f), 1e-4f)
    }
}
