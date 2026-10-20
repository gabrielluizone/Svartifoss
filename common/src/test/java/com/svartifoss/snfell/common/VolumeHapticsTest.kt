package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeHapticsTest {

    @Test
    fun `an unknown or missing value is off`() {
        assertEquals(VolumeHaptics.OFF, VolumeHaptics.fromPref(null))
        assertEquals(VolumeHaptics.OFF, VolumeHaptics.fromPref("loud"))
        assertEquals(VolumeHaptics.TICKS, VolumeHaptics.fromPref("ticks"))
        assertEquals(VolumeHaptics.TICKS_AND_LIMITS, VolumeHaptics.fromPref("ticks_limits"))
    }

    @Test
    fun `off never ticks`() {
        assertFalse(VolumeHaptics.OFF.ticksBetween(0f, 1f))
        assertFalse(VolumeHaptics.OFF.hitsLimit(1f, 1f, 0.1f))
    }

    @Test
    fun `a tick fires once per step crossed and not per event`() {
        val h = VolumeHaptics.TICKS
        // Several small events inside one 5 percent step are silent.
        assertFalse(h.ticksBetween(0.41f, 0.42f))
        assertFalse(h.ticksBetween(0.42f, 0.44f))
        // The one that crosses 0.45 is not.
        assertTrue(h.ticksBetween(0.44f, 0.46f))
        // And a large jump is still one tick, not one per step.
        assertTrue(h.ticksBetween(0.10f, 0.90f))
    }

    @Test
    fun `a step boundary stored a hair low does not tick twice`() {
        val h = VolumeHaptics.TICKS
        // 0.15 as a float is 0.15000000596 - a value at the boundary must belong to one step.
        assertEquals(VolumeHaptics.stepIndex(0.15f), VolumeHaptics.stepIndex(0.1499999f))
        assertFalse(h.ticksBetween(0.15f, 0.1499999f))
    }

    @Test
    fun `ticks do not fire at the limit buzz setting boundaries only for ticks_limits`() {
        assertFalse(VolumeHaptics.TICKS.hitsLimit(1f, 1f, 0.2f))
        assertTrue(VolumeHaptics.TICKS_AND_LIMITS.hitsLimit(1f, 1f, 0.2f))
        assertTrue(VolumeHaptics.TICKS_AND_LIMITS.hitsLimit(0f, 0f, -0.2f))
    }

    @Test
    fun `a limit is only a limit when the level could not move`() {
        val h = VolumeHaptics.TICKS_AND_LIMITS
        assertFalse(h.hitsLimit(0.95f, 1f, 0.2f))   // reached the end this event: a tick, not a buzz
        assertFalse(h.hitsLimit(0.5f, 0.5f, 0.2f))  // stuck mid-range (not our business)
        assertFalse(h.hitsLimit(1f, 1f, 0f))        // nothing was asked of it
    }
}
