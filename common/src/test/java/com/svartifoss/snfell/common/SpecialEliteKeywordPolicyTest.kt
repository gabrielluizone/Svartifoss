package com.svartifoss.snfell.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialEliteKeywordPolicyTest {
    @Test
    fun matchesKeywordsInTitleOrArtistCaseInsensitively() {
        assertTrue(SpecialEliteKeywordPolicy.matches("Serial Experiments Lain", "Artist"))
        assertTrue(SpecialEliteKeywordPolicy.matches("Track", "The Wired Project"))
        assertTrue(SpecialEliteKeywordPolicy.matches("IWAKURA", "Artist"))
    }

    @Test
    fun ignoresTracksWithoutKeywords() {
        assertFalse(SpecialEliteKeywordPolicy.matches("Ordinary Song", "Ordinary Artist"))
        assertFalse(SpecialEliteKeywordPolicy.matches("breakcore mix", "Artist"))
        assertFalse(SpecialEliteKeywordPolicy.matches("Track", "Breakcore Artist"))
    }
}
