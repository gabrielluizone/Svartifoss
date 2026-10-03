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
     * Longest a [PendingSeek] stands in for a player that has said nothing since.
     *
     * Players answer a seek by publishing a new state, most of them within milliseconds - but not
     * all: some publish on a cadence of their own, seconds apart, and the seek waits for the next
     * one. This has to outlast that. The bound exists for the player that ignored the seek and
     * then published nothing at all, which would otherwise be reported at a position it never
     * moved to for as long as it stayed quiet.
     */
    const val PENDING_SEEK_MAX_AGE_MS = 10_000L

    /**
     * A seek the phone has issued and the player has not yet answered.
     *
     * @param targetMs where the seek was sent to.
     * @param issuedAtRealtimeMs the phone's monotonic reading when it was sent - taken *before* the
     *   command went out, so any sample the player publishes in answer is at or after it.
     */
    data class PendingSeek(val targetMs: Long, val issuedAtRealtimeMs: Long)

    /** A position and the monotonic reading it describes, ready to extrapolate from. */
    data class PositionSample(val positionMs: Long, val sampledAtRealtimeMs: Long)

    /**
     * The position sample the phone should report, given what the player last published.
     *
     * `PlaybackState.getPosition()` is where playback was at `getLastPositionUpdateTime()`, and
     * that is only the right thing to report when the player has said nothing since that this
     * service knows to be newer. Two things can be newer, checked in this order:
     *
     *  - **A seek this service issued.** Until the player answers it, its last sample describes
     *    where playback was *before* the seek, and reporting that would put the watch back on the
     *    old position - after the watch had already drawn the new one on the press. The seek's own
     *    target is reported instead, measured from when it was sent. This is what an androidx
     *    Media3 controller does after a seek too (it masks the position until the session answers),
     *    and for the same reason. Any sample published since stands down the mask, whatever it says,
     *    since a player that ignored the seek reports exactly that; so does [PENDING_SEEK_MAX_AGE_MS].
     *  - **The track itself.** A sample older than [trackFirstSeenRealtimeMs] belongs to the
     *    previous track - see [sampleBelongsToTrack]. It is reported as position zero *when the
     *    track was first seen*, which is when it started as near as this side can tell; extrapolated
     *    from there it keeps advancing correctly however long the player then stays quiet. A zero
     *    stamped "now" instead went back to the start of the track every time the state was rebuilt.
     *
     * @param trackFirstSeenRealtimeMs zero when there is no track boundary to protect (see the
     *   caller), in which case every sample the player published counts as this track's.
     */
    fun reportableSample(
            positionMs: Long,
            sampleRealtimeMs: Long,
            trackFirstSeenRealtimeMs: Long,
            pendingSeek: PendingSeek?,
            nowRealtimeMs: Long,
    ): PositionSample {
        if (pendingSeek != null &&
                sampleRealtimeMs < pendingSeek.issuedAtRealtimeMs &&
                (nowRealtimeMs - pendingSeek.issuedAtRealtimeMs) in 0L..PENDING_SEEK_MAX_AGE_MS) {
            return PositionSample(pendingSeek.targetMs, pendingSeek.issuedAtRealtimeMs)
        }
        if (sampleBelongsToTrack(sampleRealtimeMs, trackFirstSeenRealtimeMs)) {
            return PositionSample(positionMs, sampleRealtimeMs)
        }
        val startedAt = trackFirstSeenRealtimeMs.takeIf { it in 1L..nowRealtimeMs } ?: nowRealtimeMs
        return PositionSample(0L, startedAt)
    }

    /**
     * Where a relative seek of [deltaMs] from [currentMs] lands: never before the start, and never
     * past the end when the end is known.
     *
     * One function for every relative seek on both devices - the phone resolving the actual target,
     * and the watch drawing it on the press - so the two cannot land the same press in different
     * places. The bounds are the ones androidx Media3's own seek-back/forward apply.
     *
     * @param durationMs 0 when unknown (a live stream, or a player that has not said yet), in which
     *   case nothing but the start bounds it.
     */
    fun relativeSeekTargetMs(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val unbounded = currentMs + deltaMs
        return if (durationMs > 0L) {
            unbounded.coerceIn(0L, durationMs)
        } else {
            unbounded.coerceAtLeast(0L)
        }
    }

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
