package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionsScrollGeometryTest {
    @Test
    fun scrollIndicatorTraversesWholeTrack() {
        assertEquals(0f, quickActionScrollFraction(scrollOffset = 0, scrollRange = 400), 0f)
        assertEquals(0.5f, quickActionScrollFraction(scrollOffset = 200, scrollRange = 400), 0f)
        assertEquals(1f, quickActionScrollFraction(scrollOffset = 400, scrollRange = 400), 0f)
        assertEquals(0f, quickActionScrollFraction(scrollOffset = 10, scrollRange = 0), 0f)
    }
}
