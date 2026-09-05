package com.svartifoss.snfell.common

/**
 * When the edge ring's position tick is drawn.
 *
 * The tick is the short radial mark crossing the progress band. It was built for one job: while a
 * finger is dragging the ring, the arc belongs to the finger, so nothing else on screen says where
 * the track actually *is* — the mark is the reference point you are leaving from. That is still
 * what [DURING_SEEK] does, and it stays the default because it is what every existing install
 * already sees.
 *
 * The other three exist because the same mark is useful at rest for a different reason: it reads
 * as a playhead. A ring is a smooth arc whose end is easy to lose against a busy cover, and a
 * crossing tick is legible where an arc end is not.
 *
 * **Where the mark sits differs between the two cases, and that is not a detail.** During a drag it
 * marks the *origin* (`dragOriginProgress`, which keeps advancing because the track keeps playing);
 * at rest there is no origin to mark, so it sits on the current position. Both are "where the track
 * is" — they only diverge while a finger has taken the arc away.
 *
 * [WHILE_PAUSED] deliberately hides the mark during a drag while music plays, which is the one
 * combination that gives up the affordance the tick was originally built for. It is offered anyway
 * because it is a coherent request — mark the spot only when the spot is not moving — and because
 * withholding an option to protect a default nobody chose is the wrong way round.
 */
enum class SeekMarkerVisibility(val preferenceValue: String) {
    /** Always on the ring: while playing, while paused, and while seeking. */
    ALWAYS("always"),

    /** Only while a seek gesture is in progress. The original behaviour, and the default. */
    DURING_SEEK("drag"),

    /** While seeking, and at rest whenever playback is paused. */
    DURING_SEEK_OR_PAUSED("drag_paused"),

    /** Only while playback is paused, including during a seek made while paused. */
    WHILE_PAUSED("paused");

    companion object {
        /**
         * Unknown and absent values resolve to [DURING_SEEK].
         *
         * The same reasoning `PausedHoldPolicy` records: a value can arrive from an imported
         * backup, a community theme or a newer phone build, and falling back to the behaviour every
         * install already has is the only answer that cannot surprise anyone.
         */
        fun fromPreference(value: String?): SeekMarkerVisibility =
                entries.firstOrNull { it.preferenceValue == value } ?: DURING_SEEK

        /**
         * Whether the tick should be drawn right now.
         *
         * [playing] means real playback; a paused session and true idle both count as not playing,
         * matching how `MainActivity` resolves `wear_track_time_mode`. Callers still gate this on
         * the ring being drawn at all: the tick is a mark *on* the band, and a lone dash at the
         * screen edge with no ring under it is not the same object.
         */
        fun shouldDraw(
                visibility: SeekMarkerVisibility,
                dragging: Boolean,
                playing: Boolean
        ): Boolean = when (visibility) {
            ALWAYS -> true
            DURING_SEEK -> dragging
            DURING_SEEK_OR_PAUSED -> dragging || !playing
            WHILE_PAUSED -> !playing
        }
    }
}
