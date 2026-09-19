package com.svartifoss.snfell.watch.input

import com.svartifoss.snfell.watch.input.PinchCalibrationTimeline.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinchCalibrationTimelineTest {
    private fun kindAt(ms: Long) = PinchCalibrationTimeline.stepAt(ms).kind

    @Test
    fun `the sequence keeps the windows the fitter was designed around`() {
        assertEquals(Kind.COUNTDOWN, kindAt(0))
        assertEquals(Kind.COUNTDOWN, kindAt(2_999))
        assertEquals(Kind.REST, kindAt(3_000))
        assertEquals(Kind.REST, kindAt(6_999))
        assertEquals(Kind.PINCH, kindAt(7_000))
        assertEquals(Kind.WAIT, kindAt(10_000))
        assertEquals(Kind.PINCH, kindAt(12_000))
        assertEquals(Kind.WAIT, kindAt(36_999))
        assertEquals(Kind.PREPARE_MOVE, kindAt(37_000))
        assertEquals(Kind.MOVE, kindAt(39_000))
        assertEquals(Kind.MOVE, kindAt(44_999))
        assertEquals(Kind.DONE, kindAt(45_000))
        assertEquals(45_000L, PinchCalibrationTimeline.TOTAL_MS)
    }

    @Test
    fun `every attempt is numbered, and its pause carries the same number`() {
        for (attempt in 1..PinchCalibrationTimeline.ATTEMPTS) {
            val start = 7_000L + (attempt - 1) * 5_000L
            val pinch = PinchCalibrationTimeline.stepAt(start + 100)
            val wait = PinchCalibrationTimeline.stepAt(start + 3_100)
            assertEquals(Kind.PINCH, pinch.kind)
            assertEquals(attempt, pinch.attempt)
            assertEquals(Kind.WAIT, wait.kind)
            assertEquals(attempt, wait.attempt)
        }
        assertEquals(0, PinchCalibrationTimeline.stepAt(4_000).attempt)
    }

    @Test
    fun `progress and seconds left never read as finished while a step is running`() {
        var previous = -1f
        for (ms in 0L until PinchCalibrationTimeline.TOTAL_MS step 250) {
            val step = PinchCalibrationTimeline.stepAt(ms)
            assertTrue(step.secondsLeft >= 1)
            assertTrue(step.stepProgress in 0f..1f)
            assertTrue("overall progress must not go backwards", step.overallProgress >= previous)
            previous = step.overallProgress
        }
        assertEquals(3, PinchCalibrationTimeline.stepAt(0).secondsLeft)
        assertEquals(1, PinchCalibrationTimeline.stepAt(2_500).secondsLeft)
    }
}
