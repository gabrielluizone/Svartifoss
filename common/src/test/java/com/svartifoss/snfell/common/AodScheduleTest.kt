package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodScheduleTest {

    @Test
    fun `off never dims and unknown values are off`() {
        (0..23).forEach { assertEquals(1f, AodNightDim.OFF.factorAt(it), 0f) }
        assertEquals(AodNightDim.OFF, AodNightDim.fromPref(null))
        assertEquals(AodNightDim.OFF, AodNightDim.fromPref("moon"))
    }

    @Test
    fun `the night window wraps midnight and has exact edges`() {
        val night = AodNightDim.NIGHT
        assertFalse(night.covers(21))
        assertTrue(night.covers(22))
        assertTrue(night.covers(23))
        assertTrue(night.covers(0))
        assertTrue(night.covers(6))
        assertFalse("07:00 is the first bright hour", night.covers(7))
        assertEquals(0.5f, night.factorAt(2), 0f)
        assertEquals(1f, night.factorAt(12), 0f)
    }

    @Test
    fun `the long night starts at dusk and ends after dawn`() {
        val long = AodNightDim.LONG_NIGHT
        assertFalse(long.covers(18))
        assertTrue(long.covers(19))
        assertTrue(long.covers(7))
        assertFalse(long.covers(8))
        assertEquals(0.6f, long.factorAt(20), 0f)
    }

    @Test
    fun `an out of range hour is clamped rather than crashing`() {
        assertTrue(AodNightDim.NIGHT.covers(99))   // clamps to 23
        assertTrue(AodNightDim.NIGHT.covers(-4))   // clamps to 0
    }

    @Test
    fun `the burn-in shift grows from gentle to wide and unknown is standard`() {
        assertTrue(AodShift.GENTLE.maxPixels < AodShift.STANDARD.maxPixels)
        assertTrue(AodShift.STANDARD.maxPixels < AodShift.WIDE.maxPixels)
        assertEquals(5, AodShift.STANDARD.maxPixels)   // what the display has always used
        assertEquals(AodShift.STANDARD, AodShift.fromPref(null))
        assertEquals(AodShift.WIDE, AodShift.fromPref("wide"))
    }

    @Test
    fun `standalone styles are recognised`() {
        assertTrue(AodStandaloneStyles.isStandalone("chrono"))
        assertTrue(AodStandaloneStyles.isStandalone("minute_ring"))
        assertTrue(AodStandaloneStyles.isStandalone("hex_frame"))
        assertFalse(AodStandaloneStyles.isStandalone("expressive"))
    }
}
