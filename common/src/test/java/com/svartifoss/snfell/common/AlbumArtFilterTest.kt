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
    fun `filter styles are plain artwork treatments with distinct matrices`() {
        val styles = PlayerBackgroundStyle.entries.filter {
            it.preferenceValue.startsWith("filter_")
        }
        assertEquals(16, styles.size)
        assertTrue(styles.all(PlayerBackgroundStyle::isPlainArtworkTreatment))
        assertEquals(styles.size, styles.map { it.artworkFilter.matrixValues?.contentHashCode() }.toSet().size)
        assertNotEquals(AlbumArtFilter.NONE, PlayerBackgroundStyle.FILTER_WARM.artworkFilter)
    }
}
