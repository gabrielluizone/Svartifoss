package com.svartifoss.snfell.watch.communication

import android.os.SystemClock
import com.svartifoss.snfell.common.PlaybackPositionEstimate
import com.svartifoss.snfell.common.PlaybackSyncPolicy
import com.svartifoss.snfell.proto.MusicState
import com.svartifoss.snfell.proto.PlaybackSync
import timber.log.Timber

/**
 * The watch's single answer to "where is the song right now".
 *
 * Before this existed the answer was computed in three places from three separate anchors - the
 * player's ViewModel, the lyrics screen's ViewModel, and whatever the media session had last been
 * handed - each re-deriving it from [PhoneConnection.musicStateArrivalRealtimeMs]. They agreed
 * only because they happened to do the same arithmetic, and nothing corrected any of them: an
 * anchor was set when a `MusicState` arrived and then extrapolated, untouched, for the rest of the
 * track. That is why the lyrics screen appeared to sync when it was opened and never again - the
 * open *was* the resync, because a fresh observer replayed the last state and re-anchored on it.
 *
 * This holds one anchor, updated from three kinds of event:
 *
 *  - **A `MusicState` from the phone** ([onMusicState]) - the authoritative sample the whole
 *    system already ran on, now recorded once instead of three times.
 *  - **A local anchor** ([anchorLocally]) - a seek, an optimistic play/pause, a predicted track
 *    advance. The position is known here, this instant, so there is no staleness at all.
 *  - **A sync reply** ([onSyncReply]) - the correction this class was built for, measured over a
 *    round trip so the transport delay is subtracted rather than assumed away.
 *
 * Not thread-safe by design: everything that touches it runs on the main thread (LiveData
 * delivery, the ViewModel tickers, `PhoneConnection`'s main-dispatcher scope), and adding
 * synchronisation would only hide a caller that had wandered off it.
 */
class PlaybackClock {

    /** The sample being extrapolated from, and everything needed to extrapolate it. */
    private var positionMs = 0L
    private var durationMs = 0L
    private var playing = false
    private var playbackSpeed = 1f

    /**
     * How stale [positionMs] was at [anchorRealtimeMs], and the monotonic reading of that moment.
     *
     * Both halves measured on one device's clock apiece and never compared across the two - see
     * [PlaybackPositionEstimate], which is the whole reason `positionAgeMs` exists instead of a
     * timestamp.
     */
    private var ageAtAnchorMs = PlaybackPositionEstimate.NO_AGE
    private var anchorRealtimeMs = SystemClock.elapsedRealtime()

    /** Kept only for the legacy fallback inside [PlaybackPositionEstimate.elapsedSinceSampleMs],
     *  which is all a phone too old to report an age leaves to work with. */
    private var positionUpdateTime = 0L

    /** Identity of the track the anchor describes, so a reply that crossed a skip is discarded. */
    private var trackKey: String? = null

    /** Current wait between checks, grown and reset by [PlaybackSyncPolicy.nextIntervalMs]. */
    var syncIntervalMs: Long = PlaybackSyncPolicy.MIN_INTERVAL_MS
        private set

    /** Whether a correction is worth asking for at all: a paused track's position does not move,
     *  so checking it would spend Bluetooth to confirm a number that cannot have changed. */
    fun isPlaying(): Boolean = playing

    /** Where the song has got to, right now. Clamped to the track by [PlaybackPositionEstimate]. */
    fun positionNowMs(): Long = PlaybackPositionEstimate.positionAtMs(
            positionMs = positionMs,
            durationMs = durationMs,
            playing = playing,
            playbackSpeed = playbackSpeed,
            elapsedSinceSampleMs = elapsedSinceSampleMs())

    private fun elapsedSinceSampleMs(): Long = PlaybackPositionEstimate.elapsedSinceSampleMs(
            positionAgeMs = ageAtAnchorMs,
            sinceAnchorMs = SystemClock.elapsedRealtime() - anchorRealtimeMs,
            legacyElapsedMs = System.currentTimeMillis() - positionUpdateTime)

    /**
     * Records the sample carried by a state the phone sent.
     *
     * [arrivalRealtimeMs] is when that state actually landed, which the caller knows and this does
     * not: LiveData hands a late observer a value that arrived before it subscribed, so stamping
     * "now" here would restart the clock on a sample that is already old - the bug that made the
     * lyrics screen re-anchor, wrongly, every time it was opened.
     *
     * @return whether this state is a different track from the one held, which the caller uses to
     *   decide that the estimate needs watching closely again.
     */
    fun onMusicState(state: MusicState, arrivalRealtimeMs: Long): Boolean {
        val incomingKey = trackKeyOf(state.title, state.artist)
        val trackChanged = incomingKey != trackKey

        trackKey = incomingKey
        positionMs = state.positionMs
        durationMs = state.durationMs
        playing = state.playing
        playbackSpeed = state.playbackSpeed
        ageAtAnchorMs = state.positionAgeMs
        positionUpdateTime = state.positionUpdateTime
        anchorRealtimeMs = arrivalRealtimeMs

        if (trackChanged) {
            syncIntervalMs = PlaybackSyncPolicy.MIN_INTERVAL_MS
        }
        return trackChanged
    }

