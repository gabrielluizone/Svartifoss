package com.svartifoss.snfell.common

/**
 * Pseudo screen-buttons for the row of up to three user-configurable mini buttons shown near the
 * bottom of the watch's now-playing screen. Where the row sits is decided by the watch, not by a
 * preference: it rests at the lowest margin that keeps the whole row inside a round display and
 * lifts only as far as it must to clear the track text. (The old
 * [MiscPreferences.WEAR_SCREEN_BUTTONS_OFFSET] is retired - see its own note for why the key has
 * to stay.) Which arrangement the row takes is `MiniButtonPlacement`. Like [SwipeGesture], each
 * slot flows
 * through the exact same [com.svartifoss.snfell.common.buttonconfig.ButtonInfo] /
 * PhoneAction pipeline as the four screen quadrants - the buttonCode values just live outside
 * both [ScreenQuadrant]'s 0..3 range and [SwipeGesture]'s 4..6 range so nothing collides in
 * the same config map.
 *
 * Unlike quadrants and swipes these are *visible* buttons: the watch renders each configured
 * slot's action icon and hides unconfigured slots entirely (and the whole row when no slot is
 * configured), so the now-playing screen looks unchanged until the user opts in. Each slot
 * supports single tap and long press, but not double tap - a visible button that waits for the
 * double-tap timeout before reacting would feel broken.
 */
object ScreenButtons {
    const val SLOT_1 = 7
    const val SLOT_2 = 8
    const val SLOT_3 = 9

    /** In on-screen order, left to right. */
    val ALL_SLOTS = intArrayOf(SLOT_1, SLOT_2, SLOT_3)
}
