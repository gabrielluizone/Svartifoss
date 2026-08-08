package com.svartifoss.snfell.music

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionCapabilitiesTest {
    @Test
    fun `an advertised action is worth issuing`() {
        val actions = PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or PlaybackStateCompat.ACTION_PLAY
        assertTrue(MediaSessionCapabilities.advertisesPlayFromSearch(actions))
    }

    @Test
    fun `an action the session omits is not`() {
        // Retro Music's real playback session: seven transport actions and nothing else.
        val retroMusic = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
        assertFalse(MediaSessionCapabilities.advertisesPlayFromSearch(retroMusic))
        assertTrue(MediaSessionCapabilities.advertises(
                retroMusic, PlaybackStateCompat.ACTION_SEEK_TO))
    }

    /**
     * The rule the whole wake-then-play chain rests on: a session that has published no state yet
     * has not refused anything. Reading that as "unsupported" would kill the command at exactly the
     * moment it is most needed - against an app that was just bound and is still starting.
     */
    @Test
    fun `no published state means try anyway`() {
        assertTrue(MediaSessionCapabilities.advertisesPlayFromSearch(null))
        assertTrue(MediaSessionCapabilities.advertises(null, PlaybackStateCompat.ACTION_PLAY_FROM_URI))
    }

    @Test
    fun `an empty action set is a real refusal, unlike a missing one`() {
        assertFalse(MediaSessionCapabilities.advertisesPlayFromSearch(0L))
    }
}
