package com.svartifoss.snfell.common

/**
 * Where the mini-button row sits on the watch, decoded from `screen_buttons_curve_style`.
 *
 * The preference began as a pure *curve* control - a bottom row whose side pills rise and tilt
 * along the bezel - and every value still carries that meaning. What it gained is a second axis:
 * some values move the row somewhere else entirely (down one wall, or spread across the screen)
 * rather than only bending it. Both axes come out of the one preference because to a user they are
 * one question ("how are the mini buttons arranged?"), and splitting them would have meant a second
 * picker whose options are mostly meaningless combinations.
 *
 * This lives in `common` for the reason every appearance decision here does: the watch lays the row
 * out with real Views and the phone's `WatchPreviewView` draws a miniature of it, and the two
 * previously each had their own `when` over the raw string. A placement the preview had not learned
 * would draw a bottom row while the wrist showed a side rail - the exact class of lie the shared
 * resolvers exist to prevent.
 *
 * Unknown values resolve to [FLAT], which is also the default: a value can arrive from an imported
 * backup or a newer phone build, and a straight row is the arrangement that is correct on every
 * screen shape and can never collide with anything.
 */
enum class MiniButtonPlacement(
        val value: String,
        /** How much of the circle's tangent angle each pill is rotated by. */
        val tiltFraction: Float,
        /** Multiplies the bezel clearance each side pill is raised by. */
        val riseScale: Float,
        val axis: Axis = Axis.BOTTOM_ROW
) {
    /** A straight horizontal row. The historical default, and what square screens always get. */
    FLAT("flat", tiltFraction = 0f, riseScale = 0f),

    /** Side pills raised along the bezel but kept upright. */
    ARC("arc", tiltFraction = 0f, riseScale = 1.0f),

    CURVED_GENTLE("curved_gentle", tiltFraction = 0.25f, riseScale = 0.6f),
    CURVED_SOFT("curved_soft", tiltFraction = 0.5f, riseScale = 0.8f),
    CURVED_MEDIUM("curved_medium", tiltFraction = 0.75f, riseScale = 1.0f),
    CURVED("curved", tiltFraction = 1f, riseScale = 1.2f),

    /** Exaggerated tilt. Note the rise scale stays at 1: the extreme case changes the *angle*, not
     *  the bezel clearance - multiplying both lifted the outer pills off the glass instead of
     *  seating them on it. */
    CURVED_EXTREME("curved_extreme", tiltFraction = 2.2f, riseScale = 1.0f),

    /**
     * One pill centred, the others pushed out to the ends of the screen, all still on the bottom
     * band and following the bezel. The row stops reading as a cluster and becomes three separate
     * targets, which is easier to hit without looking.
     */
    SPREAD("spread", tiltFraction = 0.5f, riseScale = 1.0f, axis = Axis.BOTTOM_ROW_SPREAD),

    /** A vertical rail down the left bezel, each pill following the curve. */
    SIDE_LEFT("side_left", tiltFraction = 0f, riseScale = 1.0f, axis = Axis.LEFT_RAIL),

    /** The same rail on the right, which is the side a right-handed wrist reaches first. */
    SIDE_RIGHT("side_right", tiltFraction = 0f, riseScale = 1.0f, axis = Axis.RIGHT_RAIL),

    /**
     * Split between both walls: the first pill on the left, the rest down the right, so nothing
     * sits over the middle of the artwork at all.
     */
    SIDE_SPLIT("side_split", tiltFraction = 0f, riseScale = 1.0f, axis = Axis.SPLIT_RAILS);

    /** How the row is laid out, as opposed to how much it bends. */
    enum class Axis { BOTTOM_ROW, BOTTOM_ROW_SPREAD, LEFT_RAIL, RIGHT_RAIL, SPLIT_RAILS }

    /** True when the pills stack down a wall instead of running along the bottom. */
    val isRail: Boolean
        get() = axis == Axis.LEFT_RAIL || axis == Axis.RIGHT_RAIL || axis == Axis.SPLIT_RAILS

    /**
     * Whether this arrangement bends to the screen at all.
     *
     * [FLAT] is the only one that does not, which is why it is also the one every caller may skip
     * the round-screen geometry for.
     */
    val followsCurve: Boolean
        get() = this != FLAT

    /** Degrees a pill's tilt is clamped to, so a steep tangent near the bezel cannot spin it. */
    val maxRotationDegrees: Float
        get() = if (this == CURVED_EXTREME) 35f else 15f

    /** Ceiling, in dp, on how far a side pill is raised. Without it the circle equation lifts the
     *  outer pills back over the player's own controls on a compact row. */
    val maxRiseDp: Float
        get() = if (this == CURVED_EXTREME) 36f else 18f

    companion object {
        /**
         * Faces that draw the configured mini buttons *inside* their own composition instead of
         * letting the shared row float over them.
         *
         * Chat is the first: its thread runs to the bottom of the screen and it already carries a
         * row of round actions there, so the shared row landed on top of that row - two sets of
         * controls in one band. Hosting them turns the face's own circles into the user's buttons,
         * one per configured slot.
         *
         * It lives beside the placement enum because of what it does to it: a hosted row is placed
         * by the composition, so nothing in this file reaches it. That is a real consequence for
         * the user, which is why the phone hides the curve picker (and the shape picker, since a
         * hosted button keeps the face's own silhouette) for these faces rather than offering
         * controls that change nothing - the same rule Carousel's card shape follows. The
         * *appearance* choices that are not placement - the background style and the group
         * opacity - do still apply, resolved through [MiniButtonSurfaces] exactly as the row
         * resolves them.
         */
        fun isHostedByFace(face: String?): Boolean = face?.trim() in HOSTING_FACES

        private val HOSTING_FACES = setOf("chat")

        fun fromPreference(value: String?): MiniButtonPlacement =
                values().firstOrNull { it.value == value?.trim() } ?: FLAT

        /**
         * Which wall a pill belongs to in [SIDE_SPLIT], by its index in the visible row.
         *
         * The first pill goes left and everything after it goes right, rather than splitting down
         * the middle: with the usual three configured buttons that gives 1 + 2, and with two it
         * gives one per side. An even split would put two on the left of a three-button row, which
         * reads as a mistake rather than as a choice.
         */
        fun splitSideIsLeft(index: Int): Boolean = index == 0
    }
}
