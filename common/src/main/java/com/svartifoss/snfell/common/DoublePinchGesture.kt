package com.svartifoss.snfell.common

import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP

/**
 * Pseudo screen-button for Wear OS's primary one-handed gesture. On the currently supported
 * Pixel watches that gesture is a double pinch; the platform deliberately reports the semantic
 * primary action instead of making apps depend on a particular physical motion. Like the other
 * pseudo inputs, it uses the normal [ButtonInfo] action-config pipeline, so its assignment is
 * independently selected for playing and stopped playback states.
 */
object DoublePinchGesture {
    /** Follows [CenterButton.TAP] (14) without colliding with an existing on-screen input. */
    const val DOUBLE_PINCH = 15

    /** The hand gesture itself happens once, so it has one configurable action. */
    fun buttonInfo() = ButtonInfo(false, DOUBLE_PINCH, GESTURE_SINGLE_TAP)
}
