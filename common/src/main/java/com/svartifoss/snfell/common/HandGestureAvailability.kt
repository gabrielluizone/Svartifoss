package com.svartifoss.snfell.common

/**
 * Availability of a supported input for the primary one-handed gesture ([DoublePinchGesture]).
 *
 * Every way this input can fail looks identical from the wrist - the hand moves and nothing
 * happens - and three of the four causes are outside the app entirely: the watch has no gesture
 * hardware, the gesture is switched off in the watch's own settings, or the phone has never heard
 * from the watch at all. Until the watch reported this, the phone's Controls screen offered the
 * assignment with the same confidence it offers a screen tap, and a user whose watch simply does
 * not have the hardware had no way to learn that except by asking.
 *
 * Sent as `WatchInfo.handGesture` and rendered by the phone, so the wire form is a stable code
 * rather than an ordinal: a watch running an older build sends nothing at all, which reads back as
 * [UNKNOWN], and a watch running a *newer* build must never be able to shift the meaning of a
 * value this phone already knows.
 */
enum class HandGestureAvailability(val code: Int) {
    /** No answer: a watch build from before this field, or a probe that threw. */
    UNKNOWN(0),

    /** Neither the public gesture API nor a supported vendor gesture sensor is available. */
    UNSUPPORTED(1),

    /** Supported, but turned off in the watch's own Settings. Nothing is emitted while it is. */
    DISABLED(2),

    /** Input available while the player is open and awake; does not guarantee recognition. */
    READY(3);

    companion object {
        fun fromCode(code: Int): HandGestureAvailability =
                values().firstOrNull { it.code == code } ?: UNKNOWN
    }
}
