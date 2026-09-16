package com.svartifoss.snfell.common

import kotlin.math.sqrt

/**
 * Where the chrome around a now-playing face sits, and what it leaves the face.
 *
 * "Chrome" here is everything on the player screen that is *not* the face: the clock, the four
 * quadrant hint icons, and whatever occupies the bottom band (the mini-button row, or the Up Next
 * pill that takes the same band when no row is configured). The faces own the middle; nobody owned
 * the edges, and that is the whole of what this object fixes.
 *
 * It exists because the same question - "how much of the screen is already spoken for?" - was being
 * answered nine different ways. Six faces each carried their own `maxOf(screen * <a literal>, ...)`
 * expression, two encodings of the same fact travelled on the face state at once (a fraction from
 * the top and a dp value from the bottom), and the host wrote one of them from three places, one of
 * which folded the other into it. A face combining them double-counted in some states and not
 * others, which is why the screen looked improvised rather than designed. There is one answer here
 * and every renderer reads it: the watch places real Views from it, the phone's `WatchPreviewView`
 * draws its miniature from it, and the faces read [SafeArea] alone.
 *
 * Everything is a plain dp float or a fraction of the screen, for the reason [FaceGeometry] gives:
 * `common` has no Compose and no Android, so each renderer applies the numbers in its own units.
 *
 * ## The round screen
 *
 * A round display has one continuous perimeter and four cardinal points on it, which is exactly as
 * many as there are quadrants. That is the arrangement this resolves to: the hints form a cross on
 * the bezel, identical in size and inset, and **nothing here ever shrinks or displaces a hint to
 * make room**. Two things wanted the top apex (the clock and the top hint) and two wanted the
 * bottom (the row and the bottom hint); the previous answer was to delete one of each pair, and the
 * answer before this one was to shrink them. Both read as a mistake rather than as a design. What
 * yields instead is the face, through [SafeArea], and the two apex conflicts are resolved by depth:
 * the clock takes the outer arc and the hint keeps the apex just inside it ([ClockStyle]), and the
 * row rests on the arc just inside the bottom hint ([Chrome.lowerMarginDp]).
 *
 * One function does the circle geometry for all of it - [bezelInsetFraction], the inverse of
 * [RoundScreenText.halfChordAt]: given something's half-width, how far from the glass it must sit
 * before the chord is wide enough to hold it. It places the mini row, and it is also the floor
 * under every hint's inset, so a scaled-up icon on a small watch moves inwards instead of having
 * its corners cut by the bezel.
 */
object PlayerChromeLayout {

    // ---------------------------------------------------------------------------------------
    // Designed sizes. These are the *only* place the chrome's dimensions are written down.
    // ---------------------------------------------------------------------------------------

    /** The quadrant hint's designed size, matching `music_screen_icon_size`. */
    const val HINT_SIZE_DP = 24f

    /** The hint's designed distance from the glass, matching `music_screen_icon_offset`. */
    const val HINT_EDGE_INSET_DP = 4f

    /**
     * The one breathing gap in this file.
     *
     * Deliberately a single constant used for every clearance - chrome to chrome, and chrome to
     * face - rather than a value per boundary. The nine expressions this replaces used 4dp, 6dp,
     * 10dp and three different screen fractions for the same idea, and no two of them agreed.
     */
    const val CHROME_GAP_DP = 6f

    /**
     * Multiplier turning the clock's text size into the band it occupies.
     *
     * The clock is the one chrome item whose size the user controls (the Typography page scales
     * it), so its band cannot be a constant. A line box is a little taller than its text size;
     * this is that, rounded up rather than measured, because a resolver that cannot lay text out
     * must err on the side of reserving slightly too much.
     */
    const val CLOCK_LEADING = 1.3f

    /** The curved clock hugs the glass, where a straight one keeps Classic's designed top padding. */
    const val CURVED_CLOCK_EDGE_INSET_DP = 2f

    /** A square screen has no chord to follow, so the lower content keeps a flat margin. */
    const val SQUARE_LOWER_MARGIN_DP = 16f

    // ---------------------------------------------------------------------------------------
    // Registries. Two face-level decisions, each written once.
    // ---------------------------------------------------------------------------------------

