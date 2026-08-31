package com.svartifoss.snfell.watch.view

/** Android-free state machine used by [ClaimedGestureHost] to mirror only touch streams that
 * Compose actually claimed. A rejected DOWN must keep falling through to FourWayTouchLayout; if
 * it were observed here as well, the same swipe could execute twice. */
internal class ClaimedTouchStream {
    private var claimed = false

    fun shouldObserve(phase: Phase, handledByChild: Boolean): Boolean {
        if (phase == Phase.DOWN) {
            claimed = handledByChild
        }

        val observe = claimed
        if (phase == Phase.END) {
            claimed = false
        }
        return observe
    }

    enum class Phase { DOWN, CONTINUE, END }
}
