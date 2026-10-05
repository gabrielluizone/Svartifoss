package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.ActionsMode
import com.svartifoss.snfell.common.QuickPanelBlock
import com.svartifoss.snfell.common.QuickPanelBlockType
import com.svartifoss.snfell.common.QuickPanelGeometry
import com.svartifoss.snfell.common.QuickPanelStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPanelFoldTest {

    private val none = QuickPanelFold.Counts(starredFavorites = 0, menuActions = 0)

    @Test
    fun `the original panel fits the title, the buttons and Up Next on its first screen`() {
        // Header + buttons + Up Next is what the watch shows with no scrolling today; the rows of
        // the actions list start below it.
        val last = QuickPanelFold.lastOnFirstScreen(
                QuickPanelStack.implicitStack(), QuickPanelFold.Counts(0, 14))
        assertEquals(2, last)
    }

    @Test
    fun `a long list of rows is below the fold`() {
        val blocks = listOf(
                QuickPanelBlock(QuickPanelBlockType.HEADER),
                QuickPanelBlock(QuickPanelBlockType.ACTIONS))
        // 50 + 14 rows of 60: the first row's middle is at 80, the second at 140, the third at 200.
        assertEquals(1, QuickPanelFold.lastOnFirstScreen(blocks, QuickPanelFold.Counts(0, 14)))
    }

    @Test
    fun `an empty favourites block takes no room and is not the last on the screen`() {
        val blocks = listOf(
                QuickPanelBlock(QuickPanelBlockType.BUTTONS),
                QuickPanelBlock(QuickPanelBlockType.FAVORITES),
                QuickPanelBlock(QuickPanelBlockType.VOLUME))
        assertEquals(2, QuickPanelFold.lastOnFirstScreen(blocks, none))
        assertEquals(0f, QuickPanelFold.heightDp(blocks[1], none), 0f)
    }

    @Test
    fun `favourites grow by line, three to a line`() {
        val grid = QuickPanelBlock(QuickPanelBlockType.FAVORITES)
        val heights = (0..7).map {
            QuickPanelFold.heightDp(grid, QuickPanelFold.Counts(starredFavorites = it, menuActions = 0))
        }
        // The block's own default of six caps eight starred entries at two lines.
        assertEquals(0f, heights[0], 0f)
        assertEquals(heights[1], heights[3], 0f)
        assertTrue(heights[4] > heights[3])
        assertEquals(heights[4], heights[6], 0f)
        assertEquals(heights[6], heights[7], 0f)
    }

    @Test
    fun `the actions block as a button is one row however long the menu is`() {
        val list = QuickPanelBlock(QuickPanelBlockType.ACTIONS)
        val button = list.withActionsMode(ActionsMode.BUTTON)
        val long = QuickPanelFold.Counts(starredFavorites = 0, menuActions = 14)

        assertEquals(14 * 60f, QuickPanelFold.heightDp(list, long), 0f)
        assertEquals(60f, QuickPanelFold.heightDp(button, long), 0f)
        // So a panel that only wants a way to the menu keeps the rest of it above the fold.
        val panel = listOf(
                QuickPanelBlock(QuickPanelBlockType.BUTTONS),
                QuickPanelBlock(QuickPanelBlockType.VOLUME),
                button)
        assertEquals(2, QuickPanelFold.lastOnFirstScreen(panel, long))
    }

    @Test
    fun `the blocks on one pill are as tall as the watch lays them out`() {
        // The fold draws its line from the same sums the watch and the miniature are built from; a
        // fold with numbers of its own would drift from both without anything failing.
        assertEquals(QuickPanelGeometry.volumeBlockDp(),
                QuickPanelFold.heightDp(QuickPanelBlock(QuickPanelBlockType.VOLUME), none), 0f)
        assertEquals(QuickPanelGeometry.seekBlockDp(true),
                QuickPanelFold.heightDp(QuickPanelBlock(QuickPanelBlockType.SEEK), none), 0f)
        assertEquals(QuickPanelGeometry.seekBlockDp(false),
                QuickPanelFold.heightDp(
                        QuickPanelBlock(QuickPanelBlockType.SEEK).withOption("bar", "0"), none), 0f)
        assertEquals(QuickPanelGeometry.toolsBlockDp(3),
                QuickPanelFold.heightDp(QuickPanelBlock(QuickPanelBlockType.TOOLS), none), 0f)
    }

    @Test
    fun `nothing on screen is reported as minus one`() {
        assertEquals(-1, QuickPanelFold.lastOnFirstScreen(emptyList(), none))
    }
}
