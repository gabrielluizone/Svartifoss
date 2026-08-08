package com.svartifoss.snfell.common

/**
 * How long the watch service keeps holding a *paused* session before it lets go.
 *
 * The watch app is only reachable from the watch face while its foreground service is up: that
 * service carries the ongoing-activity chip and the proxy MediaSession. Dropping it the moment
 * playback stops means an ordinary pause evicts the app, and getting back to a paused track costs
 * a trip through the launcher. Holding forever is the other extreme - a foreground service that
 * never ends - so this is a user choice rather than a constant.
 */
object PausedHoldPolicy {
    /** No extra hold: a paused track expires on the same short timer as a fully idle watch. */
    const val NO_HOLD = 0L

    /** Hold for as long as the track stays paused, with no timer at all. */
    const val FOREVER = Long.MAX_VALUE

    const val ALWAYS_VALUE = "always"

    /** Minutes, as stored. Matches the default in `MiscPreferences.WEAR_PAUSED_HOLD`. */
    const val DEFAULT_VALUE = "30"

    /**
     * Decodes the stored preference into a delay in milliseconds, [NO_HOLD] or [FOREVER].
     *
     * Anything unparseable resolves to the default rather than to [NO_HOLD]: a corrupted or
     * newer-build value should not silently restore the very behaviour this preference exists to
     * fix.
     */
    fun holdMillis(preferenceValue: String?): Long {
        if (preferenceValue == ALWAYS_VALUE) {
            return FOREVER
        }
        val minutes = preferenceValue?.trim()?.toIntOrNull()
                ?: return holdMillis(DEFAULT_VALUE)
        return when {
            minutes < 0 -> FOREVER
            minutes == 0 -> NO_HOLD
            else -> minutes * 60_000L
        }
    }
}
