package com.svartifoss.snfell.common

/**
 * Pseudo screen-buttons for three of the four full-screen swipe directions on the watch's
 * now-playing screen. Configured and executed through the exact same [com.svartifoss.snfell.common.buttonconfig.ButtonInfo]
 * / PhoneAction pipeline as the four screen quadrants - the buttonCode values are chosen outside
 * [ScreenQuadrant]'s 0..3 range so they never collide with a real quadrant in the same config map.
 * This means a swipe can be assigned any existing phone action, exactly like a quadrant tap can.
 *
 * There's no RIGHT: a right-moving swipe (left-to-right) is the universal Wear OS "swipe to
 * exit/dismiss" gesture, so this app never intercepts it - configuring it would silently break
 * the one gesture every watch owner already expects to work everywhere.
 */
object SwipeGesture {
    val NAMES = arrayOf("Swipe up", "Swipe down", "Swipe left")

    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
}
