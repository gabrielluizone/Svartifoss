package com.svartifoss.snfell.common

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** A local, versioned profile for the experimental IMU detector, in m/s² and rad/s. */
data class PinchCalibration(
        val noiseFloor: Double,
        val pinchPeak: Double,
        val rotationLimit: Double
) {
    fun encode(): String {
        require(isValid()) { "Invalid pinch calibration" }
        return "$VERSION;$noiseFloor;$pinchPeak;$rotationLimit"
    }

    internal fun isValid(): Boolean = noiseFloor.isFinite() && noiseFloor >= 0.0 &&
            pinchPeak.isFinite() && pinchPeak > noiseFloor &&
            rotationLimit.isFinite() && rotationLimit > 0.0

    companion object {
        private const val VERSION = "1"

        fun decode(value: String?): PinchCalibration? {
            if (value == null || value.length > 200) return null
            val fields = value.split(';')
            if (fields.size != 4 || fields[0] != VERSION) return null
            return PinchCalibration(
                    fields[1].toDoubleOrNull() ?: return null,
                    fields[2].toDoubleOrNull() ?: return null,
                    fields[3].toDoubleOrNull() ?: return null
            ).takeIf { it.isValid() }
        }
    }
}

data class PinchDetectorSettings(
        val sensitivityPercent: Int = 100,
        val maxPinchGapMs: Int = 800,
        val cooldownMs: Int = 1000
) {
    fun normalized(): PinchDetectorSettings = copy(
            sensitivityPercent = sensitivityPercent.coerceIn(50, 200),
            maxPinchGapMs = maxPinchGapMs.coerceIn(250, 1500),
            cooldownMs = cooldownMs.coerceIn(250, 3000)
    )
}

/** Gravity-removed acceleration magnitude and angular speed at one sensor timestamp. */
data class PinchMotionFeature(
        val timestampNanos: Long,
        val acceleration: Double,
        val rotation: Double
)

/**
 * A small high-pass motion filter, intended for the roughly 50 Hz IMU on the tested TicWatch.
 * Actual sensor time controls the gravity estimate; requesting a faster rate is not calibration.
 * Rotation is retained separately because a changing wrist orientation can also produce a pulse.
 */
class PinchMotionFilter {
    private var firstTimestamp = 0L
    private var lastTimestamp = 0L
    private var gravityX = 0.0
    private var gravityY = 0.0
    private var gravityZ = 0.0

    fun add(
            timestampNanos: Long,
            ax: Double,
            ay: Double,
            az: Double,
            gx: Double,
            gy: Double,
            gz: Double
    ): PinchMotionFeature? {
        if (timestampNanos <= 0L || !ax.isFinite() || !ay.isFinite() || !az.isFinite() ||
                !gx.isFinite() || !gy.isFinite() || !gz.isFinite() ||
                (lastTimestamp != 0L && timestampNanos <= lastTimestamp)) {
            reset()
            return null
        }
        val rotation = hypot(hypot(gx, gy), gz)
        if (!rotation.isFinite()) {
            reset()
            return null
        }
        if (lastTimestamp == 0L || timestampNanos - lastTimestamp > MAX_SAMPLE_GAP_NANOS) {
            reset()
            firstTimestamp = timestampNanos
            lastTimestamp = timestampNanos
            gravityX = ax
            gravityY = ay
            gravityZ = az
            return null
        }

        val seconds = (timestampNanos - lastTimestamp) / 1_000_000_000.0
        val alpha = seconds / (GRAVITY_TIME_CONSTANT_SECONDS + seconds)
        gravityX += alpha * (ax - gravityX)
        gravityY += alpha * (ay - gravityY)
        gravityZ += alpha * (az - gravityZ)
        lastTimestamp = timestampNanos
        val acceleration = hypot(hypot(ax - gravityX, ay - gravityY), az - gravityZ)
        if (!acceleration.isFinite()) {
            reset()
            return null
        }
        if (timestampNanos - firstTimestamp < WARMUP_NANOS) return null
        return PinchMotionFeature(timestampNanos, acceleration, rotation)
    }

    fun reset() {
        firstTimestamp = 0L
        lastTimestamp = 0L
        gravityX = 0.0
        gravityY = 0.0
        gravityZ = 0.0
    }

