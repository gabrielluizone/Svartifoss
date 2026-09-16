package com.svartifoss.snfell.actions.playback

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPresetPolicyTest {
    @Test
    fun repeatOneKeepsItsNumberedIconWhenTransferredToTheWatch() {
        assertEquals(com.svartifoss.snfell.common.R.drawable.action_repeat_one,
                repeatModeIcon(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals(com.svartifoss.snfell.common.R.drawable.action_repeat,
                repeatModeIcon(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals(com.svartifoss.snfell.common.R.drawable.action_repeat,
                repeatModeIcon(PlaybackStateCompat.REPEAT_MODE_NONE))
        // The watch holds one vector per action key, so the bitmap only has to travel for the mode
        // whose icon is not that vector. Asking by key alone used to answer "never local" for all
        // three, which sent two identical copies of the drawable the watch already had - and made
        // the resend check ask every launch for assets that were correctly absent.
        val key = com.svartifoss.snfell.common.actions.StandardActions.ACTION_SET_REPEAT_MODE
        val icons = com.svartifoss.snfell.common.actions.StandardIcons
        org.junit.Assert.assertFalse("repeat-one must travel: its glyph is not the key's vector",
                icons.canUseLocalIcon(key, repeatModeIcon(PlaybackStateCompat.REPEAT_MODE_ONE)))
        org.junit.Assert.assertTrue(
                icons.canUseLocalIcon(key, repeatModeIcon(PlaybackStateCompat.REPEAT_MODE_ALL)))
        org.junit.Assert.assertTrue(
                icons.canUseLocalIcon(key, repeatModeIcon(PlaybackStateCompat.REPEAT_MODE_NONE)))
        // An action with no opinion keeps the optimisation it always had.
        org.junit.Assert.assertTrue(icons.canUseLocalIcon(
                com.svartifoss.snfell.common.actions.StandardActions.ACTION_PLAY))
    }

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
