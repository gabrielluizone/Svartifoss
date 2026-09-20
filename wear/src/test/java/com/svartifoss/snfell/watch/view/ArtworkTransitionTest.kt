package com.svartifoss.snfell.watch.view

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkTransitionTest {

    private fun transition(
            fadeEnabled: Boolean = true,
            ambient: Boolean = false,
            hasArtwork: Boolean = true,
            hadArtwork: Boolean = true,
            samePixels: Boolean = false
    ) = artworkTransition(fadeEnabled, ambient, hasArtwork, hadArtwork, samePixels)

    /**
     * The reported bug: on a cold open the cover appeared at full opacity a moment before the
     * controls, because the animation was gated on there already being a cover to replace.
     */
    @Test
    fun `the first cover of the session is revealed rather than snapped on`() {
        assertEquals(ArtworkTransition.REVEAL, transition(hadArtwork = false))
    }

    @Test
    fun `a replacement cover cross-fades`() {
        assertEquals(ArtworkTransition.CROSSFADE, transition(hadArtwork = true))
    }

    /** Artwork returning after none is a first cover again, not a replacement of nothing. */
    @Test
    fun `artwork reappearing after none is revealed`() {
        assertEquals(ArtworkTransition.REVEAL, transition(hadArtwork = false, hasArtwork = true))
    }

    @Test
    fun `clearing the artwork animates nothing`() {
        assertEquals(ArtworkTransition.IMMEDIATE, transition(hasArtwork = false))
        assertEquals(
                ArtworkTransition.IMMEDIATE,
                transition(hasArtwork = false, hadArtwork = false))
    }

    /**
     * A play/pause re-sync re-delivers the current cover as a fresh Bitmap. Animating those is what
     * made the artwork blink on every pause, and it must stay uninteresting whether or not there
     * was a previous cover.
     */
    @Test
    fun `the same cover delivered again animates nothing`() {
        assertEquals(ArtworkTransition.IMMEDIATE, transition(samePixels = true))
        assertEquals(
                ArtworkTransition.IMMEDIATE,
                transition(samePixels = true, hadArtwork = false))
    }

    @Test
    fun `the always-on screen is never handed a transition`() {
        // Ambient frames are drawn rarely, so an animation there does not run - it freezes
        // part-way, and what it freezes is a half-transparent cover.
        for (hadArtwork in listOf(true, false)) {
            assertEquals(
                    "hadArtwork=$hadArtwork",
                    ArtworkTransition.IMMEDIATE,
                    transition(ambient = true, hadArtwork = hadArtwork))
        }
    }

    @Test
    fun `turning the fade off turns both animations off`() {
        // One preference, both directions: a user who does not want the cover animated does not
        // want it animated on the first track either.
        assertEquals(ArtworkTransition.IMMEDIATE, transition(fadeEnabled = false))
        assertEquals(
                ArtworkTransition.IMMEDIATE,
                transition(fadeEnabled = false, hadArtwork = false))
    }
}