    private companion object {
        const val GRAVITY_TIME_CONSTANT_SECONDS = 0.12
        const val WARMUP_NANOS = 400_000_000L
    }
}

/** Why a guided recording produced no profile - each has a different thing to try next. */
enum class PinchCalibrationFailure {
    /** Too few usable samples: the sensors stopped, or the screen left the test early. */
    RECORDING_INCOMPLETE,

    /** The pinches did not stand out from the resting wrist. */
    PINCHES_TOO_WEAK,

    /** The pulses were strong enough, but most attempts did not read as one double pinch. */
    PINCHES_NOT_RECOGNIZED,

    /** Ordinary arm movement would have triggered the detector, even at the strictest limit. */
    MOVEMENT_TRIGGERS
}

/** The outcome of [PinchCalibrationFitter.evaluate]: exactly one of the first two is non-null. */
data class PinchCalibrationResult(
        val calibration: PinchCalibration?,
        val failure: PinchCalibrationFailure?,
        /** Numbers worth a log line; never shown as a verdict. */
        val diagnostics: String,
        /**
         * Separate pulses seen in each usable attempt, at the threshold that recognised the most
         * of them - what a person would call taps. Empty when the recording never got that far.
         * Shown with a refusal: "0 0 0" means the pinches never reached the line, "1 1 1" that
         * the two taps ran together, "5 4 6" that the wrist was moving.
         */
        val attemptPulses: List<Int> = emptyList(),
        /** How many of those attempts were recognised as a double pinch. */
        val attemptsRecognized: Int = 0
)

/**
 * Fits a conservative amplitude/rotation heuristic from guided examples. This is not a learned
 * finger recognizer: motion that has the same IMU signature can still trigger it. An inseparable
 * recording is rejected instead of presenting a successful calibration with unusable thresholds,
 * and the rejection names which part of the recording could not be used.
 *
 * **Negatives are judged by the detector, not by single samples.** The first version refused a
 * profile if any one sample of the arm-movement phase was above the pulse threshold with a low
 * rotation. Moving an arm produces isolated spikes like that constantly, so almost every real
 * recording was refused. A single spike cannot fire a *double* pinch; what matters is whether
 * the detector itself - pair timing, hysteresis, rotation gate - fires during that movement, or
 * during the resting phase.
 *
 * **The threshold is searched, not assumed.** It used to be fixed at 60% of the median of each
 * attempt's largest sample. That largest sample is often not the pinch at all but whatever the arm
 * did inside the same three seconds, so the line could sit above both taps and nothing was ever
 * recognised. The fit now tries a short grid - threshold from 60% down to 30% of that peak (never
 * below the resting-noise guard), and rotation limits around the one the pinches themselves
 * showed - and keeps the first combination, in order of preference, that recognises the most
 * attempts without firing during rest or movement. The chosen threshold is stored through the
 * profile's reference peak, so the saved format and the phone's sensitivity slider are unchanged.
 */
object PinchCalibrationFitter {
    /** The minimum number of guided attempts that must be usable. */
    const val MINIMUM_TRIALS = 4

    fun fit(
            rest: List<PinchMotionFeature>,
            trials: List<List<PinchMotionFeature>>,
            movement: List<PinchMotionFeature>
    ): PinchCalibration? = evaluate(rest, trials, movement).calibration

