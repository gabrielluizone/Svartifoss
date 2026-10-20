package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterShapeTest {

    @Test
    fun `the wave is flat when silent and bends more as the level rises`() {
        assertEquals(0f, MeterShape.waveAmplitude(0f), 0f)
        var previous = 0f
        listOf(0.05f, 0.25f, 0.5f, 0.75f, 1f).forEach { level ->
            val amplitude = MeterShape.waveAmplitude(level)
            assertTrue("amplitude at $level must rise", amplitude > previous)
            previous = amplitude
        }
        assertEquals(1f, MeterShape.waveAmplitude(1f), 1e-6f)
    }

    @Test
    fun `an audible level never draws a perfectly straight wave`() {
        // A straight line at 5 percent would read as a plain bar and the style would look broken.
        assertTrue(MeterShape.waveAmplitude(0.05f) >= MeterShape.WAVE_FLOOR)
    }

    @Test
    fun `an inward wave touches the track and never leaves its inside`() {
        val samples = (0..200).map { MeterShape.waveOffset(it / 200f, 5f, inwardOnly = true) }
        assertEquals(0f, samples.first(), 1e-5f)
        assertTrue(samples.all { it <= 1e-5f && it >= -1.0001f })
        assertTrue("it must actually move", samples.minOrNull()!! < -0.9f)
    }

    @Test
    fun `a symmetric wave swings both ways and eases in from the baseline`() {
        val samples = (0..400).map { MeterShape.waveOffset(it / 400f, 6f) }
        assertEquals(0f, samples.first(), 1e-5f)
        assertTrue(samples.any { it > 0.9f })
        assertTrue(samples.any { it < -0.9f })
        // Inside the easing window the swing is held down.
        assertTrue(samples.take(8).all { kotlin.math.abs(it) < 0.2f })
    }

    @Test
    fun `the wave phase repeats once per period and never reaches one`() {
        assertEquals(0f, MeterShape.wavePhase(0L), 0f)
        assertEquals(0.5f, MeterShape.wavePhase(MeterShape.WAVE_PERIOD_MS / 2), 1e-3f)
        assertEquals(0f, MeterShape.wavePhase(MeterShape.WAVE_PERIOD_MS * 3), 0f)
        (0L..MeterShape.WAVE_PERIOD_MS * 2 step 37L).forEach {
            val phase = MeterShape.wavePhase(it)
            assertTrue("$it -> $phase", phase >= 0f && phase < 1f)
        }
    }

    @Test
    fun `the wave clock advances only while moving and resumes without a jump`() {
        val clock = WavePhaseClock()
        // The first moving frame only records the time; there is nothing to measure yet.
        assertEquals(0f, clock.advance(moving = true, nowMs = 1_000L), 0f)
        val step = clock.advance(moving = true, nowMs = 1_000L + WavePhaseClock.FRAME_MS)
        assertEquals(WavePhaseClock.FRAME_MS.toFloat() / MeterShape.WAVE_PERIOD_MS, step, 1e-5f)
        // Stopping holds the phase where it stood.
        assertEquals(step, clock.advance(moving = false, nowMs = 9_000L), 0f)
        // Resuming much later starts from there rather than from where the clock would put it.
        assertEquals(step, clock.advance(moving = true, nowMs = 60_000L), 0f)
    }

    @Test
    fun `a long gap between two frames moves the wave by one bounded step`() {
        val clock = WavePhaseClock()
        clock.advance(moving = true, nowMs = 0L + 1L)
        val phase = clock.advance(moving = true, nowMs = 5_000L)
        assertEquals(WavePhaseClock.MAX_STEP_MS.toFloat() / MeterShape.WAVE_PERIOD_MS, phase, 1e-5f)
    }

    @Test
    fun `a travelling wave moves its crests along the track and stays anchored at the start`() {
        val cycles = 5f
        // The first crest past the easing window: the first sample that is higher than both of its
        // neighbours.
        fun crest(phase: Float): Float {
            val ts = (150..950).map { it / 1000f }
            val values = ts.map { MeterShape.waveOffset(it, cycles, phase = phase) }
            val index = (1 until values.size - 1).first {
                values[it] > values[it - 1] && values[it] >= values[it + 1]
            }
            return ts[index]
        }
        // Growing the phase slides a crest toward the loud end: the one that was at t is, a quarter
        // of a wavelength later, a quarter of a cycle further along (or the next crest, a whole
        // cycle on, once it has run off the front of the window).
        val before = crest(0f)
        val after = crest(0.25f)
        val cycleLength = 1f / cycles
        val shifted = (after - before + cycleLength) % cycleLength
        assertEquals(0.25f * cycleLength, shifted, 0.01f)
        // Whatever the phase, the track's own start is not swung away from.
        listOf(0f, 0.3f, 0.7f).forEach { phase ->
            assertEquals(0f, MeterShape.waveOffset(0f, cycles, phase = phase), 1e-6f)
            assertEquals(0f, MeterShape.waveOffset(0f, cycles, inwardOnly = true, phase = phase), 1e-6f)
        }
    }

    @Test
    fun `a whole wavelength of phase is the same wave`() {
        listOf(0.1f, 0.4f, 0.9f).forEach { t ->
            assertEquals(
                    MeterShape.waveOffset(t, 4f, phase = 0f),
                    MeterShape.waveOffset(t, 4f, phase = 1f), 1e-4f)
        }
    }

    @Test
    fun `a track always gets at least one crest`() {
        assertEquals(1f, MeterShape.waveCycles(5f, 40f), 0f)
        assertEquals(4f, MeterShape.waveCycles(120f, 30f), 1e-6f)
    }

    @Test
    fun `the ruler has major marks at the quarters and minor ones between`() {
        assertEquals(21, MeterShape.RULER_MARKS)
        val majors = (0 until MeterShape.RULER_MARKS)
                .filter { MeterShape.rulerMark(it) == MeterShape.RulerMark.MAJOR }
        assertEquals(listOf(0, 5, 10, 15, 20), majors)
        assertEquals(0.25f, MeterShape.rulerPosition(5), 1e-6f)
        assertEquals(1f, MeterShape.rulerPosition(20), 0f)
        assertTrue(MeterShape.rulerMarkLength(MeterShape.RulerMark.MAJOR) >
                MeterShape.rulerMarkLength(MeterShape.RulerMark.MINOR))
    }

    @Test
    fun `a mark is lit when the level reaches it`() {
        assertTrue(MeterShape.rulerMarkLit(10, 0.5f))    // exactly 50 percent lights the 50 mark
        assertFalse(MeterShape.rulerMarkLit(11, 0.5f))
        assertTrue(MeterShape.rulerMarkLit(0, 0f))       // the zero mark is always lit
        assertTrue(MeterShape.rulerMarkLit(20, 1f))
    }

    @Test
    fun `basalt fills exactly one column partially at a time`() {
        val count = MeterShape.BASALT_COLUMNS
        listOf(0f, 0.1f, 0.3f, 0.5f, 0.77f, 1f).forEach { level ->
            val fills = (0 until count).map { MeterShape.basaltFill(it, level) }
            assertTrue("$level: at most one partial column",
                    fills.count { it > 0f && it < 1f } <= 1)
            // Together the columns hold exactly the level, scaled to the number of columns.
            assertEquals(level * count, fills.sum(), 1e-4f)
            // Lit from the quiet end: the fills never rise again once they have fallen.
            assertEquals(fills.sortedDescending(), fills)
        }
    }

    @Test
    fun `basalt columns and their gaps exactly fill the width`() {
        val total = 150f
        val width = MeterShape.basaltColumnWidth(total)
        val lastRight = MeterShape.basaltColumnLeft(
                10f, total, MeterShape.BASALT_COLUMNS - 1) + width
        assertEquals(10f + total, lastRight, 1e-3f)
        assertEquals(10f, MeterShape.basaltColumnLeft(10f, total, 0), 0f)
    }

    @Test
    fun `basalt is ragged but the loud end is the tallest`() {
        val profile = MeterShape.BASALT_PROFILE
        assertEquals(MeterShape.BASALT_COLUMNS, profile.size)
        assertEquals(1f, profile.maxOrNull()!!, 0f)
        assertEquals(1f, profile.last(), 0f)
        // Ragged on purpose: a monotonic ramp would just be a signal-strength icon.
        assertTrue((1 until profile.size).any { profile[it] < profile[it - 1] })
    }

    @Test
    fun `thickness is bounded and never reserves less room than the default`() {
        assertEquals(0.5f, MeterShape.thicknessScale(10), 0f)
        assertEquals(1.8f, MeterShape.thicknessScale(500), 0f)
        assertEquals(1f, MeterShape.thicknessScale(100), 0f)
        // Thinning the stroke must not move the arc inwards.
        assertEquals(1f, MeterShape.reservedScale(60), 0f)
        assertEquals(1.5f, MeterShape.reservedScale(150), 0f)
    }

    @Test
    fun `an arc track starts and ends where its angles say`() {
        val track = MeterTrack.Arc(100f, 100f, 50f, 0f, 90f)
        val point = FloatArray(2)
        val normal = FloatArray(2)
        track.pointAt(0f, point)
        assertEquals(150f, point[0], 1e-3f)
        assertEquals(100f, point[1], 1e-3f)
        track.pointAt(1f, point)
        assertEquals(100f, point[0], 1e-3f)
        assertEquals(150f, point[1], 1e-3f)
        track.normalAt(1f, normal)
        assertEquals(0f, normal[0], 1e-3f)
        assertEquals(1f, normal[1], 1e-3f)
        assertEquals(Math.PI.toFloat() * 25f, track.length, 1e-2f)
    }

    @Test
    fun `a negative sweep runs the other way and has the same length`() {
        val forward = MeterTrack.Arc(0f, 0f, 40f, 0f, 90f)
        val backward = MeterTrack.Arc(0f, 0f, 40f, 0f, -90f)
        assertEquals(forward.length, backward.length, 1e-3f)
        val point = FloatArray(2)
        backward.pointAt(1f, point)
        assertEquals(0f, point[0], 1e-3f)
        assertEquals(-40f, point[1], 1e-3f)
    }

    @Test
    fun `a line track has a normal at a quarter turn from its direction`() {
        val track = MeterTrack.Line(0f, 100f, 0f, 0f)   // bottom to top
        val normal = FloatArray(2)
        track.normalAt(0.5f, normal)
        assertEquals(-1f, normal[0], 1e-6f)
        assertEquals(0f, normal[1], 1e-6f)
        assertEquals(100f, track.length, 1e-6f)
    }

    @Test
    fun `a degenerate line does not divide by zero`() {
        val normal = FloatArray(2)
        MeterTrack.Line(5f, 5f, 5f, 5f).normalAt(0f, normal)
        assertNull(normal.firstOrNull { it.isNaN() })
    }
}
