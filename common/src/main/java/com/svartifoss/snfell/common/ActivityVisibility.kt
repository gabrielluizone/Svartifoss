package com.svartifoss.snfell.common

/**
 * Shared vocabulary for "when should this element be shown/active", used by the mini-buttons and
 * screen-gestures modes and mirroring the Track-time display option. Kept in `common` so the watch
 * and the phone preview resolve the same stored string identically.
 *
 * "playing" is actual playback; a paused session (still holding a loaded track) and true idle both
 * count as *not* playing, matching the two button configs' playing-vs-stopped split.
 */
object ActivityVisibility {
    const val ALWAYS = "always"
    const val PLAYING = "playing"
    const val PAUSED = "paused"
    const val NEVER = "never"

    /** Whether the element is active for the given playback state. Unknown values default to
     *  [ALWAYS], so a value from a newer build never silently hides the element. */
    fun isActive(mode: String?, isPlaying: Boolean): Boolean = when (mode) {
        NEVER -> false
        PLAYING -> isPlaying
        PAUSED -> !isPlaying
        else -> true
    }
}
