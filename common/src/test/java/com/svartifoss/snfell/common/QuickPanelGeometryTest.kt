package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPanelGeometryTest {

    @Test
    fun `a short tools block is sized for three chips and a full one for four`() {
        assertEquals(3, QuickPanelGeometry.toolColumns(1))
        assertEquals(3, QuickPanelGeometry.toolColumns(2))
        assertEquals(3, QuickPanelGeometry.toolColumns(3))
        assertEquals(4, QuickPanelGeometry.toolColumns(4))
        // Past a line, every line is still four wide: the extras wrap, they do not shrink the rest.
        assertEquals(4, QuickPanelGeometry.toolColumns(8))
    }

    @Test
    fun `tools grow by line`() {
        assertEquals(1, QuickPanelGeometry.toolRows(4))
        assertEquals(2, QuickPanelGeometry.toolRows(5))
        assertEquals(
                QuickPanelGeometry.toolsBlockDp(4) + QuickPanelGeometry.TOOL_CHIP_DP,
                QuickPanelGeometry.toolsBlockDp(8), 0f)
    }

    @Test
    fun `the readout is what separates the two heights of the seek block`() {
        val withReadout = QuickPanelGeometry.seekBlockDp(true)
        val without = QuickPanelGeometry.seekBlockDp(false)
        assertEquals(
                QuickPanelGeometry.SEEK_READOUT_DP - QuickPanelGeometry.SEEK_PADDING_TOP_DP,
                withReadout - without, 0f)
    }

    @Test
    fun `every chip row is a finger-sized touch target`() {
        // 48dp is the least Android asks of a target; a smaller number here would ship a control
        // that the preview happily draws and a thumb cannot hit.
        assertTrue(QuickPanelGeometry.STEPPER_DP >= 48f)
        assertTrue(QuickPanelGeometry.SEEK_CHIP_DP >= 48f)
        assertTrue(QuickPanelGeometry.TOOL_CHIP_DP >= 48f)
        assertTrue(QuickPanelGeometry.VOLUME_HEIGHT_DP >= QuickPanelGeometry.STEPPER_DP)
    }

    @Test
    fun `favourites take no room until something is starred, then grow by line`() {
        assertEquals(0f, QuickPanelGeometry.favoritesGridBlockDp(0), 0f)
        val one = QuickPanelGeometry.favoritesGridBlockDp(1)
        assertEquals(one, QuickPanelGeometry.favoritesGridBlockDp(3), 0f)
        assertEquals(
                one + QuickPanelGeometry.FAVORITES_LINE_DP + QuickPanelGeometry.FAVORITES_ROW_GAP_DP,
                QuickPanelGeometry.favoritesGridBlockDp(4), 0f)
    }
}
