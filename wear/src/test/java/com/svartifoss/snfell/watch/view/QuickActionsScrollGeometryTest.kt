package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsScrollGeometryTest {
    @Test
    fun centreItemsRemainFullSizeLikeScalingLazyColumn() {
        val transform = quickActionItemTransform(
                viewportStart = 0f,
                viewportEnd = 200f,
                itemStart = 75f,
                itemEnd = 125f
        )

        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(1f, transform.alpha, 0.0001f)
    }

    @Test
    fun bezelItemsRecedeUsingWearComposeEdgeLimits() {
        val partlyVisible = quickActionItemTransform(0f, 200f, -25f, 25f)
        val outside = quickActionItemTransform(0f, 200f, -50f, 0f)

        assertTrue(partlyVisible.scale in 0.7f..1f)
        assertTrue(partlyVisible.alpha in 0.5f..1f)
        assertEquals(0.7f, outside.scale, 0.0001f)
        assertEquals(0.5f, outside.alpha, 0.0001f)
    }

    @Test
    fun scrollIndicatorTraversesWholeTrack() {
        assertEquals(0f, quickActionScrollFraction(scrollOffset = 0, scrollRange = 400), 0f)
        assertEquals(0.5f, quickActionScrollFraction(scrollOffset = 200, scrollRange = 400), 0f)
        assertEquals(1f, quickActionScrollFraction(scrollOffset = 400, scrollRange = 400), 0f)
        assertEquals(0f, quickActionScrollFraction(scrollOffset = 10, scrollRange = 0), 0f)
    }
}