    fun evaluate(
            rest: List<PinchMotionFeature>,
            trials: List<List<PinchMotionFeature>>,
            movement: List<PinchMotionFeature>
    ): PinchCalibrationResult {
        // A trial interrupted by a sensor gap is dropped rather than failing the whole recording:
        // the detector resets on a gap anyway, and five good attempts are still five good attempts.
        val usableTrials = trials.filter { validTrial(it) }
        if (!validRecording(rest, 20) || !validRecording(movement, 20) ||
                usableTrials.size < MINIMUM_TRIALS) {
            return rejected(PinchCalibrationFailure.RECORDING_INCOMPLETE,
                    "rest=${rest.size} movement=${movement.size} " +
                            "trials=${usableTrials.size}/${trials.size}")
        }

        val noiseFloor = noiseFloorOf(rest)
        val peaks = usableTrials.map { trial -> trial.maxOf { it.acceleration } }
        val pinchPeak = quantile(peaks, 0.5)
        val minimumPeak = minimumPeakAbove(noiseFloor)
        val required = requiredTrials(usableTrials.size)
        val numbers = "noise=%.3f peak=%.3f minPeak=%.3f"
                .format(Locale.ROOT, noiseFloor, pinchPeak, minimumPeak)
        if (pinchPeak <= minimumPeak || peaks.count { it > minimumPeak } < required) {
            return rejected(PinchCalibrationFailure.PINCHES_TOO_WEAK,
                    "$numbers strong=${peaks.count { it > minimumPeak }}/$required",
                    usableTrials.map { pulseCount(it, minimumPeak) })
        }
        val guard = noiseGuard(noiseFloor)
        if (guard >= pinchPeak * 0.9) {
            return rejected(PinchCalibrationFailure.PINCHES_TOO_WEAK,
                    "$numbers guard=%.3f".format(Locale.ROOT, guard),
                    usableTrials.map { pulseCount(it, guard) })
        }

        // Only rotation around the positive pulses informs the limit. Quiet padding must not
        // dilute a brief turn, and one unusually large turn must not define every later attempt.
        val pulseRotations = usableTrials.mapIndexed { index, trial ->
            trial.filter { it.acceleration >= peaks[index] * 0.5 }.maxOf { it.rotation }
        }
        val baseLimit = max(MINIMUM_ROTATION_LIMIT, quantile(pulseRotations, 0.75) * 1.5 + 0.05)

        var best: Candidate? = null
        var mostRecognized: Candidate? = null
        for (fraction in THRESHOLD_FRACTIONS) {
            val threshold = max(guard, pinchPeak * fraction)
            for (factor in ROTATION_FACTORS) {
                val calibration = PinchCalibration(
                        noiseFloor,
                        threshold / REFERENCE_FRACTION,
                        max(MINIMUM_ROTATION_LIMIT, baseLimit * factor))
                if (!calibration.isValid()) continue
                val recognized = usableTrials.count { detections(calibration, it) >= 1 }
                val falseTriggers = detections(calibration, movement) + detections(calibration, rest)
                val candidate = Candidate(calibration, threshold, recognized, falseTriggers)
                // Strictly greater: an earlier, more conservative combination wins a tie.
                if (mostRecognized == null || recognized > mostRecognized.recognized) {
                    mostRecognized = candidate
                }
                if (recognized >= required && falseTriggers == 0 &&
                        (best == null || recognized > best.recognized)) {
                    best = candidate
                }
            }
        }

        val shown = best ?: mostRecognized
        val pulses = shown?.let { candidate -> usableTrials.map { pulseCount(it, candidate.threshold) } }
                .orEmpty()
        val detail = if (shown == null) numbers else {
            "$numbers threshold=%.3f limit=%.3f recognized=${shown.recognized}/$required "
                    .format(Locale.ROOT, shown.threshold, shown.calibration.rotationLimit) +
                    "falseTriggers=${shown.falseTriggers} pulses=$pulses"
        }
        if (best != null) {
            return PinchCalibrationResult(best.calibration, null, detail, pulses, best.recognized)
        }
        val recognizedSomewhere = (mostRecognized?.recognized ?: 0) >= required
        return PinchCalibrationResult(
                null,
                if (recognizedSomewhere) PinchCalibrationFailure.MOVEMENT_TRIGGERS
                else PinchCalibrationFailure.PINCHES_NOT_RECOGNIZED,
                detail,
                pulses,
                mostRecognized?.recognized ?: 0)
    }

    /**
     * The level a pinch has to clear to count as strong, from a resting recording - the line the
     * calibration screen draws across its live graph, and the same minimum [evaluate] applies.
     * Null until the rest is long enough to say.
     */
    fun minimumPeakFor(rest: List<PinchMotionFeature>): Double? =
            if (rest.size < 20) null else minimumPeakAbove(noiseFloorOf(rest))

    private fun noiseFloorOf(rest: List<PinchMotionFeature>) =
            quantile(rest.map { it.acceleration }, 0.95)

    private fun minimumPeakAbove(noiseFloor: Double) = max(noiseFloor * 3.5, noiseFloor + 0.06)

    /** Two thirds of the attempts, never fewer than three: one missed pinch in six is ordinary. */
    fun requiredTrials(usable: Int): Int = max(3, ceil(usable * 2.0 / 3.0).toInt())

