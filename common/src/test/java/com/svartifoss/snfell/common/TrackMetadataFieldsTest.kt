package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMetadataFieldsTest {

    // ---- duration ---------------------------------------------------------

    @Test
    fun `a track under an hour is minutes and seconds`() {
        assertEquals("3:07", TrackMetadataFields.formatDuration(187_000))
    }

    @Test
    fun `an hour-long track grows an hours field rather than counting to 90 minutes`() {
        assertEquals("1:02:03", TrackMetadataFields.formatDuration(3_723_000))
    }

    /** "Do not invent information": a duration nobody published is a row that does not exist. */
    @Test
    fun `an absent duration is absent, not zero`() {
        assertNull(TrackMetadataFields.formatDuration(0))
        assertNull(TrackMetadataFields.formatDuration(-1))
    }

    // ---- bitrate ----------------------------------------------------------

    @Test
    fun `bitrate is rounded to whole kbps`() {
        assertEquals("320 kbps", TrackMetadataFields.formatBitrate(320_537))
        assertEquals("128 kbps", TrackMetadataFields.formatBitrate(128_000))
    }

    /** A value already small enough to be kbps is a player reporting the wrong unit, not a
     *  0.32 kbps file. */
    @Test
    fun `a sub-kilobit bitrate is rejected rather than shown as a fraction`() {
        assertNull(TrackMetadataFields.formatBitrate(320))
        assertNull(TrackMetadataFields.formatBitrate(0))
    }

    // ---- sample rate ------------------------------------------------------

    @Test
    fun `a whole sample rate loses its trailing zero`() {
        assertEquals("48 kHz", TrackMetadataFields.formatSampleRate(48_000))
        assertEquals("96 kHz", TrackMetadataFields.formatSampleRate(96_000))
    }

    @Test
    fun `the one rate that needs a decimal keeps it`() {
        assertEquals("44.1 kHz", TrackMetadataFields.formatSampleRate(44_100))
    }

    @Test
    fun `an absent sample rate is absent`() {
        assertNull(TrackMetadataFields.formatSampleRate(0))
    }

    // ---- channels ---------------------------------------------------------

    @Test
    fun `channel counts are named rather than counted`() {
        assertEquals("Mono", TrackMetadataFields.formatChannels(1))
        assertEquals("Stereo", TrackMetadataFields.formatChannels(2))
        assertEquals("5.1", TrackMetadataFields.formatChannels(6))
        assertEquals("7.1", TrackMetadataFields.formatChannels(8))
    }

    @Test
    fun `an unusual channel count falls back to the number`() {
        assertEquals("3 ch", TrackMetadataFields.formatChannels(3))
        assertNull(TrackMetadataFields.formatChannels(0))
    }

    // ---- file size --------------------------------------------------------

    @Test
    fun `file size switches unit at a megabyte`() {
        assertEquals("512 KB", TrackMetadataFields.formatFileSize(512 * 1024))
        assertEquals("8.4 MB", TrackMetadataFields.formatFileSize((8.4 * 1024 * 1024).toLong()))
    }

    @Test
    fun `an absent file size is absent`() {
        assertNull(TrackMetadataFields.formatFileSize(0))
    }

    // ---- track position ---------------------------------------------------

    @Test
    fun `a track position shows its total when there is one`() {
        assertEquals("7 / 12", TrackMetadataFields.formatTrackPosition(7, 12, 1))
        assertEquals("7", TrackMetadataFields.formatTrackPosition(7, 0, 1))
    }

    /** Nearly every single-disc release reports disc 1, so printing it makes a useful detail into
     *  a constant. */
    @Test
    fun `disc one is dropped and later discs are kept`() {
        assertEquals("3 / 10", TrackMetadataFields.formatTrackPosition(3, 10, 1))
        assertEquals("3 / 10 (disc 2)", TrackMetadataFields.formatTrackPosition(3, 10, 2))
    }

    @Test
    fun `no track number is no row`() {
        assertNull(TrackMetadataFields.formatTrackPosition(0, 12, 1))
    }

    // ---- codec ------------------------------------------------------------

    @Test
    fun `the codec is the subtype, uppercased`() {
        assertEquals("MPEG", TrackMetadataFields.formatCodec("audio/mpeg"))
        assertEquals("FLAC", TrackMetadataFields.formatCodec("audio/flac"))
    }

    @Test
    fun `the vendor prefix is stripped`() {
        assertEquals("MS-WMA", TrackMetadataFields.formatCodec("audio/x-ms-wma"))
    }

    @Test
    fun `a missing or malformed mime type yields nothing`() {
        assertNull(TrackMetadataFields.formatCodec(null))
        assertNull(TrackMetadataFields.formatCodec(""))
        assertNull(TrackMetadataFields.formatCodec("audio/"))
    }

    // ---- groups -----------------------------------------------------------

    /** Each group is a face-scoped preference, so every key has to be distinct and non-blank or
     *  two blocks would share one switch. */
    // ---- precise position -------------------------------------------------

    @Test
    fun `the precise form keeps three digits of milliseconds`() {
        assertEquals("3:07.045", TrackMetadataFields.formatPreciseDuration(187_045))
    }

    @Test
    fun `the precise form grows an hours field like the plain one`() {
        assertEquals("1:02:03.400", TrackMetadataFields.formatPreciseDuration(3_723_400))
    }

    /** Unlike [TrackMetadataFields.formatDuration], zero is a real position - a track at its very
     *  start is at 0:00.000, and refusing it would blank the row for the first millisecond. */
    @Test
    fun `zero is a real position even though it is not a real duration`() {
        assertEquals("0:00.000", TrackMetadataFields.formatPreciseDuration(0))
        assertNull(TrackMetadataFields.formatPreciseDuration(-1))
    }

    @Test
    fun `a position is paired with its total`() {
        assertEquals("1:23.456 / 3:45.678",
                TrackMetadataFields.formatPlaybackPosition(83_456, 225_678))
    }

    /** A player that publishes no duration (a live stream) still has a position worth showing. */
    @Test
    fun `a position with no duration is shown alone rather than against a zero`() {
        assertEquals("1:23.456", TrackMetadataFields.formatPlaybackPosition(83_456, 0))
    }

    /**
     * The position is extrapolated between the phone's samples, so it overshoots at the end of a
     * track by design. `3:46.010 / 3:45.678` reads as a bug rather than as rounding.
     */
    @Test
    fun `an overshooting position is clamped to the duration`() {
        assertEquals("3:45.678 / 3:45.678",
                TrackMetadataFields.formatPlaybackPosition(226_010, 225_678))
    }

    // ---- speed ------------------------------------------------------------

    /** A row reading "1x" on every track is a constant, and a constant is not information. */
    @Test
    fun `ordinary speed is no row at all`() {
        assertNull(TrackMetadataFields.formatSpeed(1f))
        assertNull(TrackMetadataFields.formatSpeed(0.999998f))
    }

    @Test
    fun `an altered speed is shown without trailing zeroes`() {
        assertEquals("1.25x", TrackMetadataFields.formatSpeed(1.25f))
        assertEquals("2x", TrackMetadataFields.formatSpeed(2f))
        assertEquals("0.5x", TrackMetadataFields.formatSpeed(0.5f))
    }

    /** A stopped or nonsensical rate is not a speed to display. */
    @Test
    fun `a non-positive or non-finite speed is rejected`() {
        assertNull(TrackMetadataFields.formatSpeed(0f))
        assertNull(TrackMetadataFields.formatSpeed(-1f))
        assertNull(TrackMetadataFields.formatSpeed(Float.NaN))
    }

    // ---- origin and host --------------------------------------------------

    @Test
    fun `a scheme decides where the audio comes from`() {
        assertEquals(TrackMetadataFields.Origin.STREAM,
                TrackMetadataFields.Origin.of("https://i.scdn.co/track.mp3"))
        assertEquals(TrackMetadataFields.Origin.FILE,
                TrackMetadataFields.Origin.of("file:///sdcard/Music/a.flac"))
        assertEquals(TrackMetadataFields.Origin.CONTENT,
                TrackMetadataFields.Origin.of("content://media/external/audio/media/1043"))
        assertEquals(TrackMetadataFields.Origin.OTHER,
                TrackMetadataFields.Origin.of("spotify://track/xyz"))
    }

    /** No URI at all is a different thing from a URI this app cannot classify. */
    @Test
    fun `an absent uri has no origin rather than an unknown one`() {
        assertNull(TrackMetadataFields.Origin.of(null))
        assertNull(TrackMetadataFields.Origin.of("   "))
        assertNull(TrackMetadataFields.Origin.of("not-a-uri"))
    }

    @Test
    fun `the host is the part of a signed url worth reading`() {
        assertEquals("rr3---sn-4g5e6nz7s.googlevideo.com",
                TrackMetadataFields.uriHost(
                        "https://rr3---sn-4g5e6nz7s.googlevideo.com/videoplayback?expire=1&sig=x"))
    }

    /** `www.` is never the informative half of a host. */
    @Test
    fun `a www prefix is dropped`() {
        assertEquals("example.com", TrackMetadataFields.uriHost("https://www.example.com/a.mp3"))
    }

    @Test
    fun `credentials in a url do not become the host`() {
        assertEquals("cdn.example.com",
                TrackMetadataFields.uriHost("https://user:pw@cdn.example.com/a.mp3"))
    }

    /** A local URI has no host, and inventing "media" from a content authority would be naming
     *  the plumbing rather than a server. */
    @Test
    fun `a file uri has no host`() {
        assertNull(TrackMetadataFields.uriHost("file:///sdcard/Music/a.flac"))
        assertNull(TrackMetadataFields.uriHost(null))
    }

    // ---- output codes -----------------------------------------------------

    /** The code crosses Bluetooth, so a newer phone naming an output this build has never heard of
     *  must degrade to "say nothing" rather than throw. */
    @Test
    fun `an unknown output code resolves rather than throwing`() {
        assertEquals(TrackMetadataFields.Output.UNKNOWN, TrackMetadataFields.Output.fromCode(99))
        assertEquals(TrackMetadataFields.Output.BLUETOOTH, TrackMetadataFields.Output.fromCode(3))
    }

    /** Codes are the wire format: renumbering one silently re-labels every row a paired phone
     *  sends until both sides are updated. */
    @Test
    fun `output codes are stable and distinct`() {
        val codes = TrackMetadataFields.Output.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(0, TrackMetadataFields.Output.UNKNOWN.code)
    }

    @Test
    fun `every group has its own preference key`() {
        val keys = TrackMetadataFields.Group.entries.map { it.preferenceKey }
        assertEquals("group preference keys are not unique", keys.size, keys.toSet().size)
        keys.forEach { assertTrue("blank group key", it.isNotBlank()) }
    }

    /** Catalogue numbers are useful when you want one and noise on a wrist when you do not. */
    @Test
    fun `identifiers are the one group that starts hidden`() {
        TrackMetadataFields.Group.entries.forEach { group ->
            assertEquals(
                    "unexpected default for $group",
                    group != TrackMetadataFields.Group.IDENTIFIERS,
                    group.defaultVisible)
        }
    }

    @Test
    fun `group keys are all registered as face-scoped appearance keys`() {
        TrackMetadataFields.Group.entries.forEach { group ->
            assertTrue(
                    "${group.preferenceKey} is not in FaceScopedPreferences.SCOPED_KEYS, so it " +
                            "would change every face at once and no saved theme could carry it",
                    group.preferenceKey in FaceScopedPreferences.SCOPED_KEYS)
            assertNotNull(
                    "${group.preferenceKey} has no PreferenceDefinition, so it never reaches the " +
                            "watch",
                    MiscPreferences.EXPORTABLE.firstOrNull { it.key == group.preferenceKey })
        }
    }
}