    /**
     * Faces that already represent the LEFT and RIGHT quadrants inside their own composition.
     *
     * Expressive and Material flank their centre control with prev/next buttons that *are* those
     * quadrants: the buttons carry the configured action's icon
     * (`NowPlayingFaceState.leftActionIcon`) and their taps run the quadrant's action
     * (`onSkipPreviousTap` → `executeAction(ScreenQuadrant.LEFT)`). Drawing bezel hints for the
     * same two actions on those faces would show each of them twice.
     *
     * Same shape and same reasoning as [MiniButtonPlacement.isHostedByFace]: a face may host a
     * piece of shared chrome inside its composition, and when it does the shared copy stands down.
     */
    fun hostsSideAffordance(face: String?): Boolean = face?.trim() in SIDE_AFFORDANCE_FACES

    private val SIDE_AFFORDANCE_FACES = setOf("expressive", "material")

    /**
     * Faces that let the mini-button row draw over them rather than lifting out of its way.
     *
     * Immersive is the one, and it is a decision its own source already records: its grounded
     * title/artist/time block *is* the layout, and lifting it to clear the mini buttons "made the
     * text float mid-screen and broke the composition the face exists for". So the row draws over
     * it, on purpose.
     *
     * This covers the row and the pill only - **not** the bottom hint, which such a face still
     * clears. The two are not the same size or the same kind of thing: the row is a band of opaque
     * pills the user put there and can move, while the hint is a 24dp glyph that would land on the
     * artist line with nothing about it to explain why. The recorded reasoning was about the row.
     *
     * Declining is listed here rather than expressed as a face quietly not reading [SafeArea],
     * because those two look identical in the source and only one of them is intentional.
     */
    fun declinesLowerContentBand(face: String?): Boolean =
            face?.trim() in LOWER_CONTENT_DECLINING_FACES

    /**
     * Immersive and Split, for the same reason stated two different ways.
     *
     * Immersive's grounded block *is* its layout. Split's panel runs from the seam to the glass and
     * its text is top-anchored inside it, so there is nowhere for that text to retreat to: lifting
     * it would push it into the seam, which is the face. Both are already in
     * `FaceScopedPreferences.SELF_COMPOSED_FACES`, which turns the mini-button row off by default
     * precisely because these compositions reach the screen edge - a user who turns it back on is
     * choosing the overlay.
     */
    private val LOWER_CONTENT_DECLINING_FACES = setOf("immersive", "split")

    // ---------------------------------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------------------------------

    /**
     * What is on screen in the bottom band: the mini-button row, or the Up Next pill that takes
     * the same band when no row is configured.
     *
     * One type for both, because the band does not care which of them is in it. They do place
     * themselves differently, and that difference is [designedMarginDp] rather than two code paths:
     * what they must not differ on is clearing the bottom hint, which neither of them used to do.
     *
     * [followsBezel] is false for an arrangement that leaves the band empty: a side rail is pinned
     * to a wall and a face-hosted row is placed by the composition, so neither reserves anything
     * here.
     */
    data class LowerContent(
            val widthDp: Float,
            val heightDp: Float,
            val followsBezel: Boolean = true,
            /**
             * A distance from the glass this content places itself at, instead of following the
             * chord.
             *
             * The row has none: it is narrow enough that the chord is what decides how low it can
             * rest, which is why a one-button row used to sit 9.6dp from the glass. The pill has
             * one, because it is nearly screen-wide with fully rounded ends - the chord would drop
             * it a third of the way up the face to buy clearance its own silhouette does not need.
             * Either way it is still raised to clear the bottom hint, which is the part neither of
             * them used to do.
             */
            val designedMarginDp: Float? = null
    ) {
        companion object {
            /** The row, sized as measured, reserving nothing when its placement leaves the band. */
            fun row(
                    widthDp: Float,
                    heightDp: Float,
                    placement: MiniButtonPlacement,
                    hostedByFace: Boolean
            ): LowerContent = LowerContent(widthDp, heightDp,
                    followsBezel = !placement.isRail && !hostedByFace)

            /** The Up Next pill, from the one place its shape is written down. */
            fun upNextPill(screenDp: Float): LowerContent = LowerContent(
                    widthDp = screenDp * FaceGeometry.UpNextPill.WIDTH_FRACTION,
                    heightDp = FaceGeometry.UpNextPill.heightDp(screenDp),
                    designedMarginDp = screenDp * FaceGeometry.UpNextPill.BOTTOM_MARGIN_FRACTION)
        }
    }