    private class Candidate(
            val calibration: PinchCalibration,
            val threshold: Double,
            val recognized: Int,
            val falseTriggers: Int
    )

    private fun detections(calibration: PinchCalibration, recording: List<PinchMotionFeature>): Int {
        val detector = ExperimentalPinchDetector(calibration, PinchDetectorSettings())
        return recording.count(detector::add)
    }

    private fun rejected(
            failure: PinchCalibrationFailure,
            diagnostics: String,
            pulses: List<Int> = emptyList()
    ) = PinchCalibrationResult(null, failure, diagnostics, pulses, 0)

    /**
     * Rest and movement only need enough valid, ordered samples: the noise floor is a percentile
     * that a sensor gap does not bias, and the detector already resets itself across one. They
     * used to be refused for any gap, which a single stalled gyro delivery was enough to cause.
     */
    private fun validRecording(features: List<PinchMotionFeature>, minimumSize: Int): Boolean {
        if (features.size < minimumSize || features.any { !it.isValid() }) return false
        return features.zipWithNext().all { (previous, current) ->
            current.timestampNanos > previous.timestampNanos
        }
    }

    /** A guided attempt must also be continuous: a gap can split the very pair it contains. */
    private fun validTrial(features: List<PinchMotionFeature>): Boolean {
        if (!validRecording(features, 10)) return false
        return features.zipWithNext().all { (previous, current) ->
            current.timestampNanos - previous.timestampNanos <= MAX_SAMPLE_GAP_NANOS
        }
    }

    private fun quantile(values: List<Double>, fraction: Double): Double {
        val sorted = values.sorted()
        val index = (sorted.size - 1) * fraction
        val lower = index.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
    }

    private const val MINIMUM_ROTATION_LIMIT = 0.15

    /** Threshold as a fraction of the attempts' median peak, most conservative first. */
    private val THRESHOLD_FRACTIONS = listOf(0.6, 0.5, 0.4, 0.3)

    /** Rotation limits around the fitted one: as fitted, stricter, then looser. */
    private val ROTATION_FACTORS = listOf(1.0, 0.8, 0.65, 1.6, 2.5)
}

/**
 * Whether an experimental detection may run an action, given when the wearer last touched the
 * screen, pressed a button or turned the crown.
 *
 * The detector reads the wrist's motion, and a finger tapping the watch face shakes the wrist the
 * same way a pinch does - a double tap on the player, which opens the quick actions, is two sharp
 * pulses a fifth of a second apart, which is exactly a double pinch. Without this, opening the
 * quick panel could also play or pause. The firmware detectors are not gated: they classify the
 * gesture themselves, and a touch is not what they report.
 */
object PinchInteractionGuard {
    /** Long enough to cover a double tap and the button press that follows it. */
    const val QUIET_AFTER_INTERACTION_MS = 800L

    fun allows(nowMs: Long, lastInteractionMs: Long?): Boolean =
            lastInteractionMs == null || nowMs - lastInteractionMs >= QUIET_AFTER_INTERACTION_MS
}

/**
 * Experimental two-pulse heuristic. Hysteresis and a 120 ms minimum separation prevent a broad
 * pulse or closely spaced ringing from being counted twice. They cannot distinguish a pinch
 * from unrelated motion with the same amplitude, timing and rotation; physical testing is needed.
 * Run this input instead of, never alongside, the watch's native pinch recognizer.
 */
