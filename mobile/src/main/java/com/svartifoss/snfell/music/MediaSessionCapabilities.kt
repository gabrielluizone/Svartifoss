package com.svartifoss.snfell.music

import android.support.v4.media.session.PlaybackStateCompat

/**
 * Reads what a session says it can do, so a transport feature can be offered only where it works.
 *
 * Worth having as its own (pure, testable) decision because the honest answer has three states, not
 * two - see [advertises]. Apps differ wildly here: Retro Music's playback session advertises exactly
 * seven actions and implements nothing else, while a Media3 client freshly woken by a browser
 * binding advertises nothing at all for the first moments of its life.
 */
object MediaSessionCapabilities {

    /**
     * Whether [action] is worth issuing against a session whose advertised [actions] bitmask is
     * [actions], with null meaning "no playback state yet".
     *
     * A missing state resolves to **true**, deliberately. A session that has not published a state
     * has not declined anything - it is usually one that was just bound and is still starting up,
     * which is exactly the case every wake-then-play path depends on. Treating unknown as
     * unsupported would break the one flow that most needs to work; the cost of guessing wrong is
     * an ignored command, which every caller already tolerates.
     */
    fun advertises(actions: Long?, action: Long): Boolean {
        if (actions == null) return true
        return actions and action != 0L
    }

    /** Convenience for the search case, which is the one with a real alternative to fall back to. */
    fun advertisesPlayFromSearch(actions: Long?): Boolean =
            advertises(actions, PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)

    // Deliberately no advertisesSkipToQueueItem(). Gating the queue tap on
    // ACTION_SKIP_TO_QUEUE_ITEM broke every player that implements the command without advertising
    // it, which turns out to be common - the bitmask is a hint, not a contract. That path now
    // issues the command unconditionally and checks whether playback actually moved, because an
    // unsupported transport command is a harmless no-op while a wrong guess is a dead queue. See
    // MusicService.onQueueEntrySelected.
}
