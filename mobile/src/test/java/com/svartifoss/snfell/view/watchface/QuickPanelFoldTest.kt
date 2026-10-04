package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.QuickPanelBlock
import com.svartifoss.snfell.common.QuickPanelBlockType
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
    fun `nothing on screen is reported as minus one`() {
        assertEquals(-1, QuickPanelFold.lastOnFirstScreen(emptyList(), none))
    }
}
