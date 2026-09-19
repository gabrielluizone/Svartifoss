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

    /**
     * [accept] for an event that arrived [sinceRegistrationMs] after its listener was registered.
     *
     * An on-change sensor reports its current value on activation - the Android sensor contract
     * requires it - so a "2" delivered in the first moments of a subscription is the last gesture
     * being replayed, not a new one. The timestamp watermark above only catches that replay within
     * one process, and only when the firmware repeats the original timestamp; after the process
     * has died (the watch app is closed and reopened constantly) or with a fresh timestamp, the
     * replay would run the assigned action the moment the player opened. Such an event still
     * seeds the watermark, so a later identical copy is refused too.
     *
     * Both times here are the app's own clock, never a vendor timestamp compared against it. A real
     * double pinch inside the window - under a third of a second after the player takes focus -
     * is not something a person does, which is what makes discarding it safe where discarding the
     * first event unconditionally was not.
     */
    fun acceptLive(timestampNanos: Long, values: FloatArray, sinceRegistrationMs: Long): Boolean =
            accept(timestampNanos, values) && sinceRegistrationMs >= REPLAY_WINDOW_MS

    companion object {
        private const val DOUBLE_PINCH_VALUE = 2f

        /** How long after registering an event is taken for the activation replay. */
        const val REPLAY_WINDOW_MS = 300L
    }
}
