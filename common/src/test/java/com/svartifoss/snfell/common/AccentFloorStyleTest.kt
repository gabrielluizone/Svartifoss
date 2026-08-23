package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentFloorStyleTest {

    @Test
    fun `every preference value round-trips`() {
        AccentFloorStyle.entries.forEach {
            assertEquals(it, AccentFloorStyle.fromPreference(it.preferenceValue))
        }
    }

    @Test
    fun `unknown values resolve to off rather than to something visible`() {
        // A value can arrive from an imported backup or a newer build on the other device.
        // Inventing a strong treatment on a face nobody chose it for is the worse failure.
        assertEquals(AccentFloorStyle.OFF, AccentFloorStyle.fromPreference("glow"))
        assertEquals(AccentFloorStyle.OFF, AccentFloorStyle.fromPreference(""))
        assertEquals(AccentFloorStyle.OFF, AccentFloorStyle.fromPreference(null))
    }

    @Test
    fun `values are matched case and whitespace insensitively`() {
        assertEquals(AccentFloorStyle.BOLD, AccentFloorStyle.fromPreference("  BOLD "))
    }

    @Test
    fun `off is the only invisible style`() {
        assertFalse(AccentFloorStyle.OFF.isVisible)
        assertTrue(AccentFloorStyle.SOFT.isVisible)
        assertTrue(AccentFloorStyle.STANDARD.isVisible)
        assertTrue(AccentFloorStyle.BOLD.isVisible)
    }

    @Test
    fun `stronger styles reach both higher and denser`() {
        // Pins the ordering the names promise: a user moving Soft to Bold must get more of the
        // effect on every axis, not more on one and less on another.
        val ramp = listOf(AccentFloorStyle.SOFT, AccentFloorStyle.STANDARD, AccentFloorStyle.BOLD)
        ramp.zipWithNext { weaker, stronger ->
            assertTrue(stronger.maxAlpha > weaker.maxAlpha)
            assertTrue(stronger.innerStop < weaker.innerStop)
            assertTrue(stronger.maskStart < weaker.maskStart)
        }
    }
}
