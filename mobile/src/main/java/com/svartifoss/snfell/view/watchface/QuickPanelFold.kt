package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.FavoritesMode
import com.svartifoss.snfell.common.QuickPanelBlock
import com.svartifoss.snfell.common.QuickPanelBlockType
import kotlin.math.ceil

/**
 * Where the first screen of the watch's quick panel ends.
 *
 * The panel scrolls, and the first screen is the one that matters: it is what appears when the
 * panel opens, with no scrolling, and the parts of a person's panel they use most should be on it.
 * The editor marks the block after which the fold falls so that is visible while arranging,
 * instead of discovered on the wrist.
 *
 * The heights are nominal - the ones the watch lays its blocks out with on a 233dp display - and
 * the answer is deliberately approximate: a layout or a long title can move a pill by a few dp.
 * What it has to get right is the order of magnitude, "this is on the first screen / this needs a
 * scroll", which is why it is a pure function over block kinds and counts rather than a measurement.
 */
internal object QuickPanelFold {

    /** What the content area of the watch's first screen holds before the round bezel eats the
     *  bottom of it: 233dp tall, less the 17% the panel leaves above its content and the arc. */
    const val FIRST_SCREEN_DP = 175f

    /** Counts the heights depend on, taken from the user's own lists. */
    data class Counts(val starredFavorites: Int, val menuActions: Int)

    /** The height of [block] on the watch, in dp, including the gap above it. */
    fun heightDp(block: QuickPanelBlock, counts: Counts): Float = when (block.type) {
        QuickPanelBlockType.HEADER -> 50f
        QuickPanelBlockType.BUTTONS -> 58f
        QuickPanelBlockType.UP_NEXT -> 60f
        QuickPanelBlockType.VOLUME -> 58f
        QuickPanelBlockType.SEEK -> (if (block.seekShowsBar) 29f else 0f) + 54f
        QuickPanelBlockType.TOOLS -> block.tools.chunked(TOOLS_PER_LINE).size * 62f + 2f
        QuickPanelBlockType.FAVORITES -> {
            val shown = limited(counts.starredFavorites, block.maxEntries)
            when {
                shown == 0 -> 0f
                block.favoritesMode == FavoritesMode.GRID ->
                    ceil(shown / GRID_COLUMNS.toFloat()) * 75f
                else -> shown * 60f
            }
        }
        QuickPanelBlockType.ACTIONS -> limited(counts.menuActions, block.maxEntries) * 60f
        QuickPanelBlockType.MENU_LINK -> 60f
    }

    /**
     * The least of a block that has to fit before it counts as being on the first screen: enough to
     * show what it is. A tall list block is judged by its start, not its middle - fourteen rows
     * have a middle several screens down, but the first of them is visible and the block is
     * plainly on the first screen.
     */
    const val MIN_VISIBLE_DP = 24f

    /**
     * The index of the last block that is on the first screen, or -1 when none is. A block counts
     * as being on it when at least [MIN_VISIBLE_DP] of it fits before the fold - so one that only
     * pokes its top edge in is on the second. Blocks with no height (an empty favourites block)
     * are skipped over.
     */
    fun lastOnFirstScreen(blocks: List<QuickPanelBlock>, counts: Counts): Int {
        var top = 0f
        var last = -1
        blocks.forEachIndexed { index, block ->
            val height = heightDp(block, counts)
            if (height <= 0f) return@forEachIndexed
            if (top + minOf(height, MIN_VISIBLE_DP) <= FIRST_SCREEN_DP) last = index
            top += height
        }
        return last
    }

    private fun limited(available: Int, max: Int): Int =
            if (max > 0) minOf(available, max) else available

    private const val TOOLS_PER_LINE = 4
    private const val GRID_COLUMNS = 3
}
