package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtFilterTest {

    @Test
    fun `every photo filter supplies a finite Android color matrix`() {
        AlbumArtFilter.entries.filterNot { it == AlbumArtFilter.NONE }.forEach { filter ->
            val values = requireNotNull(filter.matrixValues)
            assertEquals(filter.name, 20, values.size)
            assertTrue(filter.name, values.all(Float::isFinite))
        }
    }

    @Test
    fun `legacy filter aliases remain plain while the new filter vocabulary is independent`() {
        val styles = PlayerBackgroundStyle.entries.filter {
            it.preferenceValue.startsWith("filter_")
        }
        assertEquals(16, styles.size)
        assertTrue(styles.all(PlayerBackgroundStyle::isPlainArtworkTreatment))
        assertEquals(styles.size, styles.map { it.artworkFilter.matrixValues?.contentHashCode() }.toSet().size)
        assertNotEquals(AlbumArtFilter.NONE, PlayerBackgroundStyle.FILTER_WARM.artworkFilter)
        assertEquals(AlbumArtFilter.MOSS, AlbumArtFilter.fromPreference("moss"))
        assertEquals(AlbumArtFilter.CANDY, AlbumArtFilter.fromPreference("candy"))
        assertEquals(AlbumArtFilter.WARM,
                resolveAlbumArtFilter("warm", PlayerBackgroundStyle.BLACK_AND_WHITE))
        assertEquals(AlbumArtFilter.MONOCHROME,
                resolveAlbumArtFilter("none", PlayerBackgroundStyle.BLACK_AND_WHITE))
    }
}
