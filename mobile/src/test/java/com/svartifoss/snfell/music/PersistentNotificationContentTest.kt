package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistentNotificationContentTest {

    private val reading = UpNextReading.of("Song A", "Song B", "Artist B")!!

    private fun compose(
            title: String?,
            artist: String? = "Artist A",
            reading: UpNextReading? = this.reading,
            shuffleOn: Boolean = false,
            repeatsOneTrack: Boolean = false
    ) = PersistentNotificationContent.compose(title, artist, reading, shuffleOn, repeatsOneTrack)

    @Test
    fun `shows the next track when the queue was read for the track on screen`() {
        val content = compose("Song A")

        assertEquals(NotificationTrack("Song A", "Artist A"), content.playing)
        assertEquals(NotificationTrack("Song B", "Artist B"), content.upNext)
    }

    @Test
    fun `withholds the next track until the queue catches up with a change of track`() {
        // The title moved to Song C; the queue has only been read for Song A so far.
        val content = compose("Song C", artist = "Artist C")

        assertEquals(NotificationTrack("Song C", "Artist C"), content.playing)
        assertNull(content.upNext)
    }

    @Test
    fun `withholds the next track under shuffle`() {
        assertNull(compose("Song A", shuffleOn = true).upNext)
    }

    @Test
    fun `withholds the next track under repeat one`() {
        assertNull(compose("Song A", repeatsOneTrack = true).upNext)
    }

    @Test
    fun `still shows the playing track under shuffle and repeat one`() {
        val content = compose("Song A", shuffleOn = true, repeatsOneTrack = true)

        assertEquals(NotificationTrack("Song A", "Artist A"), content.playing)
    }

    @Test
    fun `no known track gives the generic title and no next track`() {
        val content = compose(null)

        assertNull(content.playing)
        assertNull(content.upNext)
    }

    @Test
    fun `a blank title counts as no track, even with an artist`() {
        val content = compose("   ", artist = "Artist A")

        assertNull(content.playing)
        assertNull(content.upNext)
    }

    @Test
    fun `a blank artist is left out but the track stays`() {
        assertEquals(NotificationTrack("Song A", null), compose("Song A", artist = "  ").playing)
    }

    @Test
    fun `surrounding spaces do not stop a reading from matching the track on screen`() {
        assertEquals(NotificationTrack("Song B", "Artist B"), compose("  Song A ").upNext)
    }

    @Test
    fun `a reading needs a playing title and a next title`() {
        assertNull(UpNextReading.of("Song A", " ", "Artist B"))
        assertNull(UpNextReading.of(null, "Song B", null))
        assertEquals(NotificationTrack("Song B", null), UpNextReading.of("Song A", "Song B", "  ")!!.next)
    }

    @Test
    fun `shows where the playing track is in the queue, even under shuffle`() {
        val place = QueuePlace.of("Song A", index = 14, size = 25)
        val content = PersistentNotificationContent.compose("Song A", null, reading, true, false, place)
        assertEquals(QueueSpot(15, 25), content.spot)
    }

    @Test
    fun `withholds the position until the queue catches up with a change of track`() {
        val place = QueuePlace.of("Song A", index = 0, size = 3)
        assertNull(PersistentNotificationContent.compose("Song C", null, reading, false, false, place).spot)
    }

    @Test
    fun `a position outside the queue is no position`() {
        assertNull(QueuePlace.of("Song A", index = -1, size = 3))
        assertNull(QueuePlace.of("Song A", index = 3, size = 3))
        assertNull(QueuePlace.of(" ", index = 0, size = 3))
    }
}