    /**
     * Records a position worked out on this device, this instant - a seek, an optimistic
     * play/pause, a predicted track advance.
     *
     * Age zero, anchored now, because there is genuinely no staleness: the number was produced
     * here. The backoff resets too, since every caller of this has just changed playback in a way
     * the phone has not confirmed yet, and that is exactly when the estimate most deserves
     * checking.
     */
    fun anchorLocally(positionMs: Long, playing: Boolean) {
        this.positionMs = positionMs.coerceAtLeast(0L)
        this.playing = playing
        ageAtAnchorMs = 0L
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        positionUpdateTime = System.currentTimeMillis()
        syncIntervalMs = PlaybackSyncPolicy.MIN_INTERVAL_MS
    }

    /**
     * Applies a sync reply, correcting the anchor by however much the estimate had drifted.
     *
     * The reply is refused outright in three cases, each of which would make things worse rather
     * than better:
     *
     *  - **no session on the phone** - there is nothing to be at a position *in*;
     *  - **a different track** - the reply crossed a skip in flight, and correcting a lyric to a
     *    song the watch has already left would drag it backwards;
     *  - **a round trip too long to halve credibly** - see
     *    [PlaybackSyncPolicy.isUsableRoundTrip]. One wasted message; the next check is moments
     *    away.
     *
     * What is applied is decided by [PlaybackSyncPolicy.correctionMs] rather than being the whole
     * difference: correcting is itself visible, and a lyrics screen that snaps every few seconds
     * reads as broken where one running a fraction behind reads as a lyric.
     *
     * @param sentAtRealtimeMs this device's monotonic reading when the request went out.
     * @return whether anything was corrected, which drives the interval backoff.
     */
    fun onSyncReply(sync: PlaybackSync, sentAtRealtimeMs: Long): Boolean {
        if (!sync.hasSession) {
            return false
        }
        if (trackKeyOf(sync.title, sync.artist) != trackKey) {
            Timber.v("Discarding a playback sync for a track that is no longer showing")
            return false
        }

        val now = SystemClock.elapsedRealtime()
        val roundTripMs = now - sentAtRealtimeMs
        if (!PlaybackSyncPolicy.isUsableRoundTrip(roundTripMs)) {
            Timber.v("Discarding a playback sync with a %d ms round trip", roundTripMs)
            return false
        }

        // The phone's own staleness figure plus the leg it could not see: the trip here.
        val confirmedAgeMs = sync.positionAgeMs.coerceAtLeast(0L) +
                PlaybackSyncPolicy.oneWayDelayMs(roundTripMs)
        val confirmedNow = PlaybackPositionEstimate.positionAtMs(
                positionMs = sync.positionMs,
                durationMs = sync.durationMs,
                playing = sync.playing,
                playbackSpeed = sync.playbackSpeed,
                elapsedSinceSampleMs = confirmedAgeMs)

        val estimatedNow = positionNowMs()
        val driftMs = estimatedNow - confirmedNow
        val correction = PlaybackSyncPolicy.correctionMs(driftMs)

        // The phone's view of play/pause is authoritative regardless of whether the position needed
        // touching - a pause performed on the phone that never produced a state the watch saw would
        // otherwise leave the estimate advancing through a song that is not moving.
        playing = sync.playing
        playbackSpeed = sync.playbackSpeed
        if (sync.durationMs > 0) {
            durationMs = sync.durationMs
        }

        if (correction == 0L) {
            syncIntervalMs = PlaybackSyncPolicy.nextIntervalMs(syncIntervalMs, corrected = false)
            return false
        }

        Timber.d("Playback sync: drift %d ms over a %d ms round trip, applying %d ms",
                driftMs, roundTripMs, -correction)
        // Re-anchored to now, so the applied correction is the only change: the elapsed time the
        // old anchor had accumulated is folded into the new position rather than counted twice.
        positionMs = (estimatedNow - correction).coerceAtLeast(0L)
        ageAtAnchorMs = 0L
        anchorRealtimeMs = now
        positionUpdateTime = System.currentTimeMillis()
        syncIntervalMs = PlaybackSyncPolicy.nextIntervalMs(syncIntervalMs, corrected = true)
        return true
    }

    /** Puts the cadence back at its floor without touching the anchor - for the events that make
     *  the estimate suspect without producing a better one (a reconnect, a screen opening). */
    fun resetBackoff() {
        syncIntervalMs = PlaybackSyncPolicy.MIN_INTERVAL_MS
    }

    /**
     * Lengthens the wait after a check that produced no answer at all.
     *
     * Without this the backoff only ever moves when a reply arrives, so a watch that lost its phone
     * mid-track would sit on a clock that still says "playing" and poll the empty air every eight
     * seconds for as long as it stayed out of range. Growing the wait the same way a quiet check
     * does bounds that at roughly one message a minute, and the first successful reply resets it.
     */
    fun backOffUnanswered() {
        syncIntervalMs = PlaybackSyncPolicy.nextIntervalMs(syncIntervalMs, corrected = false)
    }

    /** Title and artist only. The duration is deliberately excluded: streaming players publish it
     *  as 0 and fill it in a moment later, which would read as a track change and discard a
     *  perfectly good reply. */
    private fun trackKeyOf(title: String?, artist: String?): String =
            "${title.orEmpty()}|${artist.orEmpty()}"
}
