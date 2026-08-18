package com.svartifoss.snfell.common

/**
 * What a long press on the centre of the now-playing screen does.
 *
 * Exists as a three-way choice because the gesture used to be a single boolean
 * ([MiscPreferences.WEAR_CENTER_LONG_PRESS_QUEUE], default off) that only ever meant "open the
 * queue". That left the gesture doing nothing at all for anyone who had not turned it on, which is
 * where the on-watch face picker goes: reachable without touching the phone, and without taking the
 * gesture away from the people who did opt into the queue.
 */
enum class CenterLongPressAction {
    /** Open the on-watch face picker. */
    FACES,

    /** Open the playback queue - what the legacy boolean meant when it was on. */
    QUEUE,

    /** Do nothing, for anyone who wants the centre to be play/pause and nothing else. */
    NONE;

    companion object {
        const val VALUE_FACES = "faces"
        const val VALUE_QUEUE = "queue"
        const val VALUE_NONE = "none"

        /**
         * Resolves the stored preference. Anything unset resolves to [FACES].
         *
         * Deliberately does **not** consult the legacy
         * [MiscPreferences.WEAR_CENTER_LONG_PRESS_QUEUE] boolean. It briefly did, on the reasoning
         * that an install which had opted into long-press-for-queue should keep it - but that
         * boolean is off by default, so for almost everyone it only had the effect of keeping the
         * gesture inert, and for the few who had set it the result was a long press that opened the
         * queue when they were reaching for the face picker. The queue has other routes (the
         * actions menu, a mini button, Chat's own action row); an on-watch way to change the face
         * has none. Anyone who does want the queue on this gesture picks it explicitly, and that
         * choice is then honoured above.
         *
         * An unrecognised value resolves to [FACES] rather than [NONE]: a value that cannot be
         * parsed is a bug or a newer build's key, and silently disabling a gesture is a worse
         * outcome than performing the default one.
         */
        fun resolve(value: String?): CenterLongPressAction =
                when (value?.trim()?.lowercase()) {
                    VALUE_QUEUE -> QUEUE
                    VALUE_NONE -> NONE
                    else -> FACES
                }
    }
}
