package com.svartifoss.snfell.watch.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Accent *selection* moved to `common` along with the function itself - see
 * `AlbumAccentSelectionTest` there. What stays here is the companion-slot picking, which is still
 * watch-only.
 */
class AlbumPaletteTest {
    @Test
    fun companionColorsComeOnlyFromRankedAlbumSwatches() {
        val primary = 0xFF1769AA.toInt()
        val coverBrown = 0xFF6E3B33.toInt()
        val coverPlum = 0xFF241B2F.toInt()

        val result = selectAlbumCompanionColors(
                primary,
                listOf(primary, coverBrown, coverBrown, coverPlum)
        )

        assertEquals(coverBrown, result.secondary)
        assertEquals(coverPlum, result.tertiary)
    }

    @Test
    fun monochromaticArtworkRequestsSameHueFallbackFromRenderer() {
        val primary = 0xFF1769AA.toInt()

        val result = selectAlbumCompanionColors(primary, listOf(primary, primary))

        assertNull(result.secondary)
        assertNull(result.tertiary)
    }
}
