package com.svartifoss.snfell.common

/**
 * Pseudo-buttons for the three configurable slots of the watch's quick actions panel (the
 * overlay opened by double-tapping the center of the now-playing screen). Same ButtonInfo /
 * PhoneAction pipeline as [ScreenQuadrant] (0..3), [SwipeGesture] (4..6) and [ScreenButtons]
 * (7..9) - these codes continue that range.
 *
 * Slot semantics on the watch: an *absent* entry means the slot's classic default (Like /
 * Shuffle / Repeat, in slot order) with its live state ring; an entry whose action is
 * NullAction hides the slot; Like/Shuffle/Repeat assignments keep the stateful ring behavior
 * in whatever slot they land; any other action turns the slot into a plain trigger showing
 * that action's icon. The phone writes each assignment into BOTH the playing and stopped
 * configs so the panel behaves the same regardless of playback state.
 */
object QuickPanelButtons {
    const val SLOT_1 = 10
    const val SLOT_2 = 11
    const val SLOT_3 = 12

    /** The long full-width row under the three buttons. Unset = the classic "Up Next" row
     *  (queue preview, opens the queue); NullAction hides it; any other action turns it into
     *  a full-width trigger for that action. */
    const val SLOT_LONG = 13

    /** The three round buttons, in on-panel order, left to right. */
    val ALL_SLOTS = intArrayOf(SLOT_1, SLOT_2, SLOT_3)
}
