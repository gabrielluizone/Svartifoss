package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineCoverMatchTest {

    // ---- the channel is the performer ----------------------------------------------------------

    /** The case that exists: an official upload, where the channel is the band. */
    @Test
    fun `an official channel matches the result by the same performer`() {
        assertTrue(OnlineCoverMatch.accepts(
                channel = "Radiohead",
                title = "Radiohead - In Rainbows From the Basement (April 2008)",
                hitArtist = "Radiohead",
                hitTrack = "Bodysnatchers"))
    }

    @Test
    fun `channel suffixes are not part of the performer`() {
        assertEquals("radiohead", OnlineCoverMatch.performerFrom("Radiohead - Topic"))
        assertEquals("taylor swift", OnlineCoverMatch.performerFrom("Taylor Swift VEVO"))
        assertEquals("taylor swift", OnlineCoverMatch.performerFrom("TaylorSwiftVEVO"))
        assertEquals("daft punk", OnlineCoverMatch.performerFrom("Daft Punk Official"))
    }

    @Test
    fun `an auto-generated or label channel still matches its performer`() {
        assertTrue(OnlineCoverMatch.accepts("Radiohead - Topic", "Creep", "Radiohead", "Creep"))
        assertTrue(OnlineCoverMatch.accepts("Taylor Swift VEVO", "Blank Space", "Taylor Swift", "Blank Space"))
    }

    /** Accents and case are not a reason to miss a record. */
    @Test
    fun `accents and case do not matter`() {
        assertTrue(OnlineCoverMatch.accepts("BEYONCÉ", "Halo", "Beyoncé", "Halo"))
        assertTrue(OnlineCoverMatch.accepts("sigur ros", "Hoppípolla", "Sigur Rós", "Hoppípolla"))
    }

    // ---- the title names both -----------------------------------------------------------------

    @Test
    fun `a title that names the performer and the track matches whoever uploaded it`() {
        assertTrue(OnlineCoverMatch.accepts(
                channel = "Some Lyrics Channel",
                title = "Oasis - Wonderwall (Lyrics)",
                hitArtist = "Oasis",
                hitTrack = "Wonderwall"))
    }

    /** The reported streams: somebody else's channel, a compilation or a 24/7 radio. */
    @Test
    fun `a live stream that merely mentions a band is not that band's record`() {
        assertFalse(OnlineCoverMatch.accepts(
                channel = "Universal Documentaries",
                title = "Noel Gallagher Performs Oasis Classics | LIVE 24/7 | Once in a Lifetime Sessions",
                hitArtist = "Oasis",
                hitTrack = "Wonderwall"))
        assertFalse(OnlineCoverMatch.accepts(
                channel = "Route Country Rockers",
                title = "Greatest Alternative Rock Of The 90s 2000s Linkin Park, Creed, Audioslave, Hoobastank, Oasis",
                hitArtist = "Linkin Park",
                hitTrack = "Numb"))
    }

    // ---- what must never match ------------------------------------------------------------------

    /** A band called Bread must not dress a cooking video. */
    @Test
    fun `a common word that happens to be an artist is not evidence`() {
        assertFalse(OnlineCoverMatch.accepts(
                channel = "Chef John",
                title = "How to make bread at home",
                hitArtist = "Bread",
                hitTrack = "Make It with You"))
    }

    @Test
    fun `whole words only`() {
        assertFalse(OnlineCoverMatch.accepts("Chef John", "A study of breadth", "Bread", "Aubrey"))
    }

    @Test
    fun `a very short name or track is never enough`() {
        assertFalse(OnlineCoverMatch.accepts("DJ", "Up all night", "Up", "Up"))
        assertFalse(OnlineCoverMatch.accepts("Some Channel", "Run to the hills", "Iron Maiden", "Run"))
    }

    @Test
    fun `nothing in common is no match`() {
        assertFalse(OnlineCoverMatch.accepts("Veritasium", "The Strange Math That Predicts", "Metallica", "One"))
    }

    // ---- the query ------------------------------------------------------------------------------

    @Test
    fun `the query drops what only describes the upload`() {
        assertEquals("radiohead Radiohead - In Rainbows From the Basement",
                OnlineCoverMatch.searchQuery(
                        "Radiohead", "Radiohead - In Rainbows From the Basement (April 2008)"))
        assertEquals("oasis Wonderwall",
                OnlineCoverMatch.searchQuery("Oasis - Topic", "Wonderwall [Official Video] | HD"))
    }

    @Test
    fun `a paragraph of a title is bounded`() {
        val query = OnlineCoverMatch.searchQuery("Channel", "word ".repeat(100))
        assertTrue(query.length <= 120)
    }
}
