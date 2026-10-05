package com.svartifoss.snfell.common

import kotlin.math.ceil

/**
 * The sizes the watch's quick panel lays its composed blocks out with, in dp.
 *
 * Three things have to agree about how big a block is: the watch (which builds real Views), the
 * phone's miniature of it (which draws the same panel on a Canvas) and the editor's fold marker
 * (which says where the first screen ends). The watch's views wrap their content, so none of these
 * heights is ever stated on the wrist - they are the sum of the parts below, and the parts are what
 * the three sides share. Written once here for the reason `FaceGeometry` is: a miniature whose
 * chips are two dp narrower than the real ones is invisible in review and obvious on the wrist.
 *
 * ## One pill per block
 *
 * Every block that is made of several controls (volume, skip and position, tools, the favourites
 * grid) sits on **one** tonal surface - the same row surface the Up Next pill and the actions rows
 * use - and draws its glyphs and text in the colour that surface was chosen for. They used to be
 * bare controls on the backdrop, with the text in the panel's tint, and the tint is picked to read
 * on a pill: a dark ink on a light tonal surface, which on a dark album backdrop is dark text on a
 * dark ground. The block's surface is what makes its legibility a property of the panel's style
 * instead of a property of whichever song is playing.
 */
object QuickPanelGeometry {

    /** Space above every block. */
    const val GAP_DP = 6f

    /** Side padding inside a pill that holds a row of chips, so the first chip's press highlight
     *  does not touch the pill's rounded edge. */
    const val PILL_PADDING_H_DP = 6f

    // --- Volume ---

    const val VOLUME_HEIGHT_DP = 56f

    /** The touch target of the lower/raise buttons: 48dp, the least a finger can be asked to hit. */
    const val STEPPER_DP = 48f
    const val STEPPER_GLYPH_DP = 22f
    const val VOLUME_PADDING_H_DP = 4f

    // --- Skip and position ---

    /** The time, the bar and the space around them, above the chips. */
    const val SEEK_READOUT_DP = 39f

    /** Above the chips when there is no readout. */
    const val SEEK_PADDING_TOP_DP = 4f
    const val SEEK_CHIP_DP = 48f
    const val SEEK_GLYPH_DP = 28f
    const val SEEK_PADDING_BOTTOM_DP = 4f

    /** Four chips is the most a round screen holds at a finger-sized width. */
    const val SEEK_CHIPS_ACROSS = 4

    // --- Tools ---

    const val TOOLS_PER_ROW = 4
    const val TOOLS_MIN_COLUMNS = 3
    const val TOOLS_PADDING_V_DP = 6f
    const val TOOL_CHIP_DP = 56f
    const val TOOL_GLYPH_DP = 20f

    // --- Favourites grid ---

    const val FAVORITES_COLUMNS = 3
    const val FAVORITES_PADDING_V_DP = 8f
    const val FAVORITES_ART_DP = 54f
    const val FAVORITES_LABEL_GAP_DP = 3f
    const val FAVORITES_LABEL_DP = 12f
    const val FAVORITES_ROW_GAP_DP = 6f

    /** Height of the volume block, gap included. */
    fun volumeBlockDp(): Float = GAP_DP + VOLUME_HEIGHT_DP

    /** Height of the skip-and-position block, gap included. */
    fun seekBlockDp(showsReadout: Boolean): Float =
            GAP_DP + (if (showsReadout) SEEK_READOUT_DP else SEEK_PADDING_TOP_DP) +
                    SEEK_CHIP_DP + SEEK_PADDING_BOTTOM_DP

    /** How many tool chips share a line: as many as there are, but never fewer than the minimum
     *  width a chip is sized for, so two tools are not blown up to half the screen each. */
    fun toolColumns(tools: Int): Int =
            tools.coerceAtMost(TOOLS_PER_ROW).coerceAtLeast(TOOLS_MIN_COLUMNS)

    fun toolRows(tools: Int): Int = ceil(tools / TOOLS_PER_ROW.toFloat()).toInt()

    /** Height of the tools block, gap included. */
    fun toolsBlockDp(tools: Int): Float =
            GAP_DP + TOOLS_PADDING_V_DP * 2 + toolRows(tools) * TOOL_CHIP_DP

    fun favoriteRows(entries: Int): Int = ceil(entries / FAVORITES_COLUMNS.toFloat()).toInt()

    /** One line of the favourites grid: the cover and the name beneath it. */
    const val FAVORITES_LINE_DP = FAVORITES_ART_DP + FAVORITES_LABEL_GAP_DP + FAVORITES_LABEL_DP

    /** Height of the favourites grid, gap included; nothing starred takes no room at all. */
    fun favoritesGridBlockDp(entries: Int): Float {
        if (entries <= 0) return 0f
        val rows = favoriteRows(entries)
        return GAP_DP + FAVORITES_PADDING_V_DP * 2 + rows * FAVORITES_LINE_DP +
                (rows - 1) * FAVORITES_ROW_GAP_DP
    }
}
