package com.svartifoss.snfell.common

import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/** How a queue row is filled. Transparent is a [Solid] of `0`. */
sealed interface QueueRowFill {
    data class Solid(val color: Int) : QueueRowFill

    /** [colors] spread evenly along [direction]. */
    data class Gradient(val direction: Direction, val colors: List<Int>) : QueueRowFill

    enum class Direction {
        /** Top to bottom. */
        VERTICAL,
        /** Left to right. */
        HORIZONTAL,
        /** Top-left to bottom-right, Compose's default `linearGradient`. */
        DIAGONAL,
        /** Round the row's centre, starting at three o'clock. */
        SWEEP
    }
}

/** A stroke [widthDp] wide in [color]. */
data class QueueRowStroke(val widthDp: Float, val color: Int)

/** The bar down the row's left side that marks the playing entry (and Rail's persistent spine). */
data class QueueRowKeyline(
        val color: Int,
        val widthDp: Float = 3f,
        /** How far in from the top and the bottom of the row the bar stops, as a share of it. */
        val insetFraction: Float = .18f)

/**
 * Everything one queue row is painted with, apart from its outline and its cover.
 *
 * Plain colours rather than Compose or Canvas types, so the watch and the phone's miniature paint
 * from the same answer: the miniature used to keep a second, hand-copied table of every style, and
 * by the time the two were compared it disagreed with the watch on Slab, Ink, Rail, Sunset and
 * Chrome - the screen built to show what a style looks like was showing something else.
 */
data class QueueRowSkin(
        val fill: QueueRowFill,
        /** Text and icons on the row. */
        val onColor: Int,
        /** A stroke around the outline. */
        val border: QueueRowStroke? = null,
        val keyline: QueueRowKeyline? = null,
        /** A stroke along the foot of the row, independent of [border]. */
        val underline: QueueRowStroke? = null,
        /** Layers laid along the foot of the row, lowest first (Strata). */
        val strata: List<Int>? = null)

/**
 * The colours the Route style draws its line and its stations with.
 *
 * The line is [travelled] above the playing entry and [ahead] below it, so the queue reads as a
 * route with a "you are here" on it; [lit] is the playing station.
 */
data class QueueRouteColors(
        val lit: Int,
        val travelled: Int,
        val ahead: Int,
        /** The soft glow around the playing station. */
        val halo: Int,
        /** A station that already played. */
        val past: Int,
        /** The open ring of a station still to come. */
        val upcomingRing: Int,
        /** The dark core inside it, which keeps the ring legible where the line passes behind. */
        val upcomingCore: Int)

/**
 * The colours of every queue style, from the playing album's three colours.
 *
 * The finish is what a style *is* now that the outline and the cover cut are chosen separately
 * (see [QueueAppearance]): which surface, which gradient, which stroke, how the playing entry is
 * set apart. Shared by the watch's queue and the phone's miniature of it. Not JVM-testable, since
 * the colour arithmetic goes through `ColorUtils`; [QueueStyles] holds the half that is.
 */
object QueueRowSkins {

    private const val IDLE_SURFACE = 0xFF1E1E20.toInt()
    private const val MATERIAL_SURFACE = 0xFF2A2A2A.toInt()
    private const val LIGHT_SURFACE = 0xFFECECEC.toInt()
    private const val LIGHT_ON = 0xFF111111.toInt()
    private const val MONO_IDLE = 0xFF262626.toInt()
    private const val MONO_ACTIVE = 0xFFE0E0E0.toInt()
    private const val SLAB_SURFACE = 0xFF1E1E20.toInt()
    private const val TERMINAL_GREEN = 0xFF33FF66.toInt()
    private const val BASALT_STONE_TOP = 0xFF2C2F33.toInt()
    private const val BASALT_STONE_BOTTOM = 0xFF212326.toInt()
    private const val STRATA_GROUND = 0xFF1D1E21.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val TRANSPARENT = 0

