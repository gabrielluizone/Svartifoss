package com.svartifoss.snfell.common

/**
 * What turning the rotary input does on the now-playing screen.
 *
 * Rotary input is not only the physical crown: every non-Classic Galaxy Watch reports a finger
 * sliding around the *touch bezel* as rotary scroll, which is the same physical gesture as an
 * edge-seek drag on the progress ring. That overlap is why [OFF] exists - before it, the choice
 * was volume-or-seek with no way to leave the rim to touch seeking alone.
 */
enum class RotaryAction(val preferenceValue: String) {
    /** Turning changes the phone's media volume (the historical default). */
    VOLUME("volume"),

    /** Turning scrubs the playback timeline. */
    SEEK("seek"),

    /** Rotary input is ignored entirely, leaving the rim to edge-seek touch drags. */
    OFF("off");

    companion object {
        /**
         * Resolves the stored value, falling back to the legacy `rotary_seek` boolean when the
         * three-way key has never been written. [legacyRotarySeek] is what that boolean held; a
         * value of true predates this preference and meant "seek instead of volume".
         *
         * Kept as a pure function (rather than reading SharedPreferences here) so `common` stays
         * free of Android preference plumbing and both sides decode identically.
         */
        fun resolve(storedValue: String?, legacyRotarySeek: Boolean): RotaryAction {
            entries.firstOrNull { it.preferenceValue == storedValue }?.let { return it }
            return if (legacyRotarySeek) SEEK else VOLUME
        }
    }
}
