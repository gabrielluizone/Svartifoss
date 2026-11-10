package com.svartifoss.snfell.common

import kotlin.math.floor

/**
 * How many lines a face's title may wrap to before its text block reaches the face's own controls.
 *
 * The title modes and the size control are the user's, and on most faces the room for them was never
 * stated anywhere: the block simply grew. "Wrap to 5 lines", a long title in the default mode on a
 * tight face, or the title at 160% therefore ran the text into whatever the face draws beneath it -
 * Poster's play mark, Studio's progress orb, the disc and transport row on Material - and the
 * collision is only ever seen by somebody trying out a theme. This is the band each of those faces
 * actually has, so the title stops at an ellipsis instead of running into the controls.
 *
 * The ceiling is a *line count* rather than a height so the phone's miniature can honour exactly the
 * same limit through its own title planner; the watch additionally caps the block's height, so a
 * typeface taller than the reference metrics still cannot push the artist into the controls.
 *
 * Fractions are of the screen's height, measured from the top, on the composition each face draws
 * while its block follows the face (no position chosen); [movedBand] covers a block moved to the top
 * or the bottom.
 */
object TitleBand {

    /**
     * The most title lines a band [bandDp] tall holds once an artist row of [artistRowDp] has its
     * share, at the user's [titleScale] and [artistScale]. Never fewer than one: a title is never
     * dropped, and [fitScale] is what keeps that one line from being cut by its block.
     */
    fun lineCeiling(
            bandDp: Float,
            titleLineDp: Float,
            titleScale: Float = 1f,
            artistRowDp: Float = 0f,
            artistScale: Float = 1f
    ): Int {
        val line = titleLineDp * titleScale
        if (line <= 0f) return 1
        val room = bandDp - artistRowDp * artistScale
        return floor(room / line).toInt().coerceAtLeast(1)
    }

    /**
     * How much a face's title and artist must both shrink for one title line and the artist row to
     * fit a band [bandDp] tall: 1 while they fit at the user's [titleScale] and [artistScale], less
     * once those sizes make them taller than the band. One factor for both keeps the proportion the
     * user chose between them, and nothing is hidden or cut.
     *
     * [lineCeiling] cannot answer this: it counts the title lines that fit, and when not even one
     * did, the title was still handed a line it had no room for and its block clipped it - from
     * about 115% on Expressive and Material the foot of the title was cut, and at 140% only its top
     * half showed.
     *
     * Anchored at the face's design: a band shorter than the 100% title line and artist row counts
     * as that tall (by the reference metrics Expressive's and Material's are, while the glyphs
     * themselves fit), so the default sizes never shrink - the text stops growing past them. Pass 0
     * for an element that is not drawn.
     */
    fun fitScale(
            bandDp: Float,
            titleLineDp: Float,
            titleScale: Float = 1f,
            artistRowDp: Float = 0f,
            artistScale: Float = 1f
    ): Float {
        val needed = titleLineDp * titleScale + artistRowDp * artistScale
        val room = maxOf(bandDp, titleLineDp + artistRowDp)
        if (!(needed > 0f) || !(room > 0f)) return 1f
        return (room / needed).coerceAtMost(1f)
    }

    /** The height of a block centred on the screen that must stop at [floor]. */
    fun centredBand(floor: Float): Float = (2f * (floor - .5f)).coerceAtLeast(0f)

    /** The height a block moved to the top or bottom has between [top] and [floor]. */
    fun movedBand(top: Float, floor: Float): Float = (floor - top).coerceAtLeast(0f)

    /**
     * Where a block has to stop: the face's own [faceFloor], or [chromeTop] - the top of the shared
     * chrome in the bottom band, the mini-button row or the Up Next pill - when that begins higher.
     *
     * The face's floor alone is not enough. With the player controls hidden, Poster's floor drops
     * to its track time near the bottom of the screen, below where a configured mini-button row or
     * the Up Next pill sits, so a block sent to the bottom landed on the row.
     */
    fun effectiveFloor(faceFloor: Float, chromeTop: Float): Float = minOf(faceFloor, chromeTop)

    /**
     * How far below the middle of the screen a face's track time sits once it has been lifted
     * clear of the shared chrome in the bottom band - the watch's `TrackFooter` clamp, in screen
     * fractions. [designed] is the face's own offset, [chromeTop] the top of the mini-button row
     * or the Up Next pill (1 with neither), [gap] the clearance kept above it. Never lifted above
     * [FOOTER_MIN_OFFSET], so the time cannot be pulled over the face's middle.
     *
     * A face's floor has to count the lifted time too: with the pill showing, Poster's time moves
     * from the floor of the screen to just above the pill, into the band its block of text was
     * allowed to grow into, and a block sent to the bottom ended on top of it.
     */
    fun footerOffset(designed: Float, chromeTop: Float, gap: Float): Float {
        val clearance = chromeTop.coerceIn(.20f, .95f) - .5f - gap
        return minOf(designed, clearance).coerceAtLeast(minOf(designed, FOOTER_MIN_OFFSET))
    }

