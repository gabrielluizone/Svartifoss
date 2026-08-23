package com.svartifoss.snfell.common

/**
 * Works out where playback has reached, from a position sample that was taken somewhere else.
 *
 * The watch is told the position only when something changes - the phone deliberately suppresses
 * position-only retransmissions (see `MusicService.isEquivalentTo`) - so for most of a track the
 * watch is not reading a position, it is *predicting* one. Everything that follows playback on the
 * wrist rides on this: the progress ring, the track time, and the synced-lyrics screen, which is
 * the one that makes an error of a second or two obvious rather than invisible.
 *
 * **The rule this exists to enforce: never subtract one device's clock from the other's.**
 *
 * The original design sent the sample time as a phone epoch timestamp and had the watch subtract it
 * from its own `System.currentTimeMillis()`. That is only correct if the two wall clocks agree, and
 * they routinely do not - Wear OS syncs a watch's time from its phone with tolerance, not exactly.
 * Whatever they differ by became a fixed offset applied to every predicted position, in either
 * direction, for the whole track. Two symptoms that look unrelated come from that single fault:
 *
 *  - lyrics that sit a few seconds ahead of or behind the song, consistently;
 *  - a jump on pause and on resume, because a paused state predicts nothing (elapsed is zero, so
 *    the position is exactly what the phone measured) while a playing one predicts through the
 *    skew. Every transition between them steps by the offset.
 *
 * The fix is to send a **duration** rather than a timestamp: `positionAgeMs` is how stale the
 * sample already was when the phone built the payload, measured entirely with the phone's own
 * clock, so no clock is ever compared against a foreign one. The receiver adds however long it has
 * held the payload, measured entirely with its own monotonic clock. What is left unaccounted for is
 * the Bluetooth flight time between the two, which is a couple of hundred milliseconds rather than
 * seconds - and it is unaccounted for in the direction that leaves a lyric marginally late, which
 * reads far better than early.
 *
 * `elapsedRealtime`, not `currentTimeMillis`, for the receiver's half: the point is to measure a
 * duration on one device, and a wall clock can be stepped by an NTP correction or a timezone/user
 * change mid-track, which would move the lyric.
 */
object PlaybackPositionEstimate {

    /** [elapsedSinceSampleMs]'s `positionAgeMs` when the sender did not provide one. */
    const val NO_AGE: Long = -1L

    /**
     * How much wall time has passed since the position was sampled.
     *
     * @param positionAgeMs how stale the sample was when the sender built the payload, or a
     *   negative value if the sender is too old to report it (see [NO_AGE]).
     * @param sinceAnchorMs how long the receiver has held the payload, from its own monotonic
     *   clock.
     * @param legacyElapsedMs the pre-fix cross-device subtraction, used only when [positionAgeMs]
     *   is absent. Kept so a new watch paired with a phone that has not been updated keeps working
     *   exactly as it did before rather than freezing - the skew comes back with it, which is the
     *   honest trade when the phone simply is not sending the better number.
     *
     * Never negative. The legacy path could genuinely go negative when the watch's clock ran behind
     * the phone's, which ran the lyric backwards past the start of the track.
     */
    fun elapsedSinceSampleMs(
            positionAgeMs: Long,
            sinceAnchorMs: Long,
            legacyElapsedMs: Long,
    ): Long = if (positionAgeMs >= 0) {
        (positionAgeMs + sinceAnchorMs).coerceAtLeast(0L)
    } else {
        legacyElapsedMs.coerceAtLeast(0L)
    }

    /**
     * Whether a position sample can describe the track it is about to be attached to.
     *
     * A `MediaSession` publishes metadata and playback state through **separate** callbacks with no
     * guaranteed order, so the moment a track changes there is a window where the new track's title,
     * artist and duration are readable while the position still belongs to the *previous* one.
     * Shipping that pair produces a specific and very visible wrong answer: a 2:30 track ending into
     * a 4:00 one leaves the watch counting 2:31, 2:32 … up to 4:00, because the stale position is
     * being extrapolated against the new track's length. Nothing about it looks like an error.
     *
     * The rule is the only one available without asking the player anything: **a sample taken before
     * we first saw this track cannot be about this track.** Both arguments come from the phone's own
     * monotonic clock, so there is no clock to disagree about, and a sample that fails this is
     * reported as position zero rather than guessed at.
     *
     * @param sampleRealtimeMs `PlaybackState.getLastPositionUpdateTime()`. Zero means the session
     *   has never published one, which is not a sample at all.
     * @param trackFirstSeenRealtimeMs when this track's metadata was first read.
     */
    fun sampleBelongsToTrack(sampleRealtimeMs: Long, trackFirstSeenRealtimeMs: Long): Boolean =
            sampleRealtimeMs > 0L && sampleRealtimeMs >= trackFirstSeenRealtimeMs

    /**
     * The position to display, clamped to the track.
     *
     * A paused track predicts nothing: the sample the sender took *is* the answer, because playback
     * stopped at the moment it was taken. Advancing it by the elapsed time is what would make a
     * paused lyric keep scrolling.
     *
     * @param durationMs 0 when unknown, in which case nothing caps the result but the track itself.
     */
    fun positionAtMs(
            positionMs: Long,
            durationMs: Long,
            playing: Boolean,
            playbackSpeed: Float,
            elapsedSinceSampleMs: Long,
    ): Long {
        val advance = if (playing) (elapsedSinceSampleMs * playbackSpeed).toLong() else 0L
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return (positionMs + advance).coerceIn(0L, max)
    }
}
