package com.svartifoss.snfell.watch.view

import com.svartifoss.snfell.watch.view.face.NowPlayingFaceState
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.LoveLetterTypewriterFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class LainFontTest {
    @Test
    fun testKeywordsTriggers() {
        val testCases = listOf(
            "iwakura" to true,
            "Lain" to true,
            "wired" to true,
            "breakcore" to true,
            "serial experiments" to true,
            "nothing" to false,
            "wiredlau" to true,
            "serial experiments lain" to true,
            "breakcore music" to true
        )

        for ((input, expected) in testCases) {
            val state = NowPlayingFaceState(title = input, artist = "other")
            val expectedFont = if (expected) LoveLetterTypewriterFamily else GoogleSansFamily
            assertEquals("Failed for title: '$input'", expectedFont, state.titleFont)
            assertEquals("Failed for artist with title: '$input'", expectedFont, state.artistFont)
        }

        // Test artist triggering title font
        val stateArtistTrigger = NowPlayingFaceState(title = "normal", artist = "iwakura")
        assertEquals(LoveLetterTypewriterFamily, stateArtistTrigger.titleFont)
        assertEquals(LoveLetterTypewriterFamily, stateArtistTrigger.artistFont)
    }
}