class ExperimentalPinchDetector(
        calibration: PinchCalibration,
        settings: PinchDetectorSettings
) {
    private val validCalibration = calibration.isValid()
    private val normalizedSettings = settings.normalized()
    private val threshold = accelerationThreshold(calibration, normalizedSettings)

    /** The acceleration a pulse has to reach, for drawing it against the live signal. */
    val triggerThreshold: Double get() = threshold
    private val releaseThreshold = threshold * 0.55
    private val rotationLimit = calibration.rotationLimit
    private val maxPinchGapNanos = normalizedSettings.maxPinchGapMs * 1_000_000L
    private val cooldownNanos = normalizedSettings.cooldownMs * 1_000_000L
    private var lastTimestamp = 0L
    private var firstPulseTimestamp = 0L
    private var lastRiseTimestamp = 0L
    private var lastDetectionTimestamp = 0L
    private var aboveThreshold = false

    fun add(feature: PinchMotionFeature): Boolean {
        if (!validCalibration || !feature.isValid() ||
                (lastTimestamp != 0L && feature.timestampNanos <= lastTimestamp)) {
            reset()
            return false
        }
        if (lastTimestamp != 0L && feature.timestampNanos - lastTimestamp > MAX_SAMPLE_GAP_NANOS) {
            reset()
            lastTimestamp = feature.timestampNanos
            return false
        }
        lastTimestamp = feature.timestampNanos
        if (feature.rotation > rotationLimit) {
            firstPulseTimestamp = 0L
            lastRiseTimestamp = 0L
            aboveThreshold = true // Require a quiet sample after the turn, not its decaying tail.
            return false
        }
        if (lastDetectionTimestamp != 0L &&
                feature.timestampNanos - lastDetectionTimestamp < cooldownNanos) {
            firstPulseTimestamp = 0L
            lastRiseTimestamp = 0L
            aboveThreshold = feature.acceleration > releaseThreshold
            return false
        }
        if (firstPulseTimestamp != 0L &&
                feature.timestampNanos - firstPulseTimestamp > maxPinchGapNanos) {
            firstPulseTimestamp = 0L
        }
        if (feature.acceleration <= releaseThreshold) {
            aboveThreshold = false
            return false
        }
        if (aboveThreshold || feature.acceleration < threshold) return false
        aboveThreshold = true

        val sinceLastRise = feature.timestampNanos - lastRiseTimestamp
        val hadPreviousRise = lastRiseTimestamp != 0L
        lastRiseTimestamp = feature.timestampNanos
        if (hadPreviousRise && sinceLastRise < MIN_PINCH_GAP_NANOS) {
            // Ringing: a tap makes the sensor oscillate, and each swing that re-crosses the
            // threshold within this gap belongs to the same pulse. It is absorbed - the pulse is
            // extended - rather than treated as a new edge. This used to clear the pending first
            // pulse instead, which is what made a real double pinch unrecognisable: the first tap
            // rang, its ringing cancelled it, and the second tap became a lone "first" pulse with
            // nothing left to pair with.
            return false
        }
        if (firstPulseTimestamp == 0L) {
            firstPulseTimestamp = feature.timestampNanos
            return false
        }

        firstPulseTimestamp = 0L
        lastDetectionTimestamp = feature.timestampNanos
        return true
    }

    fun reset() {
        lastTimestamp = 0L
        firstPulseTimestamp = 0L
        lastRiseTimestamp = 0L
        lastDetectionTimestamp = 0L
        aboveThreshold = false
    }

    private companion object {
        const val MIN_PINCH_GAP_NANOS = 120_000_000L
    }
}

private const val MAX_SAMPLE_GAP_NANOS = 250_000_000L

private fun PinchMotionFeature.isValid(): Boolean = timestampNanos > 0L &&
        acceleration.isFinite() && acceleration >= 0.0 && rotation.isFinite() && rotation >= 0.0

/** The part of [PinchCalibration.pinchPeak] a pulse must reach at 100% sensitivity. */
private const val REFERENCE_FRACTION = 0.6

/** The floor no sensitivity can lower the threshold past, so resting noise never triggers. */
private fun noiseGuard(noiseFloor: Double): Double = noiseFloor * 2.5 + 0.03

private fun accelerationThreshold(
        calibration: PinchCalibration,
        settings: PinchDetectorSettings
): Double = max(noiseGuard(calibration.noiseFloor),
        calibration.pinchPeak * REFERENCE_FRACTION / (settings.sensitivityPercent / 100.0))

/**
 * How many separate pulses [recording] holds at [threshold], with the detector's own hysteresis
 * and ringing merge but no rotation gate or pairing - what a person would call "taps", used to
 * explain a refused calibration.
 */
internal fun pulseCount(recording: List<PinchMotionFeature>, threshold: Double): Int {
    val release = threshold * 0.55
    var above = false
    var lastRise = 0L
    var pulses = 0
    for (feature in recording) {
        if (feature.acceleration <= release) {
            above = false
            continue
        }
        if (above || feature.acceleration < threshold) continue
        above = true
        if (lastRise == 0L || feature.timestampNanos - lastRise >= 120_000_000L) pulses++
        lastRise = feature.timestampNanos
    }
    return pulses
}
