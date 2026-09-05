package com.svartifoss.snfell.actions.playback

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPresetPolicyTest {
    @Test
    fun playbackSpeedIsFiniteAndClampedToSupportedPickerRange() {
        assertEquals(0.5f, normalizePlaybackSpeed(-4f))
        assertEquals(2f, normalizePlaybackSpeed(8f))
        assertEquals(1f, normalizePlaybackSpeed(Float.NaN))
        assertEquals(1.25f, normalizePlaybackSpeed(1.25f))
    }

    @Test
    fun playbackSpeedTitleDoesNotExposeFloatNoise() {
        assertEquals("1", formatPlaybackSpeed(1f))
        assertEquals("1.25", formatPlaybackSpeed(1.25f))
        assertEquals("0.5", formatPlaybackSpeed(0.5f))
    }

    @Test
    fun seekPercentClampsAndUsesKnownDuration() {
        assertEquals(0, normalizePercent(-1))
        assertEquals(100, normalizePercent(101))
        assertEquals(45_000L, seekPositionForPercent(180_000L, 25))
        assertEquals(180_000L, seekPositionForPercent(180_000L, 200))
        assertNull(seekPositionForPercent(0L, 50))
    }

    @Test
    fun unknownRepeatModeFailsSafeToOff() {
        assertEquals(
                PlaybackStateCompat.REPEAT_MODE_ONE,
                normalizeRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, normalizeRepeatMode(999))
    }
}