    /** See [footerOffset]. */
    const val FOOTER_MIN_OFFSET = .16f

    /**
     * Where a block moved to the top begins: under the clock when one is shown (its top padding,
     * its tallest configurable glyph and a gap), otherwise the margin every moved block keeps from
     * the glass. The watch's `blockRoomAbove` and the phone's miniature read the same numbers.
     */
    fun movedTop(clockShown: Boolean): Float = if (clockShown) CLOCK_CLEARANCE else EDGE_MARGIN

    /**
     * The top of the awake Up Next pill as the faces are told it (the watch's
     * `miniButtonsTopFraction` while the pill shows), and as the phone's miniature reserves it.
     */
    const val UP_NEXT_PILL_TOP = .66f

    /** See [movedTop]. */
    const val CLOCK_CLEARANCE = .22f

    /** How close to the glass a moved block may sit, at the top and, with nothing else there, at the bottom. */
    const val EDGE_MARGIN = .08f

    /**
     * Where Poster's controls begin: the play mark when it is drawn (higher when the progress line
     * sits under it), else the progress line, else the track time, else the edge of the readable
     * area. Mirrors `PosterComposition`'s own offsets.
     */
    fun posterFloor(controls: Boolean, progressLine: Boolean, trackTime: Boolean): Float = when {
        controls -> 1f - FaceGeometry.Poster.MARK_FRACTION -
                (if (progressLine) FaceGeometry.Poster.MARK_LIFT_WITH_LINE else FaceGeometry.Poster.MARK_LIFT)
        progressLine -> .5f + FaceGeometry.Poster.LINE_OFFSET - .01f
        // With no progress line the time sits on the floor of the screen.
        trackTime -> .5f + FaceGeometry.Poster.TIME_OFFSET - .02f
        else -> .9f
    }

    /**
     * Where Studio's controls begin: the progress orb at the bottom of the screen when it is drawn,
     * else the play mark it holds, else the track time. Mirrors `StudioComposition`'s own offsets.
     */
    fun studioFloor(progressOrb: Boolean, controls: Boolean, trackTime: Boolean): Float = when {
        progressOrb -> 1f - FaceGeometry.Studio.ORB_LIFT - 2f * FaceGeometry.Studio.ORB_RADIUS
        controls -> 1f - FaceGeometry.Studio.ORB_LIFT -
                FaceGeometry.Studio.ORB_RADIUS * FaceGeometry.Studio.MARK_OF_ORB
        trackTime -> .5f + FaceGeometry.Studio.TIME_OFFSET - .02f
        else -> .9f
    }

    /**
     * Expressive's band, in dp: from where its text hangs ([FaceGeometry.Expressive.TEXT_TOP_FRACTION])
     * down to the top of the ring around its play button, and never less than [EXPRESSIVE_MIN_BAND]
     * of the screen. [ringBoxDp] is that ring's box - 62dp, or 78dp on a watch of 225dp or more.
     */
    fun expressiveBand(screenDp: Float, ringBoxDp: Float): Float = maxOf(
            screenDp * (.5f - FaceGeometry.Expressive.TEXT_TOP_FRACTION) - ringBoxDp / 2f,
            screenDp * EXPRESSIVE_MIN_BAND)

    /** See [expressiveBand]. */
    const val EXPRESSIVE_MIN_BAND = .14f

    /** Material's block hangs from 17% of the screen and must stop at its centred disc's top. */
    const val MATERIAL_TOP = .17f
    const val MATERIAL_FLOOR = .5f - .30f / 2f

    /**
     * Split's panel text starts just under the seam and has to stop before the bezel. At its 100%
     * sizes the panel holds the artist and one title line; at 160% the title ran off the bottom of
     * the screen.
     */
    const val SPLIT_TOP = FaceGeometry.Split.SEAM_FRACTION + .04f
    const val SPLIT_FLOOR = .94f

    /**
     * Where Ribbon's title band ends: the depth its 100% line reaches on the reference 192dp watch
     * ([FaceGeometry.Ribbon.TITLE_TOP_FRACTION] plus one 29dp line). The title sits where the round
     * screen narrows fastest, and below this depth the glass loses width faster than a larger line
     * gains it, so a bigger title had to be shrunk to fit the narrower column - at 120% a short
     * title came out smaller than at 100%. [fitScale] against this band stops it growing instead;
     * on a larger watch the same depth holds a proportionally larger title.
     */
    const val RIBBON_TITLE_FLOOR = .92f

    /** Split's 21sp title and 15sp artist, as laid-out lines. */
    const val SPLIT_TITLE_LINE_DP = 26f
    const val SPLIT_ARTIST_ROW_DP = 19f
}
