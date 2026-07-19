package com.svartifoss.snfell.watch.view

import com.svartifoss.snfell.watch.view.face.resolveMetadataVisibility
import com.svartifoss.snfell.watch.view.face.shouldEnableCentralSeek
import com.svartifoss.snfell.watch.view.face.shouldKeepEdgeSeekView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerConfigurationTest {
    @Test
    fun titleAndArtistCanBeHiddenIndependently() {
        val titleHidden = resolveMetadataVisibility(
                title = "Track",
                artist = "Artist",
                showTitle = false,
                showArtist = true,
                titleIsStatus = false,
                artistIsStatus = false
        )
        assertFalse(titleHidden.title)
        assertTrue(titleHidden.artist)

        val artistHidden = resolveMetadataVisibility(
                title = "Track",
                artist = "Artist",
                showTitle = true,
                showArtist = false,
                titleIsStatus = false,
                artistIsStatus = false
        )
        assertTrue(artistHidden.title)
        assertFalse(artistHidden.artist)
    }

    @Test
    fun errorAndPlaybackStatusRemainVisible() {
        val visibility = resolveMetadataVisibility(
                title = "Connection failed",
                artist = "Playback stopped",
                showTitle = false,
                showArtist = false,
                titleIsStatus = true,
                artistIsStatus = true
        )

        assertTrue(visibility.title)
        assertTrue(visibility.artist)
    }

    @Test
    fun centerAndEdgeScrubbersAreIndependent() {
        assertTrue(shouldEnableCentralSeek("central"))
        assertFalse(shouldEnableCentralSeek("none"))
        assertFalse(shouldEnableCentralSeek("edge"))
    }

    @Test
    fun hiddenEdgeArcKeepsItsIndependentSeekTarget() {
        assertTrue(shouldKeepEdgeSeekView(
                edgeProgressVisible = false, edgeSeekEnabled = true))
        assertTrue(shouldKeepEdgeSeekView(
                edgeProgressVisible = true, edgeSeekEnabled = false))
        assertTrue(shouldKeepEdgeSeekView(
                edgeProgressVisible = true, edgeSeekEnabled = true))
        assertFalse(shouldKeepEdgeSeekView(
                edgeProgressVisible = false, edgeSeekEnabled = false))
    }

    @Test
    fun miniButtonsAppearForPlayingOrPausedTracksButNotIdle() {
        assertTrue(hasActiveMiniButtons(configured = true, idle = false))
        assertFalse(hasActiveMiniButtons(configured = true, idle = true))
        assertFalse(hasActiveMiniButtons(configured = false, idle = false))

        assertTrue(shouldShowMiniButtons(
                configured = true, idle = false, ambient = false, overlayActive = false))
        assertFalse(shouldShowMiniButtons(
                configured = true, idle = true, ambient = false, overlayActive = false))
        assertFalse(shouldShowMiniButtons(
                configured = true, idle = false, ambient = true, overlayActive = false))
        assertFalse(shouldShowMiniButtons(
                configured = true, idle = false, ambient = false, overlayActive = true))
    }

    @Test
    fun hiddenProgressRingIsRevealedOnlyDuringAnActiveDrag() {
        assertFalse(shouldDrawEdgeProgress(
                seekable = true, visualEnabled = false, dragging = false))
        assertTrue(shouldDrawEdgeProgress(
                seekable = true, visualEnabled = false, dragging = true))
        assertTrue(shouldDrawEdgeProgress(
                seekable = true, visualEnabled = true, dragging = false))
        // An in-flight gesture is deliberately completed even if a stale playback tick briefly
        // revokes seekability; onTouchEvent follows the same contract.
        assertTrue(shouldDrawEdgeProgress(
                seekable = false, visualEnabled = true, dragging = true))
        assertFalse(shouldDrawEdgeProgress(
                seekable = false, visualEnabled = true, dragging = false))
    }

    @Test
    fun clockProgressStylesRoundTripFromPreferences() {
        assertEquals(RingStyle.WATCH_DOTS_60, RingStyle.fromPref("watch_dots_60"))
        assertEquals(RingStyle.WATCH_TICKS_60, RingStyle.fromPref("watch_ticks_60"))
        assertEquals(RingStyle.HOUR_SEGMENTS_12, RingStyle.fromPref("hour_segments_12"))
        assertEquals(RingStyle.SOLID, RingStyle.fromPref("unknown"))
    }

    @Test
    fun volumeLayoutsRoundTripFromPreferences() {
        assertEquals(VolumeLayout.EDGE, VolumeLayout.fromPref("edge"))
        assertEquals(VolumeLayout.HALO, VolumeLayout.fromPref("halo"))
        assertEquals(VolumeLayout.METER, VolumeLayout.fromPref("meter"))
        assertEquals(VolumeLayout.EDGE, VolumeLayout.fromPref("unknown"))
    }
}
