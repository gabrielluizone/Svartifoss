package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekMarkerVisibilityTest {

    @Test
    fun `an unknown or absent value keeps the behaviour every install already has`() {
        // It can arrive from an imported backup, an installed community theme, or a newer phone
        // build talking to an older watch. Falling back to anything else would change the ring for
        // somebody who never chose to change it.
        assertEquals(SeekMarkerVisibility.DURING_SEEK, SeekMarkerVisibility.fromPreference(null))
        assertEquals(SeekMarkerVisibility.DURING_SEEK, SeekMarkerVisibility.fromPreference(""))
        assertEquals(
                SeekMarkerVisibility.DURING_SEEK,
                SeekMarkerVisibility.fromPreference("whenever_i_feel_like_it"))
    }

    @Test
    fun `every preference value round-trips`() {
        SeekMarkerVisibility.entries.forEach { visibility ->
            assertEquals(
                    visibility,
                    SeekMarkerVisibility.fromPreference(visibility.preferenceValue))
        }
    }

    @Test
    fun `the default draws the mark for the drag it was built for and at no other time`() {
        assertTrue(draws(SeekMarkerVisibility.DURING_SEEK, dragging = true, playing = true))
        assertTrue(draws(SeekMarkerVisibility.DURING_SEEK, dragging = true, playing = false))
        assertFalse(draws(SeekMarkerVisibility.DURING_SEEK, dragging = false, playing = true))
        assertFalse(draws(SeekMarkerVisibility.DURING_SEEK, dragging = false, playing = false))
    }

    @Test
    fun `always ignores both playback and the gesture`() {
        listOf(true, false).forEach { dragging ->
            listOf(true, false).forEach { playing ->
                assertTrue(
                        "always must draw for dragging=$dragging playing=$playing",
                        draws(SeekMarkerVisibility.ALWAYS, dragging, playing))
            }
        }
    }

    @Test
    fun `the paused modes differ only while a finger is dragging during playback`() {
        // This is the whole distinction between the two options, and the only combination where
        // they disagree -- so it is the one worth pinning.
        assertTrue(draws(SeekMarkerVisibility.DURING_SEEK_OR_PAUSED, dragging = true, playing = true))
        assertFalse(draws(SeekMarkerVisibility.WHILE_PAUSED, dragging = true, playing = true))

        listOf(
                Triple(true, false, true),
                Triple(false, false, true),
                Triple(false, true, false)
        ).forEach { (dragging, playing, expected) ->
            assertEquals(
                    "dragging=$dragging playing=$playing",
                    expected,
                    draws(SeekMarkerVisibility.DURING_SEEK_OR_PAUSED, dragging, playing))
            assertEquals(
                    "dragging=$dragging playing=$playing",
                    expected,
                    draws(SeekMarkerVisibility.WHILE_PAUSED, dragging, playing))
        }
    }

    private fun draws(
            visibility: SeekMarkerVisibility,
            dragging: Boolean,
            playing: Boolean
    ): Boolean = SeekMarkerVisibility.shouldDraw(visibility, dragging, playing)
}
