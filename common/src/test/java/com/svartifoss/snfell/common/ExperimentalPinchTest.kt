package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalPinchTest {
    private val calibration = PinchCalibration(0.03, 1.0, 0.5)

    @Test
    fun `stationary gravity is removed in every watch orientation after warmup`() {
        for (gravity in listOf(
                doubleArrayOf(0.0, 0.0, 9.81),
                doubleArrayOf(9.81, 0.0, 0.0),
                doubleArrayOf(0.0, -9.81, 0.0),
                doubleArrayOf(5.6638, 5.6638, 5.6638)
        )) {
            val filter = PinchMotionFilter()
            repeat(20) { index ->
                assertNull(filter.add(nanos(index * 20L), gravity[0], gravity[1], gravity[2],
                        0.0, 0.0, 0.0))
            }
            val feature = filter.add(nanos(400), gravity[0], gravity[1], gravity[2],
                    0.3, 0.4, 0.0)!!
            assertEquals(0.0, feature.acceleration, 0.000001)
            assertEquals(0.5, feature.rotation, 0.000001)
        }
    }

    @Test
    fun `filter uses elapsed sample time instead of a fixed requested rate`() {
        val fast = warmedFilter()
        val slow = warmedFilter()
        val fastPulse = fast.add(nanos(420), 1.0, 0.0, 9.81, 0.0, 0.0, 0.0)!!
        val slowPulse = slow.add(nanos(500), 1.0, 0.0, 9.81, 0.0, 0.0, 0.0)!!
        assertEquals(0.12 / 0.14, fastPulse.acceleration, 0.000001)
        assertEquals(0.12 / 0.22, slowPulse.acceleration, 0.000001)
        assertTrue(fastPulse.acceleration > slowPulse.acceleration)
    }

    @Test
    fun `invalid samples and clock discontinuities require a fresh filter warmup`() {
        val invalidSamples = listOf(
                doubleArrayOf(Double.NaN, 0.0, 9.81, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, Double.POSITIVE_INFINITY, 9.81, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 9.81, Double.NaN, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 9.81, 0.0, Double.NEGATIVE_INFINITY, 0.0)
        )
        for (sample in invalidSamples) {
            val filter = warmedFilter()
            assertNull(filter.add(nanos(420), sample[0], sample[1], sample[2],
                    sample[3], sample[4], sample[5]))
            assertNull(filter.add(nanos(440), 0.0, 0.0, 9.81, 0.0, 0.0, 0.0))
        }
        for (timestamp in listOf(0L, -1L, nanos(400), nanos(380), nanos(651))) {
            val filter = warmedFilter()
            assertNull(filter.add(timestamp, 0.0, 0.0, 9.81, 0.0, 0.0, 0.0))
            assertNull(filter.add(nanos(700), 0.0, 0.0, 9.81, 0.0, 0.0, 0.0))
        }
    }

    @Test
    fun `explicit filter reset clears gravity and warmup state`() {
        val filter = warmedFilter()
        filter.reset()
        assertNull(filter.add(nanos(420), 9.81, 0.0, 0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `a single impulse or a sustained pulse is not a double pinch`() {
        assertEquals(0, detections(mapOf(100L to 1.0)))
        assertEquals(0, detections((100L..500L step 20).associateWith { 1.0 }))
    }

    @Test
    fun `two distinct impulses trigger exactly once within the window`() {
        assertEquals(1, detections(mapOf(100L to 1.0, 400L to 1.0)))
        assertEquals(1, detections(mapOf(100L to 1.0, 220L to 1.0)))
        assertEquals(1, detections(mapOf(100L to 1.0, 900L to 1.0)))
    }

    @Test
    fun `pulses outside the configured interval do not pair`() {
        assertEquals(0, detections(mapOf(100L to 1.0, 920L to 1.0)))
        assertEquals(0, detections(mapOf(100L to 1.0, 500L to 1.0),
                settings = PinchDetectorSettings(maxPinchGapMs = 250)))
        assertEquals(1, detections(mapOf(100L to 1.0, 1300L to 1.0),
                settings = PinchDetectorSettings(maxPinchGapMs = 1500)))
    }

    @Test
    fun `ringing cannot pair consecutive short oscillations with an old first edge`() {
        assertEquals(0, detections(mapOf(100L to 1.0, 180L to 0.9, 260L to 0.8, 340L to 0.7)))
        assertEquals(0, detections(mapOf(100L to 1.0, 200L to 1.0)))
    }

    @Test
    fun `hysteresis requires the pulse to fall well below the trigger before rearming`() {
        val plateau = (100L..400L step 20).associateWith { if (it == 100L || it == 400L) 1.0 else 0.5 }
        assertEquals(0, detections(plateau))
    }

    @Test
    fun `arm rotation cancels an in progress pair`() {
        assertEquals(0, detections(mapOf(100L to 1.0, 400L to 1.0),
                rotations = mapOf(200L to 1.0)))
        assertEquals(0, detections(mapOf(100L to 1.0, 400L to 1.0),
                rotations = mapOf(100L to 1.0, 400L to 1.0)))
    }

    @Test
    fun `cooldown suppresses complete additional pairs but releases for later pairs`() {
        assertEquals(2, detections(mapOf(
                100L to 1.0, 400L to 1.0, 600L to 1.0, 900L to 1.0,
                1500L to 1.0, 1800L to 1.0
        ), durationMs = 2200L))
        assertEquals(1, detections(mapOf(
                100L to 1.0, 400L to 1.0, 1500L to 1.0, 1800L to 1.0
        ), settings = PinchDetectorSettings(cooldownMs = 3000), durationMs = 2200L))
    }

    @Test
    fun `sensitivity changes the threshold while retaining a background noise guard`() {
        val pulses = mapOf(100L to 0.45, 400L to 0.45)
        assertEquals(0, detections(pulses, settings = PinchDetectorSettings(sensitivityPercent = 100)))
        assertEquals(1, detections(pulses, settings = PinchDetectorSettings(sensitivityPercent = 200)))
        assertEquals(0, detections(mapOf(100L to 0.10, 400L to 0.10),
                settings = PinchDetectorSettings(sensitivityPercent = 200)))
        assertEquals(0, detections(mapOf(100L to 1.0, 400L to 1.0),
                settings = PinchDetectorSettings(sensitivityPercent = 50)))
    }

    @Test
    fun `explicit detector reset forgets a partial gesture`() {
        val detector = ExperimentalPinchDetector(calibration, PinchDetectorSettings())
        assertFalse(detector.add(feature(100, 1.0)))
        assertFalse(detector.add(feature(120, 0.0)))
        detector.reset()
        assertFalse(detector.add(feature(400, 1.0)))
    }

    @Test
    fun `invalid features and discontinuities cannot complete a partial gesture`() {
        val invalid = listOf(
                PinchMotionFeature(0L, 0.0, 0.0),
                PinchMotionFeature(-1L, 0.0, 0.0),
                feature(100, 0.0),
                feature(80, 0.0),
                feature(140, Double.NaN),
                feature(140, -0.1),
                feature(140, 0.0, Double.POSITIVE_INFINITY),
                feature(140, 0.0, -0.1),
                feature(500, 0.0)
        )
        for (badFeature in invalid) {
            val detector = ExperimentalPinchDetector(calibration, PinchDetectorSettings())
            assertFalse(detector.add(feature(100, 1.0)))
            assertFalse(detector.add(feature(120, 0.0)))
            assertFalse(detector.add(badFeature))
            assertFalse(detector.add(feature(600, 1.0)))
        }
    }

    @Test
    fun `calibration fits separated examples and does not let one extreme peak set the profile`() {
        val profile = PinchCalibrationFitter.fit(
                recording(0.02),
                listOf(trial(0.9), trial(1.0), trial(1.1), trial(8.0)),
                recording(0.2, rotation = 0.04)
        )!!
        assertEquals(0.02, profile.noiseFloor, 0.000001)
        assertEquals(1.05, profile.pinchPeak, 0.000001)
        assertEquals(0.2, profile.rotationLimit, 0.000001)
    }

    @Test
    fun `calibration uses the upper rest percentile instead of its average`() {
        val rest = recording(0.02).toMutableList()
        repeat(10) { index -> rest[index] = rest[index].copy(acceleration = 0.08) }
        val profile = PinchCalibrationFitter.fit(rest, List(4) { trial(1.0) }, recording(0.1))!!
        assertEquals(0.08, profile.noiseFloor, 0.000001)
    }

    @Test
    fun `negative arm movements can be distinguished by rotation but similar quiet pulses cannot`() {
        val trials = List(4) { trial(1.0) }
        assertNotNull(PinchCalibrationFitter.fit(recording(0.02), trials, recording(2.0, 2.0)))
        assertNull(PinchCalibrationFitter.fit(recording(0.02), trials, recording(0.8, 0.02)))
        val oneConfusablePulse = recording(0.02).toMutableList()
        oneConfusablePulse[20] = oneConfusablePulse[20].copy(acceleration = 0.8)
        assertNull(PinchCalibrationFitter.fit(recording(0.02), trials, oneConfusablePulse))
    }

    @Test
    fun `calibration rejects missing data and positives that do not separate from rest`() {
        val rest = recording(0.02)
        val trials = List(4) { trial(1.0) }
        val movement = recording(0.1)
        assertNull(PinchCalibrationFitter.fit(emptyList(), trials, movement))
        assertNull(PinchCalibrationFitter.fit(rest, emptyList(), movement))
        assertNull(PinchCalibrationFitter.fit(rest, trials.take(3), movement))
        assertNull(PinchCalibrationFitter.fit(rest, trials, emptyList()))
        assertNull(PinchCalibrationFitter.fit(rest.take(10), trials, movement))
        assertNull(PinchCalibrationFitter.fit(rest, listOf(emptyList<PinchMotionFeature>()) + trials, movement))
        assertNull(PinchCalibrationFitter.fit(recording(0.2), List(4) { trial(0.21) }, movement))
        assertNull(PinchCalibrationFitter.fit(rest,
                listOf(trial(1.0), trial(0.03), trial(0.04), trial(0.03)), movement))
    }

    @Test
    fun `calibration rejects corrupt or discontinuous recordings`() {
        val trials = List(4) { trial(1.0) }
        val movement = recording(0.1)
        val rest = recording(0.02)
        assertNull(PinchCalibrationFitter.fit(rest.reversed(), trials, movement))
        assertNull(PinchCalibrationFitter.fit(rest + rest.last(), trials, movement))
        assertNull(PinchCalibrationFitter.fit(rest.dropLast(1) + feature(2000, 0.02), trials, movement))
        assertNull(PinchCalibrationFitter.fit(rest, trials,
                movement.dropLast(1) + movement.last().copy(rotation = Double.NaN)))
    }

    @Test
    fun `strong single pulses do not qualify as double pinch calibration`() {
        val singles = List(6) { trial(1.0).map { sample ->
            if (sample.timestampNanos == nanos(500)) sample.copy(acceleration = 0.02) else sample
        } }
        assertNull(PinchCalibrationFitter.fit(recording(0.02), singles, recording(0.1)))
    }

    @Test
    fun `profile round trips with an explicit version and rejects invalid values`() {
        assertEquals(calibration, PinchCalibration.decode(calibration.encode()))
        for (value in listOf(null, "", "1", "2;0.03;1.0;0.5", "1;0.03;1.0;0.5;extra",
                "1;-0.03;1.0;0.5", "1;NaN;1.0;0.5", "1;0.03;Infinity;0.5",
                "1;0.03;0.03;0.5", "1;0.03;0.01;0.5", "1;0.03;1.0;0.0",
                "1;0.03;1.0;-0.5", "1;0.03;1.0;NaN", "1;0.03;1.0;Infinity")) {
            assertNull(value, PinchCalibration.decode(value))
        }
    }

    @Test
    fun `settings preserve defaults and clamp all limits`() {
        assertEquals(PinchDetectorSettings(), PinchDetectorSettings().normalized())
        assertEquals(PinchDetectorSettings(50, 250, 250),
                PinchDetectorSettings(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE).normalized())
        assertEquals(PinchDetectorSettings(200, 1500, 3000),
                PinchDetectorSettings(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE).normalized())
    }

    private fun warmedFilter(): PinchMotionFilter = PinchMotionFilter().also { filter ->
        for (time in 0L..400L step 20) {
            filter.add(nanos(time), 0.0, 0.0, 9.81, 0.0, 0.0, 0.0)
        }
    }

    private fun detections(
            pulses: Map<Long, Double>,
            rotations: Map<Long, Double> = emptyMap(),
            settings: PinchDetectorSettings = PinchDetectorSettings(),
            durationMs: Long = 1800
    ): Int {
        val detector = ExperimentalPinchDetector(calibration, settings)
        return (0L..durationMs step 20).count { time ->
            detector.add(feature(time, pulses[time] ?: 0.02, rotations[time] ?: 0.02))
        }
    }

    private fun recording(acceleration: Double, rotation: Double = 0.02): List<PinchMotionFeature> =
            (0L..1000L step 20).map { feature(it, acceleration, rotation) }

    private fun trial(peak: Double): List<PinchMotionFeature> =
            recording(0.02).map { sample ->
                if (sample.timestampNanos == nanos(200) || sample.timestampNanos == nanos(500)) {
                    sample.copy(acceleration = peak, rotation = 0.1)
                } else sample
            }

    private fun feature(timeMs: Long, acceleration: Double, rotation: Double = 0.02) =
            PinchMotionFeature(nanos(timeMs), acceleration, rotation)

    private fun nanos(timeMs: Long): Long = 1_000_000_000L + timeMs * 1_000_000L
}
