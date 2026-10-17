package com.svartifoss.snfell.common

/**
 * Decides when a session is a live stream, from the one signal players give for it: a duration of
 * exactly 0.
 *
 * A YouTube live publishes `METADATA_KEY_DURATION = 0`, where a video publishes its length and a
 * player that has nothing to say about it leaves the key out - which is why *absent* is not a
 * signal here, and why the caller passes whether the key was published as 0 rather than the
 * duration itself. The watch used to treat all of those alike and draw nothing where the time goes;
 * this is what lets it say "LIVE" there instead.
 *
 * **A zero has to last before it counts.** Streaming players routinely publish a track's metadata
 * with a duration of 0 and fill the real one in a moment later (the same fact the phone's track
 * guard is written around), so reading the first zero as "live" would flash the label at the start
 * of every track of a player that does this. The zero must have been there for [CONFIRM_MS], for
 * the same track, before it is believed; a duration that turns real, or another track, starts the
 * wait over.
 *
 * Pure and free of `android.*`, with the clock passed in, so it is pinned by a JVM test. Not
 * thread-safe: the one caller reads and writes it on the main thread.
 */
class LiveStreamTracker(private val confirmMs: Long = CONFIRM_MS) {

    /** [live], and when to ask again if the answer is "not yet" - null when nothing is pending. */
    data class Verdict(val live: Boolean, val recheckAfterMs: Long?)

    private var identity: Any? = null
    private var zeroSinceMs = 0L

    /**
     * @param identity what makes this the same track as the last call: the session and the track's
     *   names, together. Compared with `equals`.
     * @param durationPublishedAsZero whether the metadata carries the duration key *and* its value
     *   is 0.
     * @param nowMs a monotonic reading in milliseconds, positive.
     */
    fun observe(identity: Any, durationPublishedAsZero: Boolean, nowMs: Long): Verdict {
        if (!durationPublishedAsZero) {
            this.identity = null
            zeroSinceMs = 0L
            return Verdict(live = false, recheckAfterMs = null)
        }
        if (identity != this.identity || zeroSinceMs == 0L) {
            this.identity = identity
            zeroSinceMs = nowMs
        }
        val waited = nowMs - zeroSinceMs
        return if (waited >= confirmMs) {
            Verdict(live = true, recheckAfterMs = null)
        } else {
            Verdict(live = false, recheckAfterMs = confirmMs - waited)
        }
    }

    companion object {
        /** Long enough to outlast the moment between a track's metadata and its real duration. */
        const val CONFIRM_MS = 3_000L
    }
}
