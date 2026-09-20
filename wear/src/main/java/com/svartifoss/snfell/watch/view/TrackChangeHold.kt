package com.svartifoss.snfell.watch.view

/**
 * Decides what the player does with a `MusicState` that arrives while a track change *this device
 * asked for* is still settling on the phone.
 *
 * Skipping a track is not one event, it is a short sequence of them, and the watch used to draw
 * every step of it. A player asked for the next track routinely drops out of the playing state
 * while it swaps its source: it publishes a paused (or empty) state carrying the *old* metadata,
 * then the new metadata, then finally the playing state again. The phone transmits each of those
 * faithfully - it is reading a real `MediaSession` and has nothing better to report - so on the
 * wrist one press produced the artist line flicking to "Stopped", the play/pause control turning
 * over and back, and the cover blanking, all before the track the user actually asked for appeared.
 * YouTube Music is the pronounced case (it genuinely pauses and resumes around every skip, where
 * local players mostly do not), but nothing here is specific to it.
 *
 * None of that is information. It is the inside of a transition, and it is only visible because the
 * watch has no way to tell it apart from a real pause. This object is that way: while a skip is
 * settling, a state that says *not playing* and names either nothing or a track the user has
 * already left is held back rather than drawn.
 *
 * **Held, never discarded.** The phone dedupes its own transmissions (`equalsIgnoringTime`), so a
 * state thrown away here could be the only copy that is ever sent - a skip that silently failed
 * would then leave the watch showing playback that is not happening, for the rest of the session.
 * Every held state is kept and applied when the window closes, so the worst case is that the truth
 * arrives late instead of never.
 *
 * Pure and free of `android.*` so it can be pinned by a plain JVM test, the same convention as
 * [PredictedTrackAdvance] - which is the other half of this: that one decides *what* to show in the
 * gap, this one decides what not to let overwrite it.
 */
object TrackChangeHold {

    /**
     * How long the window stays open after the last thing the phone said about the transition.
     *
     * Measured from that, not from the press, and this is the correction that made it work. A
     * window timed from the press alone expired in the middle of the sequence it was covering:
     * the phone reported the pause, then the new track still paused, and the resume - which is a
     * streaming client buffering, not a fixed cost - arrived after it had closed. The held pause
     * was then published, which is precisely the "it skips, then flashes Stopped, then comes
     * right" report. Each transitional state therefore pushes the window out again, and a real
     * pause ends it by simply not being followed by anything.
     */
    const val HOLD_MS = 4_000L

    /**
     * The longest the window may stay open in total, however many transitional states arrive.
     *
     * The extension above is bounded by this and by nothing else, so a player that keeps
     * republishing paused states cannot make the watch claim playback indefinitely. Reaching it
     * means the phone has been saying *not playing* for twelve seconds, which is not a transition
     * by any reading.
     */
    const val MAX_HOLD_MS = 12_000L

    /**
     * How far into a track a paused state stops looking like a transition.
     *
     * A source swap reports the new track at (or near) its start, while a pause the user made
     * reports wherever they were listening. Without this the window would cover a genuine pause
     * made on the phone in the seconds after a skip - the watch would go on drawing playback that
     * had stopped. The cost of the exception is symmetrical and much smaller: a pause made inside
     * the first two seconds of a new track still waits out the window.
     */
    const val TRANSITION_POSITION_MS = 2_000L

    /**
     * The most titles kept as "already left behind" during one window.
     *
     * A burst of skips passes through several tracks, and each one's paused echo can still be in
     * flight when the next press lands - so the whole burst's worth has to be recognisable, not
     * just the last one. Bounded because this is a memo, not a history: a window is a few seconds
     * long and nobody presses past this many tracks inside one.
     */
    const val MAX_TITLES_LEFT_BEHIND = 8

    enum class Decision {
        /** Draw it, and end the window - the phone has moved past the transition. */
        APPLY,

        /** Keep it back; apply it unchanged if the window closes with nothing better. */
        DEFER,

        /**
         * Draw it as *playing*, and keep the original for the window's close.
         *
         * The state names a track the user has not seen yet, so its metadata is wanted at once -
         * but a player that has published new metadata and not yet resumed is mid-transition, and
         * turning the control over for the moment in between is the flicker itself. The unmodified
         * state is still held, so a player that genuinely stopped on that track is reported when
         * the window closes rather than covered up indefinitely.
         */
        ASSUME_PLAYING,
    }

    /**
     * @param holdActive whether a skip made here is still within its window.
     * @param titlesLeftBehind the tracks that were on screen when the skips in this window were
     *   made - the ones whose paused echoes are meaningless by the time they arrive.
     * @param incomingPositionMs where the incoming state says playback is. Only consulted for a
     *   track the window has not seen before - see [TRANSITION_POSITION_MS].
     */
    fun decide(
            holdActive: Boolean,
            incomingTitle: String?,
            incomingPlaying: Boolean,
            incomingPositionMs: Long,
            titlesLeftBehind: Collection<String>,
    ): Decision {
        if (!holdActive) {
            return Decision.APPLY
        }
        // Playing is never held: it is the phone reporting that the transition is over, which is
        // both true and the exact thing the window is waiting for.
        if (incomingPlaying) {
            return Decision.APPLY
        }
        // No title at all is the gap between one source being torn down and the next being ready.
        // It is also what an idle phone looks like, which is why it is deferred rather than
        // dropped - if playback really did end, the window closing says so.
        if (incomingTitle.isNullOrBlank()) {
            return Decision.DEFER
        }
        // Deferred whatever the position says: playback did reach the end of a track the user has
        // left, so a late sample from it describes nothing anybody is looking at.
        if (titlesLeftBehind.any { PredictedTrackAdvance.isSameTrack(it, incomingTitle) }) {
            return Decision.DEFER
        }
        if (incomingPositionMs > TRANSITION_POSITION_MS) {
            return Decision.APPLY
        }
        return Decision.ASSUME_PLAYING
    }
}
