package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPlatformTest {

    @Test
    fun `each api level maps to the generation that shipped on it`() {
        assertEquals(3, WearPlatform.wearOsGeneration(30))
        assertEquals(4, WearPlatform.wearOsGeneration(33))
        assertEquals(5, WearPlatform.wearOsGeneration(34))
        assertEquals(6, WearPlatform.wearOsGeneration(36))
        // Wear OS 4 skipped 32, so a watch reporting it is still on 3.
        assertEquals(3, WearPlatform.wearOsGeneration(32))
    }

    @Test
    fun `an unrecognised level is admitted rather than guessed at`() {
        // A diagnostic that invents a generation is worse than one that prints the number it was
        // given and says it does not know the rest.
        assertNull(WearPlatform.wearOsGeneration(29))
        val described = WearPlatform.describe(29)
        assertTrue(described, described.contains("API 29"))
        assertFalse("it must not claim a generation it cannot know",
                described.contains(Regex("""Wear OS \d""")))
    }

    /**
     * The whole reason this object exists: an ongoing activity holds the always-on player past the
     * system's watch-face timeout from Wear OS 5, and not before it. Two watches running the same
     * build therefore behave differently, which is what the report could not explain.
     */
    @Test
    fun `an ongoing activity only holds the player from wear os five`() {
        assertFalse(WearPlatform.holdsAmbientWithOngoingActivity(30))
        assertFalse(WearPlatform.holdsAmbientWithOngoingActivity(33))
        assertTrue(WearPlatform.holdsAmbientWithOngoingActivity(34))
        assertTrue(WearPlatform.holdsAmbientWithOngoingActivity(36))
        assertEquals(34, WearPlatform.AMBIENT_HOLD_MIN_API)
    }

    @Test
    fun `the description says which of the two behaviours applies`() {
        assertTrue(WearPlatform.describe(33).contains("returns to the watch face"))
        assertTrue(WearPlatform.describe(34).contains("held past the watch-face timeout"))
        // And still names the version, so a report is readable without this source open.
        assertTrue(WearPlatform.describe(34).contains("Wear OS 5"))
        assertTrue(WearPlatform.describe(33).contains("Wear OS 4"))
    }

    @Test
    fun `a newer platform than this build knows still reads as supported`() {
        // The hold is a floor, not a range: a future release must not report as unable to hold.
        assertTrue(WearPlatform.holdsAmbientWithOngoingActivity(99))
        assertEquals(6, WearPlatform.wearOsGeneration(99))
    }
}
