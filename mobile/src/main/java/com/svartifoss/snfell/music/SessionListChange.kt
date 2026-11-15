package com.svartifoss.snfell.music

/**
 * Whether a session list the system reported is news, pinned by `SessionListChangeTest`.
 *
 * `MediaSessionManager` calls every active-sessions listener whenever any app creates, destroys or
 * reorders a media session - inactive ones included - and hands over the list of *active* sessions,
 * which most of those events leave exactly as it was. Every one used to make
 * [ActiveMediaSessionProvider] read the sessions again and, while nothing played, re-resolve and
 * re-publish the paused one, rebuilding the watch's state. On the owner's phone a timer app
 * reconnecting to its own browser service about four times a second turned that into twelve
 * callbacks a second, each one repeated by both listeners (`docs/watch-sync-battery-investigation.md`,
 * "Third pass").
 *
 * The same sessions in the same order say nothing new: which session plays is learned from the
 * sessions' own callbacks, never from this list. Order is part of the list, because the system keeps
 * it most recently active first and that order is what an idle play command follows. And no list
 * remembered (the provider just started listening) is always news.
 */
internal object SessionListChange {
    fun isNew(previous: List<Any>?, current: List<Any>): Boolean = previous != current
}
