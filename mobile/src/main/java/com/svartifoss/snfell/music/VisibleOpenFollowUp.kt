package com.svartifoss.snfell.music

/**
 * What the phone does after a shortcut had to be opened *visibly* - the last rung of
 * `MusicService.playDeepLink`, where the content is on screen in its app and nothing says it will
 * play. One instance follows one open; [next] is asked on every reading of the app's session and
 * answers with the single thing worth doing at that moment.
 *
 * Three rules, each written after the behaviour it replaces went wrong on a phone.
 *
 * **The app is asked for the content once its session has settled, a few times at most.** The loop
 * this replaces sent the request every 200 ms for 3.2 s and then stopped for good: eight copies in
 * a second to a session that takes the command without advertising it, and none at all to an app
 * whose cold start outlasted the window - which is the ordinary length of a streaming app's cold
 * start once the open itself has crossed from the watch. A request is now sent when the session
 * has been out of its connecting and buffering states for [STEADY_BEFORE_REQUEST_MS], because a
 * command sent into an app that is still coming up is the one that gets lost, and an app that was
 * about to start the content by itself should not be sent it a second time.
 *
 * **Only [STATE_PLAYING] is evidence.** A cold app reports connecting or buffering within a second
 * whatever it was asked, while it restores the track it played last (`docs/streaming-app-interop.md`
 * section 5.6).
 *
 * **A bare "play" is only pressed on content the app loaded after the open.** Play is a resume, and
 * an app that was not running comes up holding whatever it played last, so pressing it there starts
 * the wrong music under the right page - the shortcut that "opens the app and then just presses
 * play". So the first track a session without one shows is the [baseline], never proof: it is as
 * likely the restored track as the requested one. Content that *replaces* the baseline is the app
 * saying it loaded what it was sent, and that alone is resumed, once it has sat paused for
 * [PRESS_PLAY_SETTLE_MS].
 *
 * Pure and clock-free (the caller passes elapsed time), pinned by `VisibleOpenFollowUpTest`.
 */