    /**
     * Every discrete fact the host knows, and nothing it has to measure twice.
     *
     * [configuredQuadrants] is what the *user* assigned, not what ends up drawn: whether a hint is
     * painted also depends on [hintsVisible] and on [hostsSideAffordance], and those are decisions
     * this object owns. A caller that pre-filtered would be re-implementing half the rule.
     */
    data class Inputs(
            val screenDp: Float,
            val round: Boolean,
            val face: String,
            val configuredQuadrants: Set<Int> = emptySet(),
            /** False when *Show player controls* is off, or the control style zeroes the icons. */
            val hintsVisible: Boolean = true,
            /** `ScreenThemeTokens.iconScale`. */
            val hintScale: Float = 1f,
            val clockVisible: Boolean = false,
            /** The clock's resolved text size in dp, with the user's typography already applied. */
            val clockTextDp: Float = FaceGeometry.Classic.CLOCK_SP,
            val lowerContent: LowerContent? = null
    )

    // ---------------------------------------------------------------------------------------
    // Outputs
    // ---------------------------------------------------------------------------------------

    /** One hint, centred on its cardinal point. Fractions are of the screen's own width/height. */
    data class HintPlacement(
            val quadrant: Int,
            val sizeDp: Float,
            val centerXFraction: Float,
            val centerYFraction: Float
    )

    /**
     * How the clock is drawn on the player.
     *
     * [CURVED_ARC] is not a new widget: `CurvedClock` already exists in the watch's shared Compose
     * chrome and is what the queue and menu screens use. The player faces chose the straight one so
     * that switching faces never moved the clock, which is still true - what moves it now is the
     * top hint arriving, and only for the people who have a top quadrant action configured and the
     * clock on, i.e. exactly the people whose hint was being deleted to make room.
     */
    enum class ClockStyle { HIDDEN, STRAIGHT_APEX, CURVED_ARC }

    /**
     * What the face must keep clear, in dp from each edge.
     *
     * [sideDp] is one value for both sides even when only one of them carries a hint. An
     * asymmetric inset would push every centred composition off the vertical axis to buy back a
     * few dp, which is a worse trade than leaving one side's reserve unused.
     */
    data class SafeArea(
            val topDp: Float,
            val bottomDp: Float,
            val sideDp: Float,
            /**
             * Where the band's own content rests, in dp from the glass.
             *
             * The one number here that is not a keep-out, and it is for the caller that *draws* the
             * band rather than avoids it: the Up Next pill is composed by the faces, so it needs
             * the resting line, not the reserve. Reading [bottomDp] there would have the pill push
             * itself up by its own height.
             */
            val lowerContentMarginDp: Float = 0f
    )

    data class Chrome(
            val hints: List<HintPlacement>,
            val clock: ClockStyle,
            /** How deep the clock's own band runs from the glass; 0 when it is hidden. */
            val clockBandDp: Float,
            /**
             * Where the bottom of [Inputs.lowerContent] rests, measured from the glass - and with
             * no lower content, the line one would rest on, which is the bottom hint's own reserve.
             */
            val lowerMarginDp: Float,
            val safeArea: SafeArea
    ) {
        fun hint(quadrant: Int): HintPlacement? = hints.firstOrNull { it.quadrant == quadrant }
    }

    // ---------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------

    /**
     * How far from the glass something of a given half-width has to sit before it fits.
     *
     * The inverse of [RoundScreenText.halfChordAt], in the same units: [halfWidthFraction] and the
     * result are both fractions of the screen. At the very edge the chord is nothing, so anything
     * with real width has to come inwards, and this says by how much.
     *
     * It is the only circle equation in this file. The mini row's resting line is this, and so is
     * the floor under every hint's inset - which is what keeps a scaled-up icon on a small watch
     * from having its outer corners shaved by the bezel, without a second piece of trigonometry
     * that could disagree with the first.
     */
    fun bezelInsetFraction(halfWidthFraction: Float): Float {
        val half = halfWidthFraction.coerceIn(0f, .5f)
        val squared = .25f - half * half
        if (squared <= 0f) return .5f
        return .5f - sqrt(squared.toDouble()).toFloat()
    }

