package com.svartifoss.snfell.common

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

/**
 * Fits a conservative amplitude/rotation heuristic from guided examples. This is not a learned
 * finger recognizer: motion that has the same IMU signature can still trigger it. An inseparable
 * recording is rejected instead of presenting a successful calibration with unusable thresholds.
 */
object PinchCalibrationFitter {
    fun fit(
            rest: List<PinchMotionFeature>,
            trials: List<List<PinchMotionFeature>>,
            movement: List<PinchMotionFeature>
    ): PinchCalibration? {
        if (!validRecording(rest, 20) || !validRecording(movement, 20) || trials.size < 4 ||
                trials.any { !validRecording(it, 10) }) return null

        val noiseFloor = quantile(rest.map { it.acceleration }, 0.95)
        val peaks = trials.map { trial -> trial.maxOf { it.acceleration } }
        val pinchPeak = quantile(peaks, 0.5)
        val minimumPeak = max(noiseFloor * 3.5, noiseFloor + 0.06)
        val requiredGoodTrials = ceil(trials.size * 0.75).toInt()
        if (pinchPeak <= minimumPeak || peaks.count { it > minimumPeak } < requiredGoodTrials) {
            return null
        }

        // Only rotation around the positive pulses informs the limit. Quiet padding must not
        // dilute a brief turn, and one unusually large turn must not define every later attempt.
        val pulseRotations = trials.mapIndexed { index, trial ->
            trial.filter { it.acceleration >= peaks[index] * 0.5 }.maxOf { it.rotation }
        }
        val rotationLimit = max(0.15, quantile(pulseRotations, 0.75) * 1.5 + 0.05)
        val calibration = PinchCalibration(noiseFloor, pinchPeak, rotationLimit)
        if (!calibration.isValid()) return null
        val threshold = accelerationThreshold(calibration, PinchDetectorSettings())
        if (threshold >= pinchPeak * 0.9) return null

        // High-rotation negatives can be rejected by the gyro gate. A negative pulse that also
        // passes that gate has no distinguishing feature here, so do not claim it was calibrated.
        if (movement.any { it.rotation <= rotationLimit && it.acceleration >= threshold }) {
            return null
        }
        // Amplitude alone cannot establish a double pinch: the fitted thresholds must also
        // recover one pair in most guided trials, with neither repeated triggers nor noise.
        val recoveredTrials = trials.count { trial ->
            val detector = ExperimentalPinchDetector(calibration, PinchDetectorSettings())
            trial.count(detector::add) == 1
        }
        if (recoveredTrials < requiredGoodTrials) return null
        return calibration
    }

    private fun validRecording(features: List<PinchMotionFeature>, minimumSize: Int): Boolean {
        if (features.size < minimumSize || features.any { !it.isValid() }) return false
        return features.zipWithNext().all { (previous, current) ->
            val elapsed = current.timestampNanos - previous.timestampNanos
            elapsed > 0L && elapsed <= MAX_SAMPLE_GAP_NANOS
        }
    }

    private fun quantile(values: List<Double>, fraction: Double): Double {
        val sorted = values.sorted()
        val index = (sorted.size - 1) * fraction
        val lower = index.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
    }
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
            // Repeated short ringing must not eventually pair with the first edge of the pulse.
            firstPulseTimestamp = 0L
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

private fun accelerationThreshold(
        calibration: PinchCalibration,
        settings: PinchDetectorSettings
): Double = max(calibration.noiseFloor * 2.5 + 0.03,
        calibration.pinchPeak * 0.6 / (settings.sensitivityPercent / 100.0))
