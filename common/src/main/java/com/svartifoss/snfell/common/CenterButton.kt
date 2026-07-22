package com.svartifoss.snfell.common

/**
 * Pseudo screen-button for the always-present center-tap zone of the watch's now-playing
 * screen. Same ButtonInfo/PhoneAction pipeline as [ScreenQuadrant] (0..3), [SwipeGesture] (4..6),
 * [ScreenButtons] (7..9) and [QuickPanelButtons] (10..13) - this code continues that range.
 *
 * Only single tap is exposed here. Double-tap (open/close the quick actions panel) and long-press
 * (open the queue, gated by [MiscPreferences.WEAR_CENTER_LONG_PRESS_QUEUE]) stay the fixed
 * built-in behavior they've always been - single tap was the one gesture with no visible
 * affordance and nothing the user could reassign, defaulting to a play/pause toggle when unset.
 */
object CenterButton {
    const val TAP = 14
}
