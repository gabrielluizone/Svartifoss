package com.svartifoss.snfell.actions.playback

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repeat and shuffle buttons, pinned at the one value that broke them: -1.
 *
 * `MediaControllerCompat` answers -1 for both modes until the session hands it an extra binder,
 * and forever for a session that was never built on `MediaSessionCompat`. Every assertion about
 * INVALID below is a regression test for a button that did the opposite of what it said.
 */
class PlaybackModeCycleTest {

    @Test
    fun `the repeat cycle runs off, all, one, off`() {
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL,
                nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE,
                nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE,
                nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE))
    }

    /** Retro Music and others report GROUP where the rest report ALL; the cycle must not restart. */
    @Test
    fun `group advances like all`() {
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE,
                nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP))
    }

    @Test
    fun `an unknown repeat mode switches repeat on, never off`() {
        // The reported bug: INVALID fell down the else branch to NONE, so the button set repeat
        // off on every press. "Off -> off" is what a broken button looks like.
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL,
                nextRepeatMode(PlaybackStateCompat.REPEAT_MODE_INVALID))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, nextRepeatMode(999))
    }

    @Test
    fun `repeat one toggles both ways`() {
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE,
                toggledRepeatOneMode(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE,
                toggledRepeatOneMode(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE,
                toggledRepeatOneMode(PlaybackStateCompat.REPEAT_MODE_ALL))
        // Unknown turns it on - the same direction the cycle takes, and the reason this half of
        // the feature appeared to work while the other did not.
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE,
                toggledRepeatOneMode(PlaybackStateCompat.REPEAT_MODE_INVALID))
    }

    @Test
    fun `only an explicit mode counts as shuffling`() {
        assertTrue(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_ALL))
        assertTrue(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_GROUP))
        assertFalse(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_NONE))
        // The bug: `!= SHUFFLE_MODE_NONE` read unknown as on, so the toggle only ever turned
        // shuffle off and the watch drew the button permanently lit.
        assertFalse(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_INVALID))
        assertFalse(isShuffleOn(999))
    }

    @Test
    fun `an unknown shuffle mode switches shuffle on`() {
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_ALL,
                nextShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_INVALID))
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_ALL,
                nextShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE))
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_NONE,
                nextShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL))
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_NONE,
                nextShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_GROUP))
    }

    @Test
    fun `the watch is told none when the mode cannot be read`() {
        assertEquals(0, repeatModeCode(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals(0, repeatModeCode(PlaybackStateCompat.REPEAT_MODE_INVALID))
        assertEquals(1, repeatModeCode(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals(1, repeatModeCode(PlaybackStateCompat.REPEAT_MODE_GROUP))
        assertEquals(2, repeatModeCode(PlaybackStateCompat.REPEAT_MODE_ONE))
    }

    /**
     * A press must always ask for something the session can act on. Returning INVALID would make
     * `setRepeatMode(-1)` the command, which is neither a mode nor a no-op.
     */
    @Test
    fun `no cycle ever asks for an invalid mode`() {
        val repeatInputs = listOf(
                PlaybackStateCompat.REPEAT_MODE_INVALID, PlaybackStateCompat.REPEAT_MODE_NONE,
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_ONE,
                PlaybackStateCompat.REPEAT_MODE_GROUP, 999)
        for (input in repeatInputs) {
            assertTrue("nextRepeatMode($input)", nextRepeatMode(input) >= 0)
            assertTrue("toggledRepeatOneMode($input)", toggledRepeatOneMode(input) >= 0)
            assertTrue("nextShuffleMode($input)", nextShuffleMode(input) >= 0)
        }
    }
}
