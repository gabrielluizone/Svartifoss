package com.svartifoss.snfell.watch.complication

/**
 * Where the track is, for the playback complication - pure, so it can be pinned without a watch
 * face.
 *
 * A complication is read by the watch face's process at moments this app does not choose, from the
 * music-state DataItem the phone last published, and the phone deliberately does not publish while
 * only the position moves (see `MusicService.equalsIgnoringTime`). So the reading here is always a
 * *prediction*, and the only clock that can date the sample is the one the phone stamped it with:
 * `positionUpdateTime`, a wall-clock time. That is the cross-device subtraction the player itself
 * stopped trusting (see `PlaybackPositionEstimate`), and it is accepted here on purpose: the player
 * has a message channel to measure the transport with, a complication has none, and an error of a
 * second or two on a ring a few millimetres across is not visible - while the lyrics screen it
 * would wreck is not drawn from this.
 *
 * Everything is expressed as the moment the track *started* on the wall clock ([trackStartEpochMs]),
 * because that one number is what lets the watch face animate the progress on its own: the face
 * counts from it with the platform clock (see `PlaybackComplicationDataSourceService`), and this app
 * is not asked again until something other than the position changes.
 */
data class PlaybackComplicationMath(
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val speed: Float,
        /** When [positionMs] was true, on the phone's wall clock; 0 when the phone never said. */
        val sampledAtEpochMs: Long
) {
    /** The speed the position advances at; a session reporting 0 or less is treated as normal. */
    val effectiveSpeed: Float
        get() = if (speed > 0f) speed else 1f

    /** Whether there is a length to show progress against. */
    val hasLength: Boolean
        get() = durationMs > 0L

    /**
     * When the track would have started, on the wall clock, had it played at [effectiveSpeed]
     * throughout - the anchor a face can count up from. [nowEpochMs] stands in for a missing
     * sample time, so a phone that never dated its sample is read as "true now".
     */
    fun trackStartEpochMs(nowEpochMs: Long): Long {
        val sampledAt = if (sampledAtEpochMs > 0L) sampledAtEpochMs else nowEpochMs
        return sampledAt - (positionMs / effectiveSpeed).toLong()
    }

    /** The position at [nowEpochMs]: advancing while playing, still while paused, within the track. */
    fun positionAt(nowEpochMs: Long): Long {
        val position = if (!playing) {
            positionMs
        } else {
            ((nowEpochMs - trackStartEpochMs(nowEpochMs)) * effectiveSpeed).toLong()
        }
        val atLeastZero = position.coerceAtLeast(0L)
        return if (hasLength) atLeastZero.coerceAtMost(durationMs) else atLeastZero
    }

    /** How far through the track [nowEpochMs] is, 0..1; 0 for a track with no length. */
    fun fractionAt(nowEpochMs: Long): Float =
            if (!hasLength) 0f else (positionAt(nowEpochMs).toFloat() / durationMs).coerceIn(0f, 1f)

    /**
     * How much the fraction grows per second of wall-clock time while playing: the multiplier a
     * face applies to the seconds it has counted since [trackStartEpochMs].
     */
    val fractionPerSecond: Float
        get() = if (!hasLength) 0f else effectiveSpeed * 1000f / durationMs
}
