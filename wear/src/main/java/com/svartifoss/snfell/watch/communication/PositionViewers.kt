package com.svartifoss.snfell.watch.communication

/**
 * Which screens are showing the playback position, and which of them are showing it *move*.
 *
 * Two pieces of upkeep on the connection to the phone exist for those screens alone, and they do
 * not want the same answer:
 *
 * - **The covers sent ahead for the neighbouring tracks** are decoded for any screen on display,
 *   awake or always-on ([any]): a stem button skips from an always-on screen, and the cover it
 *   lands on has to be ready.
 * - **The position check against the phone** is for a readout that moves ([anyLive]): it keeps a
 *   lyric line or a progress ring within about a sixth of a second of the music. An always-on
 *   screen redraws about once a minute, where that precision buys nothing, and it is the one state
 *   a watch can sit in for hours. Each check is a message to the phone and its reply - both radios
 *   and the watch's processor - and every track change put the cadence back at its floor, so with
 *   the player left open on the wrist the checks ran all through the always-on display.
 *
 * A viewer is live from the moment it registers until its screen says otherwise, so a screen that
 * knows nothing of ambient mode (the progress screen, which closes when the display turns off)
 * needs no second call.
 *
 * Pure and free of `android.*`. Not thread-safe, like the `PlaybackClock` beside it: everything
 * that touches it is on the main thread.
 */
class PositionViewers {

    /**
     * What a call changed. [firstArrived] is the moment to decode covers that came in the dark,
     * [liveStarted] the moment to check the position soon, [liveEnded] the moment to stop checking.
     */
    data class Transition(
            val firstArrived: Boolean = false,
            val liveStarted: Boolean = false,
            val liveEnded: Boolean = false
    )

    private val showing = HashSet<Any>()
    private val live = HashSet<Any>()

    /** Whether any screen is showing the position at all. */
    val any: Boolean get() = showing.isNotEmpty()

    /** Whether any screen is showing the position move. */
    val anyLive: Boolean get() = live.isNotEmpty()

    /** [viewer]'s screen came on display ([isShowing]) or left it. */
    fun setShowing(viewer: Any, isShowing: Boolean): Transition {
        val hadAny = any
        val hadLive = anyLive
        if (isShowing) {
            showing.add(viewer)
            live.add(viewer)
        } else {
            showing.remove(viewer)
            live.remove(viewer)
        }
        return transitionFrom(hadAny, hadLive)
    }

    /**
     * [viewer]'s readout is moving ([isLive]) or is an ambient screen's occasional redraw.
     *
     * Ignored for a viewer that is not showing: an ambient callback that lands after its screen
     * stopped must not bring that screen back as a reason to ask the phone anything.
     */
    fun setLive(viewer: Any, isLive: Boolean): Transition {
        val hadLive = anyLive
        if (isLive && viewer in showing) live.add(viewer) else live.remove(viewer)
        return transitionFrom(any, hadLive)
    }

    private fun transitionFrom(hadAny: Boolean, hadLive: Boolean) = Transition(
            firstArrived = !hadAny && any,
            liveStarted = !hadLive && anyLive,
            liveEnded = hadLive && !anyLive
    )
}
