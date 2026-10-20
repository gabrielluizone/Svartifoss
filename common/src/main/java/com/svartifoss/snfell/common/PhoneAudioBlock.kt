package com.svartifoss.snfell.common

/**
 * Why the phone may be playing without being heard, as the watch is told it.
 *
 * "I pressed play on the watch and nothing happened" has causes the watch cannot see: the phone is
 * in Do Not Disturb with media silenced, its media volume is at zero or muted, or - with headphones
 * - Android is holding the volume at the hearing-safety level until someone confirms a louder one
 * on the phone itself. Each of these looks, from the wrist, exactly like a press that failed. The
 * phone measures them (`MusicService.phoneAudioBlock`) and sends one of these codes in
 * `MusicState.audioBlock`, and the watch says so where the volume is shown.
 *
 * Crosses as a **code**, never a label, for the reason the metadata face's codes do: a phone
 * service's strings do not reliably follow the in-app language, so the watch owns the wording.
 * Codes are part of the wire format: never renumber one, and a code the watch does not know reads
 * as [NONE] - a newer phone must not make an older watch show something wrong.
 */
enum class PhoneAudioBlock(val code: Int) {
    NONE(0),

    /** Do Not Disturb is on and its policy silences media. */
    DO_NOT_DISTURB(1),

    /** The media stream is muted, or at zero. */
    MUTED(2),

    /** A louder level was asked for and the phone held it at the hearing-safety limit. */
    SAFE_VOLUME_HELD(3);

    companion object {
        fun fromCode(code: Int): PhoneAudioBlock = values().firstOrNull { it.code == code } ?: NONE

        /**
         * The one reason to report, most specific first.
         *
         * Do Not Disturb wins over a muted stream because silencing media is *how* it acts - the
         * stream reads as muted while it is on - and "turn off Do Not Disturb" is the instruction
         * that actually helps, where "unmute" would send someone to a volume that is already
         * fine. A held volume is last: music is audible then, just quieter than asked for.
         *
         * Nothing is reported for playback that is not on the phone's own speaker or headphones
         * ([localPlayback] false - casting, a remote session), whose loudness none of these
         * settings decide.
         */
        fun resolve(
                localPlayback: Boolean,
                dndSilencesMedia: Boolean,
                streamMuted: Boolean,
                streamVolume: Int,
                safeVolumeHeld: Boolean
        ): PhoneAudioBlock = when {
            !localPlayback -> NONE
            dndSilencesMedia -> DO_NOT_DISTURB
            streamMuted || streamVolume <= 0 -> MUTED
            safeVolumeHeld -> SAFE_VOLUME_HELD
            else -> NONE
        }

        /**
         * Whether a volume command was held back by the hearing-safety limit: more was [requested]
         * than the phone [applied], while headphones were the output. The limit only ever applies
         * to headphones, so the same shortfall on the speaker is a player's rounding, not this.
         */
        fun heldBySafetyLimit(requested: Int, applied: Int, headphonesOutput: Boolean): Boolean =
                headphonesOutput && requested > applied
    }
}
