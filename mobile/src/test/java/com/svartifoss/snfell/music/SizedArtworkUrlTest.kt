package com.svartifoss.snfell.music

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [QueueArtworkResolver.sizedArtworkUrl].
 *
 * The bug behind it: YouTube-Music-style clients publish a *list-sized* cover URL for queue
 * entries - sized for the phone's own compact rows, often 60 or 120px. Downloaded verbatim and
 * then stretched across the watch's Cover-style pill, those came out visibly pixelated, and
 * nothing later in the chain can rescue them (`shrinkPreservingRatio` only ever shrinks).
 */
class SizedArtworkUrlTest {

    @Test
    fun googleImageHostSizeTokensAreRaisedToTheRequestedSize() {
        assertEquals(
                "https://lh3.googleusercontent.com/abc123=w320-h320-l90-rj",
                QueueArtworkResolver.sizedArtworkUrl(
                        "https://lh3.googleusercontent.com/abc123=w60-h60-l90-rj", 320))
    }

    @Test
    fun nonSizeOptionsArePreservedInPlace() {
        // The trailing options carry crop/quality/format; losing them can change what comes back.
        val result = QueueArtworkResolver.sizedArtworkUrl(
                "https://lh3.googleusercontent.com/abc=w60-h60-p-l90-rj", 96)
        assertEquals("https://lh3.googleusercontent.com/abc=w96-h96-p-l90-rj", result)
    }

    @Test
    fun squareSizeTokenIsAlsoRewritten() {
        assertEquals(
                "https://lh3.googleusercontent.com/abc=s320",
                QueueArtworkResolver.sizedArtworkUrl("https://lh3.googleusercontent.com/abc=s90", 320))
    }

    @Test
    fun aQueryStringIsKeptAndNotMistakenForSizeOptions() {
        assertEquals(
                "https://lh3.googleusercontent.com/abc=w320-h320?foo=bar",
                QueueArtworkResolver.sizedArtworkUrl(
                        "https://lh3.googleusercontent.com/abc=w60-h60?foo=bar", 320))
    }

    @Test
    fun anUnrelatedHostIsLeftCompletelyAlone() {
        // Guessing at a size parameter for a host we do not know would break the URL outright.
        val other = "https://example.com/cover.jpg?size=60"
        assertEquals(other, QueueArtworkResolver.sizedArtworkUrl(other, 320))
    }

    @Test
    fun aGoogleUrlWithNoSizeOptionsIsUnchanged() {
        val plain = "https://lh3.googleusercontent.com/abc123"
        assertEquals(plain, QueueArtworkResolver.sizedArtworkUrl(plain, 320))
    }

    @Test
    fun youtubeStaticThumbnailsAreUpgradedByFilename() {
        assertEquals(
                "https://i.ytimg.com/vi/VIDEOID/hqdefault.jpg",
                QueueArtworkResolver.sizedArtworkUrl(
                        "https://i.ytimg.com/vi/VIDEOID/default.jpg", 480))
        assertEquals(
                "https://i.ytimg.com/vi/VIDEOID/mqdefault.jpg",
                QueueArtworkResolver.sizedArtworkUrl(
                        "https://i.ytimg.com/vi/VIDEOID/default.jpg", 320))
    }

    @Test
    fun youtubeThumbnailsAreNeverDowngraded() {
        // A large source can always be shrunk; detail lost by asking for a smaller file cannot be
        // recovered, so a small request must not replace an already-larger filename.
        val big = "https://i.ytimg.com/vi/VIDEOID/hqdefault.jpg"
        assertEquals(big, QueueArtworkResolver.sizedArtworkUrl(big, 96))
    }

    @Test
    fun theDefaultTargetCoversTheLargestThumbnailTheQueueSends() {
        // 320 is what a Cover queue style asks for; a smaller default would silently under-fetch.
        assertEquals(320, QueueArtworkResolver.DEFAULT_TARGET_PX)
    }
}