    /**
     * The skin of one row of style [token], for the playing entry or any other ([isPlaying]),
     * from the queue's resolved [accent] and its two companions.
     */
    fun resolve(
            token: String?,
            isPlaying: Boolean,
            accent: Int,
            secondary: Int,
            tertiary: Int
    ): QueueRowSkin {
        val lit = PaletteTransforms.surfaceAccent(accent)
        val style = QueueStyles.spec(token)
        if (style.fillsWithCover) {
            // The cover itself is painted by the row, under a scrim (see coverScrim); this is the
            // fill that shows where an entry has no artwork, matching Glass so an art-less row is
            // indistinguishable from the plain style. The playing row carries no mark of its own:
            // the equalizer bars say it, and both a straight bar (crooked against a rounded end)
            // and a ring (noise over the cover) were tried and rejected.
            return QueueRowSkin(
                    fill = QueueRowFill.Solid(IDLE_SURFACE),
                    onColor = WHITE)
        }
        val onLit = if (isPlaying) BLACK else WHITE
        return when (style.token) {
            "minimal" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(TRANSPARENT),
                    onColor = if (isPlaying) accent else WHITE,
                    keyline = if (isPlaying) QueueRowKeyline(accent) else null)
            "material" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(MATERIAL_SURFACE),
                    onColor = if (isPlaying) accent else WHITE,
                    keyline = if (isPlaying) QueueRowKeyline(accent) else null)
            "tonal" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) lit else tonal(accent, .22f)),
                    onColor = onLit)
            "neon" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(TRANSPARENT),
                    onColor = if (isPlaying) accent else WHITE,
                    border = if (isPlaying) {
                        QueueRowStroke(2f, accent)
                    } else {
                        QueueRowStroke(1f, withAlpha(accent, .5f))
                    })
            "light" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) lit else LIGHT_SURFACE),
                    onColor = if (isPlaying) BLACK else LIGHT_ON)
            "gradient" -> QueueRowSkin(
                    fill = vertical(
                            if (isPlaying) {
                                listOf(lit, tonal(secondary, .55f))
                            } else {
                                listOf(tonal(accent, .26f), tonal(secondary, .13f))
                            }),
                    onColor = onLit)
            "mono" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) MONO_ACTIVE else MONO_IDLE),
                    onColor = onLit)
            "outline" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(TRANSPARENT),
                    onColor = if (isPlaying) accent else WHITE,
                    border = if (isPlaying) {
                        QueueRowStroke(3f, accent)
                    } else {
                        QueueRowStroke(2.5f, WHITE)
                    })
            "duotone" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) lit else tonal(secondary, .24f)),
                    onColor = onLit)
            "prism" -> QueueRowSkin(
                    fill = QueueRowFill.Gradient(
                            QueueRowFill.Direction.DIAGONAL,
                            if (isPlaying) {
                                listOf(tonal(tertiary, .52f), lit, tonal(secondary, .46f))
                            } else {
                                listOf(
                                        tonal(tertiary, .18f),
                                        tonal(accent, .28f),
                                        tonal(secondary, .14f))
                            }),
                    onColor = onLit,
                    border = QueueRowStroke(1f, withAlpha(WHITE, .38f)))
            "contrast" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) WHITE else BLACK),
                    onColor = onLit,
                    border = if (isPlaying) null else QueueRowStroke(2f, WHITE))
            "terminal" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(TRANSPARENT),
                    onColor = TERMINAL_GREEN,
                    border = QueueRowStroke(if (isPlaying) 2f else 1f, TERMINAL_GREEN))
            "frost" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(
                            if (isPlaying) withAlpha(accent, .5f) else withAlpha(WHITE, .16f)),
                    onColor = WHITE)
            "soft" -> QueueRowSkin(
                    fill = horizontal(
                            if (isPlaying) {
                                listOf(lit, PaletteTransforms.surfaceAccent(secondary))
                            } else {
                                listOf(tonal(accent, .24f), tonal(secondary, .18f))
                            }),
                    onColor = onLit,
                    border = QueueRowStroke(1f, withAlpha(WHITE, if (isPlaying) .42f else .16f)))
            "slab" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(
                            if (isPlaying) tonal(secondary, .34f) else SLAB_SURFACE),
                    onColor = WHITE,
                    keyline = if (isPlaying) QueueRowKeyline(lit, 4f, .12f) else null)
            "ink" -> QueueRowSkin(
                    fill = horizontal(listOf(
                            withAlpha(WHITE, .045f),
                            withAlpha(tonal(accent, .14f), .72f),
                            TRANSPARENT)),
                    onColor = if (isPlaying) lit else WHITE,
                    underline = if (isPlaying) {
                        QueueRowStroke(3f, lit)
                    } else {
                        QueueRowStroke(1f, withAlpha(secondary, .58f))
                    })
            "rail" -> QueueRowSkin(
                    fill = horizontal(listOf(
                            tonal(accent, if (isPlaying) .32f else .18f),
                            tonal(secondary, if (isPlaying) .18f else .10f))),
                    onColor = WHITE,
                    keyline = QueueRowKeyline(
                            if (isPlaying) lit else withAlpha(tertiary, .68f),
                            if (isPlaying) 5f else 2.5f,
                            .07f))
            "sunset" -> QueueRowSkin(
                    fill = vertical(
                            if (isPlaying) {
                                listOf(
                                        PaletteTransforms.surfaceAccent(tertiary),
                                        lit,
                                        PaletteTransforms.surfaceAccent(secondary))
                            } else {
                                listOf(
                                        tonal(tertiary, .32f),
                                        tonal(accent, .22f),
                                        tonal(secondary, .14f))
                            }),
                    onColor = onLit)
            "bubble" -> QueueRowSkin(
                    fill = if (isPlaying) {
                        QueueRowFill.Solid(lit)
                    } else {
                        horizontal(listOf(tonal(secondary, .30f), tonal(accent, .18f)))
                    },
                    onColor = onLit,
                    border = QueueRowStroke(
                            1f,
                            if (isPlaying) withAlpha(tertiary, .50f) else withAlpha(accent, .28f)))
            "chrome" -> QueueRowSkin(
                    fill = vertical(
                            if (isPlaying) {
                                listOf(
                                        chromeTone(tertiary, .88f),
                                        chromeTone(accent, .58f),
                                        chromeTone(secondary, .78f))
                            } else {
                                listOf(
                                        chromeTone(tertiary, .38f),
                                        chromeTone(accent, .10f),
                                        chromeTone(secondary, .30f))
                            }),
                    onColor = onLit,
                    border = QueueRowStroke(1f, withAlpha(WHITE, if (isPlaying) .70f else .30f)))
            "holo" -> QueueRowSkin(
                    fill = QueueRowFill.Gradient(
                            QueueRowFill.Direction.SWEEP,
                            if (isPlaying) {
                                listOf(
                                        lit,
                                        PaletteTransforms.surfaceAccent(secondary),
                                        PaletteTransforms.surfaceAccent(tertiary),
                                        lit)
                            } else {
                                listOf(
                                        tonal(accent, .20f),
                                        tonal(secondary, .26f),
                                        tonal(tertiary, .16f),
                                        tonal(accent, .20f))
                            }),
                    onColor = onLit,
                    border = QueueRowStroke(1f, withAlpha(WHITE, if (isPlaying) .60f else .28f)))
            // Slate cut as a column; the playing entry is cut from the lightened accent instead.
            // The outline carries the idea on its own - seams drawn across the row ran straight
            // through the title and artist.
            "basalt" -> QueueRowSkin(
                    fill = if (isPlaying) {
                        QueueRowFill.Solid(lit)
                    } else {
                        vertical(listOf(BASALT_STONE_TOP, BASALT_STONE_BOTTOM))
                    },
                    onColor = onLit)
            // The palette's three colours laid down as layers, brightest on top. Idle rows keep
            // them at half strength so a list of them reads as sediment, not as a rainbow.
            "strata" -> QueueRowSkin(
                    fill = if (isPlaying) {
                        vertical(listOf(tonal(accent, .30f), tonal(accent, .17f)))
                    } else {
                        QueueRowFill.Solid(STRATA_GROUND)
                    },
                    onColor = WHITE,
                    strata = listOf(tertiary, secondary, accent).map {
                        if (isPlaying) it else withAlpha(it, .5f)
                    })
            // The rows are stops on the route, not cards: nothing behind them but the line, and
            // the playing one held in a soft capsule of the album's colour - the "you are here".
            // Dark rather than lit, so the lit station inside it stays the brightest thing.
            "route" -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) tonal(accent, .22f) else TRANSPARENT),
                    onColor = WHITE,
                    border = if (isPlaying) QueueRowStroke(1f, withAlpha(lit, .55f)) else null)
            else -> QueueRowSkin(
                    fill = QueueRowFill.Solid(if (isPlaying) lit else IDLE_SURFACE),
                    onColor = onLit)
        }
    }

    /**
     * The legibility scrim over a cover-filled row, as horizontal (position, colour) stops.
     *
     * Album art is arbitrary - white, busy, or the same hue as the text - so the title needs a
     * guaranteed floor of contrast. Heaviest on the left, where the title sits, so the right of the
     * artwork stays visible the way the Wear OS media template shows it. Cover Tonal washes in a
     * darkened album accent instead of black; Cover Blur's backdrop is already soft, so it needs
     * far less darkening and keeps more of the colour.
     */
    fun coverScrim(token: String?, accent: Int): List<Pair<Float, Int>> = when (token) {
        "cover_blur" -> listOf(0f to withAlpha(BLACK, .50f), 1f to withAlpha(BLACK, .28f))
        "cover_tonal" -> neutralScrim(darkenForScrim(accent))
        else -> neutralScrim(BLACK)
    }

    /** [coverScrim]'s neutral form, in [tint]. */
    fun neutralScrim(tint: Int): List<Pair<Float, Int>> = listOf(
            0f to withAlpha(tint, .74f),
            .55f to withAlpha(tint, .46f),
            1f to withAlpha(tint, .22f))

    /** The Route style's line and stations, from the queue's [accent]. */
    fun routeColors(accent: Int): QueueRouteColors {
        val lit = PaletteTransforms.surfaceAccent(accent)
        return QueueRouteColors(
                lit = lit,
                travelled = withAlpha(lit, .55f),
                ahead = withAlpha(WHITE, .24f),
                halo = withAlpha(lit, .28f),
                past = withAlpha(lit, .7f),
                upcomingRing = withAlpha(WHITE, .75f),
                upcomingCore = withAlpha(BLACK, .85f))
    }

    /** A dark, accent-tinted surface - the queue's tonal idle rows. */
    private fun tonal(color: Int, lightness: Float): Int =
            PaletteTransforms.tonalSurface(color, lightness)

    /** One real album colour turned into a low-saturation metallic stop, keeping its hue. */
    private fun chromeTone(color: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtMost(.10f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** Pulls an accent down to a dark, low-saturation tone usable as a scrim - a bright accent at
     *  70% alpha would tint the artwork without actually darkening it. */
    private fun darkenForScrim(accent: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        hsl[1] = (hsl[1] * .8f).coerceIn(0f, .65f)
        hsl[2] = .16f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun vertical(colors: List<Int>) =
            QueueRowFill.Gradient(QueueRowFill.Direction.VERTICAL, colors)

    private fun horizontal(colors: List<Int>) =
            QueueRowFill.Gradient(QueueRowFill.Direction.HORIZONTAL, colors)

    /** [color] at [alpha], replacing whatever alpha it had - Compose's `Color.copy(alpha)`. */
    fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)
}
