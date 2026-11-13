package com.svartifoss.snfell.music

import kotlinx.coroutines.delay

/**
 * One reading of a session while a play request is being verified.
 *
 * [active] is playing, buffering or connecting; [playing] is playing and nothing else. They are
 * told apart because only the second is evidence - see [awaitRequestedPlayback].
 */
internal data class PlaybackObservation(
        val active: Boolean,
        val identity: List<String?>,
        val positionMs: Long,
        val playing: Boolean = active
)

/**
 * Issues a play request and says whether the app then played what was asked for.
 *
 * A previously playing track is not evidence that a new command was accepted, so the answer needs
 * something to have changed: the session was idle at some point, or holds a different item, or
 * went back to the start.
 *
 * **And only playing is evidence, never buffering or connecting.** This used to return the moment
 * the session became active, which an app does for more reasons than obeying: it buffers while it
 * *tries* to load an address it cannot play and then gives up, and it reports connecting while it
 * comes up on the track it had. Either read as "played", which ended the whole ladder with nothing
 * playing and nothing opened - a shortcut that silently does nothing. A busy session is now waited
 * on instead. It is not sent the command again meanwhile (that would restart whatever it is
 * loading), and the answer is never taken while it is busy: a session still busy when [timeoutMs]
 * runs out gets up to [busyGraceMs] more to either play or give up. Only one still busy after all
 * of that counts as played, which is what a slow network start looks like.
 *
 * The grace is short on purpose. Two strategies can each use it, and the watch gives the whole
 * ladder 30 s to answer before it opens the link by itself (`PhoneUriOpener.VERDICT_BACKSTOP_MS`).
 */
internal suspend fun awaitRequestedPlayback(
        read: () -> PlaybackObservation,
        issue: () -> Unit,
        timeoutMs: Long = 5000,
        pollMs: Long = 200,
        retryMs: Long = 700,
        busyGraceMs: Long = 3000
): Boolean {
    val before = read()
    var sawInactive = !before.active
    var waited = 0L
    var nextCommand = retryMs
    var last = before
    issue()
    fun answered(now: PlaybackObservation): Boolean {
        val restarted = before.positionMs > 2000 && now.positionMs in 0..1000
        return sawInactive || now.identity != before.identity || restarted
    }
    while (waited < timeoutMs) {
        val pause = minOf(pollMs, timeoutMs - waited)
        delay(pause)
        waited += pause
        last = read()
        sawInactive = sawInactive || !last.active
        if (last.playing && answered(last)) return true
        // Idle: the command may have been dropped by a session that was still warming up.
        if (!last.active && waited >= nextCommand && waited < timeoutMs) {
            issue()
            nextCommand = waited + retryMs
        }
    }
    var grace = 0L
    while (last.active && !last.playing && grace < busyGraceMs) {
        val pause = minOf(pollMs, busyGraceMs - grace)
        delay(pause)
        grace += pause
        last = read()
        if (last.playing && answered(last)) return true
    }
    return last.active && !last.playing && answered(last)
}