internal class VisibleOpenFollowUp(
        /** Whether the app was playing when the shortcut was picked. */
        private val wasPlayingBefore: Boolean,
        /** What its session held then; [PlaybackIdentity.NONE] when it had no session or no track. */
        private val identityBefore: PlaybackIdentity,
        /** Whether there is a precise request (the link, or a name to search) to send at all. */
        private val canRequest: Boolean
) {
    enum class Step {
        /** Nothing to do yet. */
        WAIT,

        /** Send the precise request now: the link, or the saved name for a search. */
        REQUEST,

        /** Press play: the app loaded new content and left it paused. */
        PRESS_PLAY,

        /** The app is playing what was asked for. Stop following. */
        PLAYED
    }

    /** The track new content is measured against: the one from before, else the first one seen. */
    private var baseline: PlaybackIdentity? = identityBefore.takeIf { it.hasTrack }
    private var steadySinceMs = NOT_YET
    private var newContentSinceMs = NOT_YET
    private var lastRequestAtMs = NOT_YET
    private var requestsSent = 0
    private var pressedPlay = false

    /** How many precise requests were sent - what a caller logs when nothing played. */
    val requests: Int get() = requestsSent

    /**
     * @param state the session's `PlaybackState` state, or null when the app has no session (yet).
     * @param identity what the session holds now.
     * @param elapsedMs time since the open was asked for.
     */
    fun next(state: Int?, identity: PlaybackIdentity, elapsedMs: Long): Step {
        if (state == null) {
            steadySinceMs = NOT_YET
            return Step.WAIT
        }

        if (baseline == null && identity.hasTrack) baseline = identity
        val loadedNew = baseline?.let { identity.hasTrack && !identity.isSameTrackAs(it) } == true
        newContentSinceMs = when {
            !loadedNew -> NOT_YET
            newContentSinceMs == NOT_YET -> elapsedMs
            else -> newContentSinceMs
        }

        // Playing where nothing was, or playing something else than before: the open worked.
        // Still playing the very thing it played before means the link has only navigated so far.
        if (state == STATE_PLAYING &&
                (!wasPlayingBefore || !identity.isSameTrackAs(identityBefore))) {
            return Step.PLAYED
        }
        if (state == STATE_BUFFERING || state == STATE_CONNECTING) {
            steadySinceMs = NOT_YET
            return Step.WAIT
        }
        if (steadySinceMs == NOT_YET) steadySinceMs = elapsedMs

        if (canRequest && !loadedNew && requestsSent < MAX_REQUESTS &&
                elapsedMs - steadySinceMs >= STEADY_BEFORE_REQUEST_MS &&
                (lastRequestAtMs == NOT_YET || elapsedMs - lastRequestAtMs >= REQUEST_INTERVAL_MS)) {
            requestsSent++
            lastRequestAtMs = elapsedMs
            return Step.REQUEST
        }

        if (!pressedPlay && loadedNew && state in RESUMABLE_STATES &&
                elapsedMs - newContentSinceMs >= PRESS_PLAY_SETTLE_MS) {
            pressedPlay = true
            return Step.PRESS_PLAY
        }
        return Step.WAIT
    }

    companion object {
        /** `PlaybackState.STATE_*`, repeated so this file stays free of Android types. */
        const val STATE_NONE = 0
        const val STATE_STOPPED = 1
        const val STATE_PAUSED = 2
        const val STATE_PLAYING = 3
        const val STATE_BUFFERING = 6
        const val STATE_CONNECTING = 8

        /** States a bare play can be pressed in; an error state is not one of them. */
        private val RESUMABLE_STATES = setOf(STATE_NONE, STATE_STOPPED, STATE_PAUSED)

        /** How long the session must have been settled before it is sent a request. */
        const val STEADY_BEFORE_REQUEST_MS = 1_000L

        /** A request can still land before the app takes it, so it is repeated - sparingly. */
        const val MAX_REQUESTS = 3
        const val REQUEST_INTERVAL_MS = 1_500L

        /** How long newly loaded content is left to start by itself before play is pressed. */
        const val PRESS_PLAY_SETTLE_MS = 1_500L

        /**
         * How long a plain open is followed, and how often the session is read. Long, because the
         * clock starts when the watch is *told* to open the link: the open then crosses Bluetooth,
         * and the app it lands in is usually starting cold.
         */
        const val FOLLOW_MS = 15_000L
        const val POLL_MS = 250L

        /**
         * Whether the visible fallback first asks the session a refused browser bind woke.
         *
         * Binding an app's browser service starts its process even when the app then turns the
         * connection away, and a cold app comes up with a session that takes the request the
         * browser was refused - YouTube Music 8.30.54 did exactly that (section 5.9 of
         * `docs/streaming-app-interop.md`). Answering the watch with the link at once made it open
         * the app on the phone's screen while the music had already started.
         *
         * Only for an app that was cold (a running one was already asked in step 1, and asking
         * again would only delay the open), that refused rather than connected and did nothing (one
         * that connected was asked through its browser, the same request), and that is the app the
         * link opens in (the follow-up watches the app it opens). The hand-off path does not take
         * it: an app with a declared play technique is better served by the technique.
         */
        fun triesWokenSession(browserRefused: Boolean, appWasCold: Boolean, opensInTheSameApp: Boolean): Boolean =
                browserRefused && appWasCold && opensInTheSameApp

        /** How long the woken app's session may take to appear before the link is opened. */
        const val WOKEN_SESSION_APPEAR_MS = 2_000L

        /**
         * How long the woken session is followed before the link is opened after all: room for the
         * three requests of [MAX_REQUESTS] after a second of settling. YouTube Music played after
         * the third, about 4.4 s in. Together with [WOKEN_SESSION_APPEAR_MS] this is added to the
         * verdict's wait in the worst case, so it stays far under the watch's 30 s backstop.
         */
        const val WOKEN_SESSION_FOLLOW_MS = 6_500L

        private const val NOT_YET = Long.MIN_VALUE
    }
}

/**
 * What a session is holding, reduced to the fields that change when it loads something else: the
 * active queue item, the media id and the title - the three `MusicService.playbackIdentity` reads,
 * for the reasons it gives. Blank strings are no information and must be passed as null.
 */
internal data class PlaybackIdentity(val queueItemId: Long?, val mediaId: String?, val title: String?) {
    /** Whether this names a track at all - a queue position alone does not. */
    val hasTrack: Boolean get() = mediaId != null || title != null

    /**
     * Whether [other] is the same track, as far as the two can be compared.
     *
     * By title where both have one, else by media id, and *the same* when they share no field -
     * deliberately not field-by-field equality. A session fills its metadata in steps when it
     * comes up (a title, then the media id, then a queue position), and each step would otherwise
     * read as the app having loaded something new, which is the one conclusion that lets play be
     * pressed. Erring towards "the same" costs a press on the rare request for a track that shares
     * its title with the one before it; erring the other way starts the wrong music.
     */
    fun isSameTrackAs(other: PlaybackIdentity): Boolean = when {
        title != null && other.title != null -> title == other.title
        mediaId != null && other.mediaId != null -> mediaId == other.mediaId
        else -> true
    }

    companion object {
        val NONE = PlaybackIdentity(null, null, null)
    }
}
