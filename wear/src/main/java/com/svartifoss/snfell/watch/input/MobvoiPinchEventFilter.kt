package com.svartifoss.snfell.watch.input

/**
 * Decodes the proprietary `mobvoi_pinch` format observed on the TicWatch Pro 5.
 *
 * A value of 2 already represents a completed double pinch; it must not be paired with another
 * event. Keep this filter across listener registrations: this on-change sensor can replay its
 * last value when the player resumes. Sensor timestamps identify events without a debounce that
 * would discard another real gesture, or a comparison with the app's clock on older firmware.
 */
internal class MobvoiPinchEventFilter {
    private var lastAcceptedTimestampNanos = 0L

    fun accept(timestampNanos: Long, values: FloatArray): Boolean {
        if (values.firstOrNull() != DOUBLE_PINCH_VALUE) return false
        if (timestampNanos <= lastAcceptedTimestampNanos) return false
        lastAcceptedTimestampNanos = timestampNanos
        return true
    }

    private companion object {
        const val DOUBLE_PINCH_VALUE = 2f
    }
}
