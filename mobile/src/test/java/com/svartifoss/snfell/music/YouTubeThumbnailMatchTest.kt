package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeThumbnailMatchTest {

    @Test
    fun `search query contains both title and channel`() {
        assertEquals(
                "radiohead in rainbows",
                YouTubeThumbnailMatch.searchQuery("Radiohead", "In Rainbows"))
    }

    @Test
    fun `presentation differences do not break an exact identity`() {
        assertTrue(YouTubeThumbnailMatch.accepts(
                expectedChannel = "Björk Official",
                expectedTitle = "Jóga!",
                resultChannel = "bjork official",
                resultTitle = "Joga"))
    }

    @Test
    fun `different channel or extra title words is rejected`() {
        assertFalse(YouTubeThumbnailMatch.accepts(
                expectedChannel = "Radiohead",
                expectedTitle = "In Rainbows",
                resultChannel = "Radiohead Topic",
                resultTitle = "In Rainbows"))
        assertFalse(YouTubeThumbnailMatch.accepts(
                expectedChannel = "Radiohead",
                expectedTitle = "In Rainbows",
                resultChannel = "Radiohead",
                resultTitle = "In Rainbows (Full Album)"))
    }

    @Test
    fun `empty identity does not produce a request`() {
        assertNull(YouTubeThumbnailMatch.cacheKey("", "Live"))
        assertNull(YouTubeThumbnailMatch.searchQuery("Channel", " "))
    }
}
