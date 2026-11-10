package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceElementsTest {

    @Test
    fun `both registries name only registered faces`() {
        // A face name that no longer exists would hide a row on the phone forever.
        assertEquals(emptySet<String>(),
                FaceElements.SOURCE_ICON_FACES - ThemeAppearance.ALLOWED_BASE_FACES)
        assertEquals(emptySet<String>(),
                FaceElements.TRACK_TIME_FACES - ThemeAppearance.ALLOWED_BASE_FACES)
    }

    @Test
    fun `the faces with no place for the app mark are exactly these`() {
        assertEquals(setOf("chat", "note", "verse", "metadata"),
                ThemeAppearance.ALLOWED_BASE_FACES - FaceElements.SOURCE_ICON_FACES)
    }

    @Test
    fun `the faces with no track time are exactly these`() {
        assertEquals(setOf("carousel", "split", "ribbon", "depth"),
                ThemeAppearance.ALLOWED_BASE_FACES - FaceElements.TRACK_TIME_FACES)
    }

    @Test
    fun `the classic View faces draw both`() {
        // Their artist row carries the mark and their block carries the time line.
        listOf("classic", "matejdro").forEach {
            assertTrue(FaceElements.drawsSourceIcon(it))
            assertTrue(FaceElements.drawsTrackTime(it))
        }
    }

    @Test
    fun `an unknown face draws neither`() {
        assertFalse(FaceElements.drawsSourceIcon("not-a-face"))
        assertFalse(FaceElements.drawsTrackTime(null))
    }
}
