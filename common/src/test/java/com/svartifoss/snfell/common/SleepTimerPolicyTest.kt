package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerPolicyTest {

    private fun minutes(value: Double): Long = (value * 60_000).toLong()

    @Test
    fun `a press with no timer running sets the shortest preset`() {
        assertEquals(15, SleepTimerPolicy.nextMinutes(0L))
        assertEquals(15, SleepTimerPolicy.nextMinutes(-5L))
    }

    @Test
    fun `presses walk up the ladder and the last one switches it off`() {
        assertEquals(30, SleepTimerPolicy.nextMinutes(minutes(15.0)))
        assertEquals(45, SleepTimerPolicy.nextMinutes(minutes(30.0)))
        assertEquals(60, SleepTimerPolicy.nextMinutes(minutes(45.0)))
        assertEquals(0, SleepTimerPolicy.nextMinutes(minutes(60.0)))
    }

    @Test
    fun `a press steps from the minutes the label shows, not from the preset it started at`() {
        // Started at 30, a minute and a half later: the chip reads 29 min, so a press tops it back
        // up to 30 - the first rung above the number on screen - and the next one goes on to 45.
        assertEquals(30, SleepTimerPolicy.nextMinutes(minutes(28.5)))
        assertEquals(45, SleepTimerPolicy.nextMinutes(minutes(30.0)))
        // Started at 60, at 59:59: still reads 60, so the next press turns it off.
        assertEquals(0, SleepTimerPolicy.nextMinutes(minutes(59.99)))
        // 14:30 left reads 15 min, which is a preset already, so the press moves on from it.
        assertEquals(30, SleepTimerPolicy.nextMinutes(minutes(14.5)))
    }

    @Test
    fun `the time left is rounded up so it never reads zero while there is time`() {
        assertEquals(0, SleepTimerPolicy.minutesLeft(0L))
        assertEquals(1, SleepTimerPolicy.minutesLeft(1L))
        assertEquals(1, SleepTimerPolicy.minutesLeft(60_000L))
        assertEquals(2, SleepTimerPolicy.minutesLeft(60_001L))
        assertEquals(30, SleepTimerPolicy.minutesLeft(minutes(29.1)))
    }

    @Test
    fun `a timer longer than any preset is switched off by the next press`() {
        // Not reachable from this chip, but a phone build with a longer preset must not wedge it.
        assertEquals(0, SleepTimerPolicy.nextMinutes(minutes(90.0)))
    }
}