    /** [bezelInsetFraction] in dp, for something [widthDp] wide on a [screenDp] screen. */
    fun bezelInsetDp(screenDp: Float, widthDp: Float): Float {
        if (screenDp <= 0f) return 0f
        return screenDp * bezelInsetFraction(widthDp / (2f * screenDp))
    }

    // ---------------------------------------------------------------------------------------
    // The curved clock's arc
    // ---------------------------------------------------------------------------------------

    /**
     * Where the curved clock's text baseline runs, in dp from the centre of the screen.
     *
     * The glyphs sit *outside* the baseline - the outward normal of a top arc read left to right
     * is up - so the caller passes the font's ascent and gets back a baseline that puts the top of
     * the text [CURVED_CLOCK_EDGE_INSET_DP] from the glass. Taking the ascent rather than the text
     * size is what keeps a tall face and a short one at the same distance from the bezel instead of
     * at the same distance from their own baselines.
     */
    fun curvedClockBaselineRadiusDp(screenDp: Float, textAscentDp: Float): Float =
            (screenDp / 2f - CURVED_CLOCK_EDGE_INSET_DP - textAscentDp.coerceAtLeast(0f))
                    .coerceAtLeast(1f)

    /** The angle a run of text [textWidthDp] wide subtends at [baselineRadiusDp]. */
    fun curvedClockSweepDegrees(textWidthDp: Float, baselineRadiusDp: Float): Float {
        if (baselineRadiusDp <= 0f) return 0f
        val radians = textWidthDp.coerceAtLeast(0f) / baselineRadiusDp
        return Math.toDegrees(radians.toDouble()).toFloat().coerceAtMost(360f)
    }

    /**
     * Where that arc starts, in the platform's own convention: degrees clockwise from 3 o'clock,
     * which puts 12 o'clock at 270.
     *
     * The run is centred on the apex, so the clock stays symmetric about the vertical axis however
     * long the time string is - "9:05" and "23:45" grow outwards from the same centre rather than
     * from a fixed left edge.
     */
    fun curvedClockStartAngleDegrees(sweepDegrees: Float): Float = 270f - sweepDegrees / 2f

    // ---------------------------------------------------------------------------------------
    // The resolver
    // ---------------------------------------------------------------------------------------

