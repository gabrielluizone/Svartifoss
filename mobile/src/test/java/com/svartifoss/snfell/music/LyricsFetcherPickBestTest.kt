package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins [LyricsFetcher.pickBest]. The ordering it encodes looks obvious and is easy to write
 * backwards - preferring the closest duration reads as "the most accurate match" right up until it
 * picks an untimed upload over a timed one and the screen silently stops following the song.
 */
class LyricsFetcherPickBestTest {

    private fun candidate(
            name: String,
            durationSec: Long,
            synced: Boolean,
            plain: Boolean = true,
    ) = LyricsCandidate(
            trackName = name,
            artistName = "artist",
            durationSec = durationSec,
            instrumental = false,
            plain = if (plain) "plain text" else null,
            synced = if (synced) "[00:01.00]line" else null)

    @Test
    fun `a synced candidate beats an unsynced one with a better duration`() {
        val exactButUntimed = candidate("exact", durationSec = 200, synced = false)
        val offByTenButTimed = candidate("timed", durationSec = 210, synced = true)

        val best = LyricsFetcher.pickBest(listOf(exactButUntimed, offByTenButTimed), durationSec = 200)

        assertEquals("timed", best?.trackName)
    }

    @Test
    fun `among synced candidates the closest duration wins`() {
        // What keeps a remix or an extended edit from being picked over the actual recording.
        val remix = candidate("remix", durationSec = 400, synced = true)
        val album = candidate("album", durationSec = 203, synced = true)

        val best = LyricsFetcher.pickBest(listOf(remix, album), durationSec = 200)

        assertEquals("album", best?.trackName)
    }

    @Test
    fun `among unsynced candidates the closest duration still wins`() {
        val far = candidate("far", durationSec = 300, synced = false)
        val near = candidate("near", durationSec = 198, synced = false)

        val best = LyricsFetcher.pickBest(listOf(far, near), durationSec = 200)

        assertEquals("near", best?.trackName)
    }

    @Test
    fun `candidates carrying no lyrics at all are ignored`() {
        val empty = LyricsCandidate("empty", "artist", 200, instrumental = false, plain = null, synced = null)
        val usable = candidate("usable", durationSec = 260, synced = false)

        val best = LyricsFetcher.pickBest(listOf(empty, usable), durationSec = 200)

        assertEquals("usable", best?.trackName)
    }

    @Test
    fun `an empty list yields null rather than a crash`() {
        assertNull(LyricsFetcher.pickBest(emptyList(), durationSec = 200))
    }

    @Test
    fun `a list with nothing usable yields null`() {
        val empty = LyricsCandidate("empty", "artist", 200, instrumental = true, plain = null, synced = null)
        assertNull(LyricsFetcher.pickBest(listOf(empty), durationSec = 200))
    }

    @Test
    fun `with an unknown duration relevance order decides but synced still wins`() {
        // durationSec = 0 means the playing app never reported one; every candidate ties on
        // distance, so LRCLIB's own ordering is all that is left - except that timings still win.
        val firstUntimed = candidate("first", durationSec = 999, synced = false)
        val secondTimed = candidate("second", durationSec = 111, synced = true)

        val best = LyricsFetcher.pickBest(listOf(firstUntimed, secondTimed), durationSec = 0)

        assertEquals("second", best?.trackName)
    }

    @Test
    fun `with an unknown duration the first usable candidate wins among equals`() {
        val first = candidate("first", durationSec = 999, synced = true)
        val second = candidate("second", durationSec = 111, synced = true)

        val best = LyricsFetcher.pickBest(listOf(first, second), durationSec = 0)

        assertEquals("first", best?.trackName)
    }
}
