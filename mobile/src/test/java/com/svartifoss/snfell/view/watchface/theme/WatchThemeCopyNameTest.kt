package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchThemeCopyNameTest {

    @Test
    fun `localized copy name is kept when it is available`() {
        assertEquals(
                "Cópia de Classic",
                uniqueWatchThemeCopyName(
                        copyBase = "Cópia de Classic",
                        existingNames = listOf("Classic"),
                        maxNameLength = 48))
    }

    @Test
    fun `copy suffix increments case insensitively`() {
        assertEquals(
                "Classic copy 3",
                uniqueWatchThemeCopyName(
                        copyBase = "Classic copy",
                        existingNames = listOf("classic COPY", "Classic Copy 2"),
                        maxNameLength = 48))
    }

    @Test
    fun `numeric suffix survives maximum length truncation`() {
        val result = uniqueWatchThemeCopyName(
                copyBase = "123456789012345",
                existingNames = listOf("123456789012", "1234567890 2"),
                maxNameLength = 12)

        assertEquals("1234567890 3", result)
        assertTrue(result.length <= 12)
    }
}