    fun resolve(inputs: Inputs): Chrome {
        val screen = inputs.screenDp.coerceAtLeast(1f)
        val drawn = drawnQuadrants(inputs)
        val hintSize = (HINT_SIZE_DP * inputs.hintScale).coerceAtLeast(0f)

        val clock = clockStyle(inputs, drawn)
        val clockBand = clockBandDp(clock, inputs.clockTextDp)

        // The floor every hint shares: at the glass the chord is zero, so a hint's own width
        // decides the shallowest inset that still holds it. Normally a no-op against the designed
        // 4dp, and the thing that saves a 1.08-scaled icon on a 160dp watch.
        val edgeFloor = if (inputs.round) bezelInsetDp(screen, hintSize) else 0f
        val edgeInset = maxOf(HINT_EDGE_INSET_DP, edgeFloor)

        // The apex belongs to the hint, and a clock sharing the top gets out of its way: on a round
        // screen by curving out to the arc, on a square one by simply being above it. Either way
        // the hint starts below the clock's own band. With no clock - or no top hint, in which case
        // the clock keeps the apex undisturbed - this is the designed inset.
        val topInset = maxOf(
                if (clock != ClockStyle.HIDDEN && ScreenQuadrant.TOP in drawn) {
                    clockBand + CHROME_GAP_DP
                } else {
                    HINT_EDGE_INSET_DP
                },
                edgeFloor)

        val hints = drawn.sorted().map { quadrant ->
            when (quadrant) {
                ScreenQuadrant.TOP -> HintPlacement(quadrant, hintSize,
                        centerXFraction = .5f,
                        centerYFraction = (topInset + hintSize / 2f) / screen)
                ScreenQuadrant.BOTTOM -> HintPlacement(quadrant, hintSize,
                        centerXFraction = .5f,
                        centerYFraction = 1f - (edgeInset + hintSize / 2f) / screen)
                ScreenQuadrant.LEFT -> HintPlacement(quadrant, hintSize,
                        centerXFraction = (edgeInset + hintSize / 2f) / screen,
                        centerYFraction = .5f)
                else -> HintPlacement(quadrant, hintSize,
                        centerXFraction = 1f - (edgeInset + hintSize / 2f) / screen,
                        centerYFraction = .5f)
            }
        }

        // Everything the bottom hint occupies, plus its gap - the line the lower content rests on
        // when the bezel would otherwise let it sit lower. A narrow row is what made this matter:
        // its chord margin shrinks with its width, so one configured button rested 9.6dp from the
        // glass on a 192dp screen, which is inside the hint.
        val bottomHintReserve =
                if (ScreenQuadrant.BOTTOM in drawn) edgeInset + hintSize + CHROME_GAP_DP else 0f

        val lower = inputs.lowerContent?.takeIf { it.followsBezel }
        val lowerMargin = when {
            lower == null -> bottomHintReserve
            lower.designedMarginDp != null -> maxOf(lower.designedMarginDp, bottomHintReserve)
            inputs.round -> maxOf(bezelInsetDp(screen, lower.widthDp) + CHROME_GAP_DP,
                    bottomHintReserve)
            else -> maxOf(SQUARE_LOWER_MARGIN_DP, bottomHintReserve)
        }

        val bottomDp = when {
            lower == null || declinesLowerContentBand(inputs.face) -> bottomHintReserve
            else -> lowerMargin + lower.heightDp + CHROME_GAP_DP
        }

        val topDp = when {
            ScreenQuadrant.TOP in drawn -> topInset + hintSize + CHROME_GAP_DP
            clock == ClockStyle.STRAIGHT_APEX -> clockBand + CHROME_GAP_DP
            else -> 0f
        }

        val sideDp = if (drawn.any { it == ScreenQuadrant.LEFT || it == ScreenQuadrant.RIGHT }) {
            edgeInset + hintSize + CHROME_GAP_DP
        } else {
            0f
        }

        return Chrome(
                hints = hints,
                clock = clock,
                clockBandDp = clockBand,
                lowerMarginDp = lowerMargin,
                safeArea = SafeArea(topDp = topDp, bottomDp = bottomDp, sideDp = sideDp,
                        lowerContentMarginDp = lowerMargin))
    }

    /** Which hints are actually painted, after the visibility gate and the side-affordance rule. */
    fun drawnQuadrants(inputs: Inputs): Set<Int> {
        if (!inputs.hintsVisible) return emptySet()
        val configured = inputs.configuredQuadrants
        if (!hostsSideAffordance(inputs.face)) return configured
        return configured - setOf(ScreenQuadrant.LEFT, ScreenQuadrant.RIGHT)
    }

    private fun clockStyle(inputs: Inputs, drawn: Set<Int>): ClockStyle = when {
        !inputs.clockVisible -> ClockStyle.HIDDEN
        // Only a round screen has an arc to curve along; a square one keeps the straight clock and
        // stacks the hint under it, which its corners leave room for.
        ScreenQuadrant.TOP in drawn && inputs.round -> ClockStyle.CURVED_ARC
        else -> ClockStyle.STRAIGHT_APEX
    }

    private fun clockBandDp(style: ClockStyle, clockTextDp: Float): Float {
        val text = clockTextDp.coerceAtLeast(0f) * CLOCK_LEADING
        return when (style) {
            ClockStyle.HIDDEN -> 0f
            ClockStyle.STRAIGHT_APEX -> FaceGeometry.Classic.CLOCK_TOP_PADDING_DP + text
            ClockStyle.CURVED_ARC -> CURVED_CLOCK_EDGE_INSET_DP + text
        }
    }
}
