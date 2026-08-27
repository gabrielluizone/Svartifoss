package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityThemeGalleryLayoutTest {

    @Test
    fun `narrow phone keeps one readable column`() {
        assertEquals(1, communityGallerySpanCount(viewportWidthDp = 320, fontScale = 1f))
    }

    @Test
    fun `ordinary phone uses two columns`() {
        assertEquals(2, communityGallerySpanCount(viewportWidthDp = 360, fontScale = 1f))
    }

    @Test
    fun `wide screens add columns but cap the gallery at four`() {
        assertEquals(3, communityGallerySpanCount(viewportWidthDp = 600, fontScale = 1f))
        assertEquals(4, communityGallerySpanCount(viewportWidthDp = 1_200, fontScale = 1f))
    }

    @Test
    fun `large text widens cards instead of squeezing their labels`() {
        assertEquals(1, communityGallerySpanCount(viewportWidthDp = 360, fontScale = 1.3f))
        assertEquals(3, communityGallerySpanCount(viewportWidthDp = 600, fontScale = 1.3f))
    }
}
