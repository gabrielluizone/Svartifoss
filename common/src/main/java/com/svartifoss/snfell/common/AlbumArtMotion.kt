package com.svartifoss.snfell.common

/**
 * Which way a cover change is travelling.
 *
 * Music moves forward, so [FORWARD] is the answer for everything - a skip, a track ending on its
 * own, a player swapping source - and [BACKWARD] is the one exception the user asked for out loud
 * by pressing previous. Deliberately not a three-state enum with an "unknown": a cover that arrives
 * without a press behind it is not a direction the screen has to guess at, it is the ordinary
 * forward case, and a third state would only mean some track changes drifted and others did not
 * for reasons invisible from the wrist.
 */
enum class CoverChangeDirection { FORWARD, BACKWARD }

/**
 * How a newly delivered cover travels into place.
 *
 * Separate from [AlbumArtMotion] so the host can resolve it once and
 * publish it, rather than each renderer re-deriving it from the same two inputs - the phone's
 * preview, the classic View face and every Compose face all have to agree, and a cover that
 * settles on one face and snaps on the next is exactly the kind of drift this project keeps
 * finding.
 *
 * [enterScale] and [enterShiftFraction] describe only the **incoming** picture. The outgoing one
 * is never faded out underneath it: two half-transparent copies over a black backdrop dip in
 * luminance halfway through, which reads as the screen blinking rather than as one cover replacing
 * another.
 */
data class CoverMotion(
        /** How long the whole change takes. Zero means "no animation at all" - see [animates]. */
        val durationMs: Int,
        /** The scale the incoming cover starts at, settling to 1. Always >= 1, so an overscaled
         *  cover crops rather than leaving a gap at the bezel while it is off-centre. */
        val enterScale: Float,
        /** Where the incoming cover starts, as a fraction of its own width; positive enters from
         *  the right. Settles to 0. Must stay well inside the margin [enterScale] buys, or the
         *  drift exposes an edge. */
        val enterShiftFraction: Float
) {
    val animates: Boolean get() = durationMs > 0

    /**
     * The same motion with no lateral travel.
     *
     * For the first cover of a session, which is not replacing anything: it arrives out of the
     * backdrop, and a drift would claim a direction nobody moved in.
     */
    fun settleOnly(): CoverMotion = copy(enterShiftFraction = 0f)

    companion object {
        val NONE = CoverMotion(durationMs = 0, enterScale = 1f, enterShiftFraction = 0f)
    }
}

/**
 * The one place the cover transition is described, for every surface that draws one.
 *
 * It used to be a flat 300 ms alpha ramp on the host's `ImageView` and nothing at all on seven of
 * the faces that draw their own cover - so the same track change dissolved on Classic, dissolved
 * on Vinyl's mini disc, and snapped on Split, Note, Chat, Metadata, Ribbon, Frame and Depth, with
 * `wear_album_art_fade` silently applying to some of those and not others.
 *
 * The easing is Material 3's emphasized-decelerate curve, quoted as its four control points rather
 * than as a platform object because the three renderers build it from three different types
 * (`PathInterpolator`, Compose's `CubicBezierEasing`, and the phone preview's own interpolation) -
 * the numbers are the shared part, the objects are not.
 */
object AlbumArtMotion {

    /**
     * Long enough for the settle to be a movement rather than a twitch, short enough that a burst
     * of skips does not queue up behind it. The previous 300 ms was chosen for a plain dissolve,
     * where there is nothing to read but the alpha.
     */
    const val DURATION_MS = 400

    /**
     * How much larger the incoming cover starts.
     *
     * It is also the whole margin the drift has to live inside: at 1.06 the picture overhangs the
     * view by 3% on each side, so [SHIFT_FRACTION] must stay under that or the far edge of the
     * cover walks into the frame.
     */
    const val ENTER_SCALE = 1.06f

    /** Lateral travel, as a fraction of the cover's width. See [ENTER_SCALE] for its ceiling. */
    const val SHIFT_FRACTION = 0.025f

    /**
     * How long a "previous" press keeps owning the direction.
     *
     * Generous on purpose: the press only *asks*, and the cover that answers it comes back over
     * Bluetooth after the phone has told the player, the player has swapped source and the new
     * artwork has been encoded and replicated. A window too tight would drift the wrong way
     * precisely on the slow connections where the transition is most visible. It is bounded at all
     * because the next track after the one you stepped back to is going forward again.
     */
    const val BACKWARD_WINDOW_MS = 6_000L

    /** Material 3 emphasized decelerate: leaves fast, arrives slowly, never overshoots. */
    const val EASE_X1 = 0.05f
    const val EASE_Y1 = 0.7f
    const val EASE_X2 = 0.1f
    const val EASE_Y2 = 1f

    /**
     * Which way this cover change is going.
     *
     * A negative [msSinceSkip] is treated as no recent press rather than as a fresh one: the two
     * clocks involved are both the watch's own monotonic clock, so it cannot legitimately happen,
     * and reading an uninitialised timestamp as "just now" would drift every first cover of a
     * session backwards.
     */
    fun directionFor(lastSkipWasBackward: Boolean, msSinceSkip: Long): CoverChangeDirection =
            if (lastSkipWasBackward && msSinceSkip in 0..BACKWARD_WINDOW_MS) {
                CoverChangeDirection.BACKWARD
            } else {
                CoverChangeDirection.FORWARD
            }

    /**
     * The motion a delivered cover gets, or [CoverMotion.NONE] when the user has turned the
     * transition off.
     *
     * `wear_album_art_fade` stays the single switch for all of it. It was written when the
     * transition was only a fade, and its name still says so, but what it has always meant on the
     * wrist is "animate a cover change or swap it outright" - splitting it into a fade switch and a
     * motion switch would ask the user a question about an implementation detail.
     */
    fun resolve(fadeEnabled: Boolean, direction: CoverChangeDirection): CoverMotion =
            if (!fadeEnabled) {
                CoverMotion.NONE
            } else {
                CoverMotion(
                        durationMs = DURATION_MS,
                        enterScale = ENTER_SCALE,
                        enterShiftFraction = when (direction) {
                            CoverChangeDirection.FORWARD -> SHIFT_FRACTION
                            CoverChangeDirection.BACKWARD -> -SHIFT_FRACTION
                        })
            }
}
