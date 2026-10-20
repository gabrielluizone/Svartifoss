package com.svartifoss.snfell.music

/**
 * When a sleep timer set to "the end of this track" should pause, decided from what the session
 * says about the track and nothing else.
 *
 * Two mechanisms, because neither is enough alone:
 *
 * - **Scheduled**: while the track plays with a known length, pause [END_MARGIN_MS] before the
 *   position reaches it. This is the one that normally fires, and the only one that pauses *before*
 *   the next track has started. Rescheduled on every state the session publishes, so a seek, a
 *   pause or a speed change moves it.
 * - **At the boundary**: when the track changes and the previous one was within
 *   [NATURAL_END_TOLERANCE_MS] of its end, it ended - pause now. A fraction of a second of the next
 *   track may already have played, which is the cost of a player whose position reports did not
 *   let the schedule land in time. A change while the previous track was nowhere near its end is a
 *   skip, and the timer moves on to the new track rather than pausing it: "stop when this song is
 *   over" does not mean "stop the moment I press next".
 *
 * A live stream - one the phone has *confirmed* has no length (`MusicState.live`) - can only end
 * by changing, so for one every change counts. A track whose length is merely not known yet is a
 * different thing and counts for nothing: players publish a duration of 0 for a moment around
 * every skip, and reading that moment as a live stream paused the next track the instant it
 * changed again (seen on the test phone while tracks were being skipped quickly).
 *
 * Pure, so it can be pinned without a session; [SleepTimer] owns the clock and the handler.
 */
object EndOfTrackSleep {
    /** Pause this long before the track's end: late enough to keep the last note, early enough
     *  that a handler running a little late still lands before the next track starts. */
    const val END_MARGIN_MS = 300L

    /** A track that changed within this much of its end is taken to have finished on its own. */
    const val NATURAL_END_TOLERANCE_MS = 3_000L

    /** One reading of the session, with the position as it was at [atRealtimeMs]. */
    data class Playback(
            val trackKey: String,
            val positionMs: Long,
            val durationMs: Long,
            val playing: Boolean,
            val speed: Float,
            val atRealtimeMs: Long,
            /** Confirmed as a live stream, which has no end but a change - see the class kdoc. */
            val live: Boolean = false
    ) {
        /** Where this reading says playback is at [nowRealtimeMs]. */
        fun positionAt(nowRealtimeMs: Long): Long {
            if (!playing) return positionMs
            val elapsed = (nowRealtimeMs - atRealtimeMs).coerceAtLeast(0L)
            return positionMs + (elapsed * effectiveSpeed()).toLong()
        }

        /** Real time until the track ends, from [nowRealtimeMs]; null when its length is unknown. */
        fun timeLeftMs(nowRealtimeMs: Long): Long? {
            if (durationMs <= 0L) return null
            val left = (durationMs - positionAt(nowRealtimeMs)).coerceAtLeast(0L)
            return (left / effectiveSpeed()).toLong()
        }

        private fun effectiveSpeed(): Float = if (speed > 0f) speed else 1f
    }

    sealed class Decision {
        /** The track has ended: pause now. */
        object PauseNow : Decision()

        /** Pause after [delayMs] unless another reading changes the answer first. */
        data class PauseIn(val delayMs: Long) : Decision()

        /** Nothing to schedule (paused, or a track with no length): wait for the next reading. */
        object Wait : Decision()
    }

    /**
     * What to do now that [current] has arrived, given the reading before it ([previous], null for
     * the first since the timer was armed).
     */
    fun decide(previous: Playback?, current: Playback): Decision {
        if (previous != null && previous.trackKey != current.trackKey && endedAt(previous, current.atRealtimeMs)) {
            return Decision.PauseNow
        }
        if (!current.playing) return Decision.Wait
        val left = current.timeLeftMs(current.atRealtimeMs) ?: return Decision.Wait
        return Decision.PauseIn((left - END_MARGIN_MS).coerceAtLeast(0L))
    }

    /** Whether the track [reading] describes had finished by [changedAtRealtimeMs]. */
    private fun endedAt(reading: Playback, changedAtRealtimeMs: Long): Boolean {
        if (reading.durationMs <= 0L) return reading.live
        return reading.positionAt(changedAtRealtimeMs) >= reading.durationMs - NATURAL_END_TOLERANCE_MS
    }
}
