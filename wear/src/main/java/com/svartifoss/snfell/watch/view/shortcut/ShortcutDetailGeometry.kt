package com.svartifoss.snfell.watch.view.shortcut

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Where the shortcut screen puts things, as fractions of the screen, so a 192dp watch and a 233dp
 * one share one composition and the buttons always sit above the narrowing bottom of the circle.
 * Plain `Float` dp in and out - no Compose - so the placements that matter on a round display
 * (nothing clipped by the bezel, nothing overlapping) are pinned by a JVM test rather than found on
 * a watch of the wrong size.
 *
 * The fractions are measured from the music apps' own playlist page on a 384px round watch (192dp):
 * the title's top at 19.5% of the screen, the round buttons' top at 43.5% and 27% across.
 *
 * It also holds the backdrop's two masks, as data, because two things read them: the screen that
 * draws them, and [ShortcutGlowMath.groundColor], which works out what colour the backdrop is
 * under the service line. Kept in two places, the text would be chosen against a backdrop the
 * screen does not draw.
 */
internal object ShortcutDetailGeometry {

    const val TITLE_TOP = .195f
    const val BUTTONS_TOP = .435f
    private const val BUTTON_SIZE = .27f
    private const val BUTTON_GAP = .0625f

    private const val SIDE_INSET = .10f

    private const val MIN_BUTTON_DP = 48f
    private const val MAX_BUTTON_DP = 64f

    /** The "Open on phone" pill: a fixed touch height, below the buttons by a fraction of the
     *  screen. */
    const val PILL_HEIGHT_DP = 40f
    private const val PILL_GAP = .045f

    /** Room left between the pill and the edge of the glass. */
    private const val GLASS_MARGIN_DP = 3f

    /** Narrower than this a translated label would be cut to nothing, so a small screen keeps
     *  this width and shows the pill whole once scrolled, as Wear lists do. */
    const val MIN_PILL_WIDTH_DP = 120f

    /**
     * The black laid over the cover, from the middle of the screen out: (distance from the centre
     * as a fraction of [RADIAL_MASK_RADIUS], opacity). It keeps the sides and corners down, so the
     * glow is a light in the middle of the glass rather than a picture behind it.
     */
    val RADIAL_MASK: List<Pair<Float, Float>> = listOf(0f to 0f, .42f to .05f, .74f to .60f, 1f to 1f)

    /** The radial mask's reach, as a fraction of the screen's width. */
    const val RADIAL_MASK_RADIUS = .64f

    /**
     * The black laid over the cover from top to bottom: (height as a fraction of the screen,
     * opacity). It takes the top edge down for the clock, leaves the cover as it is between the
     * name and the buttons, and closes it gradually behind them - a long ramp, because a short one
     * draws a horizon straight through whatever the cover has in its middle - so the lower part,
     * where "Open on phone" sits, is black.
     */
    val VERTICAL_MASK: List<Pair<Float, Float>> = listOf(
            0f to .55f, .15f to .08f, .24f to 0f, .48f to 0f, .60f to .30f, .70f to .68f,
            .82f to 1f, 1f to 1f)

    fun titleTop(screen: Float): Float = screen * TITLE_TOP

    fun sideInset(screen: Float): Float = screen * SIDE_INSET

    /** The round buttons' diameter: proportional, but never below a comfortable touch target nor so
     *  large the pair stops fitting under a long title. */
    fun buttonSize(screen: Float): Float = (screen * BUTTON_SIZE).coerceIn(MIN_BUTTON_DP, MAX_BUTTON_DP)

    fun buttonGap(screen: Float): Float = screen * BUTTON_GAP

    fun buttonsTop(screen: Float): Float = screen * BUTTONS_TOP

    /**
     * The least height the title block takes, so the buttons rest at [BUTTONS_TOP] whether the name
     * has a service line under it or not. A name that wraps to a second line is taller than this and
     * pushes the buttons down, which is the right answer to a long name.
     */
    fun titleBlockMinHeight(screen: Float, itemSpacing: Float): Float =
            (buttonsTop(screen) - titleTop(screen) - itemSpacing).coerceAtLeast(0f)

    /** Where the pill's top edge rests, with a one-line name. */
    fun pillTop(screen: Float): Float = buttonsTop(screen) + buttonSize(screen) + screen * PILL_GAP

    /** The space to put above the pill on top of the list's own item spacing. */
    fun pillTopGap(screen: Float, itemSpacing: Float): Float =
            (screen * PILL_GAP - itemSpacing).coerceAtLeast(0f)

    /**
     * The widest the pill may be: as wide as a capsule of [PILL_HEIGHT_DP] can be at [pillTop] and
     * still sit entirely on the round glass - its rounded ends are the part that would leave it
     * first - and never narrower than [MIN_PILL_WIDTH_DP].
     */
    fun pillMaxWidth(screen: Float): Float {
        val radius = screen / 2f
        val endRadius = PILL_HEIGHT_DP / 2f
        val centreOffset = abs(pillTop(screen) + endRadius - radius)
        val reach = radius - GLASS_MARGIN_DP - endRadius
        val halfStraight = if (reach > centreOffset) {
            sqrt(reach * reach - centreOffset * centreOffset)
        } else {
            0f
        }
        return max(2f * (halfStraight + endRadius), MIN_PILL_WIDTH_DP)
    }

    /** The opacity of a mask given as (position, opacity) stops at [position], linear between
     *  stops and flat beyond the ends - what the gradient brushes drawing the masks do. */
    fun maskAt(stops: List<Pair<Float, Float>>, position: Float): Float {
        if (position <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (end, endValue) = stops[i]
            if (position <= end) {
                val (start, startValue) = stops[i - 1]
                val span = end - start
                return if (span <= 0f) endValue else
                    startValue + (endValue - startValue) * (position - start) / span
            }
        }
        return stops.last().second
    }

    /** Half the width of a round screen's chord [y] below its top edge - how far either side of the
     *  centre line anything may reach at that height and still be on the glass. */
    fun chordHalfWidth(screen: Float, y: Float): Float {
        val radius = screen / 2f
        val dy = (y - radius).coerceIn(-radius, radius)
        return sqrt(radius * radius - dy * dy)
    }
}
