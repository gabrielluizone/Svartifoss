package com.svartifoss.snfell.watch.view.face

/**
 * How the Expressive transport row shares its width out while one of its buttons is held.
 *
 * The row is a *group*, not three independent controls: the button under the finger widens and the
 * one beside it gives up exactly that width, so the group's total never changes. That is the rule
 * Wear OS's own `androidx.wear.compose.material3.ButtonGroup` implements ("growing the touched
 * button, while the neighbor(s) shrink to accommodate and keep the group width constant"), and a
 * constant total is what makes the *far* control perfectly static: in a row laid out left to
 * right, a skip button growing by exactly what the centre gives up leaves everything past the
 * centre where it was, to the pixel.
 *
 * The one departure is in how the centre expresses its share, and it is a rendering decision
 * rather than a layout one - see `CookiePlayButton`. The centre is a cookie inscribed in the
 * progress ring, and the ring's radius comes from the box it sits in, so resizing that box in both
 * directions made the whole readout shrink while a *different* button was held, which reads as the
 * progress indicator reacting to playback. It changes **horizontally only** instead: the ring and
 * cookie are drawn scaled along one axis, so the control is squeezed or stretched rather than made
 * larger or smaller, and its height never moves.
 *
 * Pure, and tested as such: the arithmetic is small, every number in it is a judgement about how
 * far a control may be squeezed, and getting one wrong is visible only as a press that looks wrong
 * on a wrist.
 */
internal object TransportPressLayout {

    /** Slot indices. The row is always previous / play-pause / next. */
    const val PREVIOUS = 0
    const val CENTRE = 1
    const val NEXT = 2
    const val SLOTS = 3

    /** How much wider a side button gets at full press. */
    const val SIDE_EXPANSION_DP = 16f

    /**
     * How much wider the centre gets.
     *
     * Less than a side button's, in absolute dp and much less in proportion, because this one is
     * spent stretching a circle: the ring and the cookie widen together and the ring leaves round
     * as it goes. A side capsule only gets longer, which it can do without looking like anything
     * but a longer capsule.
     */
    const val CENTRE_EXPANSION_DP = 12f

    /**
     * The floor under a squeezed control, as a fraction of its resting width.
     *
     * Only reachable by holding both side buttons at once, which nothing in the app asks anyone to
     * do but a second finger can. Without it the centre would be asked to give up two full side
     * expansions - over half of itself - and its ring would close over the cookie inside it.
     */
    const val MIN_WIDTH_FRACTION = .72f

    /** What [slot] gains at full press. */
    fun expansionOf(slot: Int): Float =
            if (slot == CENTRE) CENTRE_EXPANSION_DP else SIDE_EXPANSION_DP

    /**
     * The dp each slot gains (positive) or gives up (negative) right now.
     *
     * @param press each slot's press progress, 0f at rest and 1f held. Values in between are the
     *   spring running, so this is called every frame of a press.
     * @param base each slot's resting width in dp, which only the floor below needs.
     */
    fun deltas(press: FloatArray, base: FloatArray): FloatArray {
        require(press.size == SLOTS && base.size == SLOTS) {
            "The Expressive transport row has exactly $SLOTS slots"
        }
        val out = FloatArray(SLOTS)

        // The centre has a neighbour on each side and takes half from each, so it grows
        // symmetrically and the progress ring inside it does not travel while it stretches.
        val centre = press[CENTRE].coerceIn(0f, 1f) * CENTRE_EXPANSION_DP
        out[CENTRE] += centre
        out[PREVIOUS] -= centre / 2f
        out[NEXT] -= centre / 2f

        // A side button has exactly one neighbour - the centre, for both of them - and takes the
        // whole of its growth from it. The far button is deliberately left out of it entirely:
        // paying for any part of this anywhere else would move it, and a control two places from
        // the finger reacting at all reads as the row rearranging itself rather than as one
        // button being pressed against the thing next to it.
        intArrayOf(PREVIOUS, NEXT).forEach { side ->
            val growth = press[side].coerceIn(0f, 1f) * SIDE_EXPANSION_DP
            out[side] += growth
            out[CENTRE] -= growth
        }

        for (slot in 0 until SLOTS) {
            out[slot] = out[slot].coerceAtLeast(-base[slot] * (1f - MIN_WIDTH_FRACTION))
        }
        return out
    }

    /**
     * The horizontal scale a control of [base] dp draws at once it has gained (or given up)
     * [delta].
     *
     * The centre's content is measured at its resting size and scaled to whatever the layout
     * settled on, rather than being laid out at the new size: the ring's geometry - and the scrub
     * angles read off it - stay in the coordinates they were authored in, and only one axis ever
     * moves. A side button needs none of this; its capsule simply fills the width it was given.
     */
    fun horizontalStretch(base: Float, delta: Float): Float =
            if (base <= 0f) 1f else (base + delta) / base
}
