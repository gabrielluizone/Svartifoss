package com.svartifoss.snfell.config

import com.svartifoss.snfell.common.actions.StandardActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which config entries on the watch are re-sent so it learns how far a skip moves playback.
 *
 * A config is pushed when it is edited, not when the app is updated, so without this an assignment
 * made before the offset existed would keep drawing its presses only after the round trip until the
 * user happened to touch that config again.
 */
class ActionSeekOffsetTransmissionTest {

    @Test
    fun `a skip or reverse by seconds without its offset is sent again`() {
        assertTrue(lacksSeekOffset(StandardActions.ACTION_SKIP_30_SECONDS, hasSeekOffset = false))
        assertTrue(lacksSeekOffset(StandardActions.ACTION_REVERSE_30_SECONDS, hasSeekOffset = false))
    }

    @Test
    fun `one that already carries its offset is left alone`() {
        assertFalse(lacksSeekOffset(StandardActions.ACTION_SKIP_30_SECONDS, hasSeekOffset = true))
        assertFalse(lacksSeekOffset(StandardActions.ACTION_REVERSE_30_SECONDS, hasSeekOffset = true))
    }

    @Test
    fun `actions that move playback by an unknown amount never ask for one`() {
        // Re-sending for these would push the whole config on every start, for a field the
        // phone never fills in for them.
        listOf(
                StandardActions.ACTION_PLAY_PAUSE,
                StandardActions.ACTION_FAST_FORWARD,
                StandardActions.ACTION_REWIND,
                StandardActions.ACTION_SEEK_TO_PERCENT,
                StandardActions.ACTION_RESTART
        ).forEach { key ->
            assertFalse(key, lacksSeekOffset(key, hasSeekOffset = false))
        }
    }
}
