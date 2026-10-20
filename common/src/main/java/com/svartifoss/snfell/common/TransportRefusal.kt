package com.svartifoss.snfell.common

import kotlin.math.abs

/**
 * A transport command the playing app did not carry out, and does not claim to support - the
 * phone's answer that lets the watch say "won't skip right now" instead of leaving a press that
 * did nothing to look like a broken button.
 *
 * Two conditions, both required, and the order of the reasoning matters. Commands are always
 * *sent* - a session's advertised actions are routinely wrong, and withholding a command on a
 * missing bit loses a working feature (see `MediaSessionCapabilities`). So the phone issues the
 * command, waits, and only when nothing moved **and** the session never claimed the action does it
 * report a refusal: a session that advertises skipping but is slow to answer is not refused, and
 * one that answers without advertising is not either. Players that block skipping on purpose - an
 * advert, a free tier's skip limit - are the case this exists for.
 *
 * Crosses as a code (`CommPaths.MESSAGE_ACTION_REFUSED`), worded on the watch; an unknown code is
 * ignored.
 */
enum class TransportRefusal(val code: Int) {
    SKIP_NEXT(1),
    SKIP_PREVIOUS(2),
    SEEK(3);

    companion object {
        /** How long the phone waits for a skip or seek to show before calling it refused - long
         *  enough for a streaming player to buffer the next track and publish it. */
        const val VERIFY_MS = 3_000L

        /** A position this far from where playback would have been anyway counts as having moved. */
        const val POSITION_TOLERANCE_MS = 2_500L

        fun fromCode(code: Int): TransportRefusal? = values().firstOrNull { it.code == code }

        /** A skip was refused: the track did not change and the session never claimed it could. */
        fun skipRefused(advertised: Boolean, trackChanged: Boolean, restartedTrack: Boolean): Boolean =
                !advertised && !trackChanged && !restartedTrack

        /**
         * Whether a previous press that left the track alone at least went back to its start -
         * which is what a player does with "previous" past the first few seconds, and is an answer.
         */
        fun restartedTrack(positionBeforeMs: Long?, positionAfterMs: Long?): Boolean =
                positionBeforeMs != null && positionAfterMs != null &&
                        positionAfterMs < positionBeforeMs - POSITION_TOLERANCE_MS

        /**
         * A seek was refused: the session never claimed it could seek, playback is where it would
         * have been without the seek, and the target was somewhere else. Unknown positions are
         * never a refusal - nothing can be said about them.
         */
        fun seekRefused(
                advertised: Boolean,
                targetMs: Long,
                expectedWithoutSeekMs: Long?,
                actualMs: Long?
        ): Boolean {
            if (advertised || expectedWithoutSeekMs == null || actualMs == null) return false
            val wouldHaveMoved = abs(targetMs - expectedWithoutSeekMs) > POSITION_TOLERANCE_MS
            val stayed = abs(actualMs - expectedWithoutSeekMs) <= POSITION_TOLERANCE_MS
            return wouldHaveMoved && stayed
        }
    }
}
