package com.svartifoss.snfell.music

import android.media.AudioManager
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.util.FloatPacker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class DirectTransportCommandTest {
    @Test
    fun theTransportMessagesCarryNoPayloadAndAreRecognised() {
        assertEquals(DirectTransportCommand.TogglePlayPause,
                DirectTransportCommands.fromMessage(CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE, null))
        assertEquals(DirectTransportCommand.SkipNext,
                DirectTransportCommands.fromMessage(CommPaths.MESSAGE_SKIP_NEXT, ByteArray(0)))
        assertEquals(DirectTransportCommand.SkipPrevious,
                DirectTransportCommands.fromMessage(CommPaths.MESSAGE_SKIP_PREVIOUS, null))
    }

    @Test
    fun aVolumeStepKeepsItsDirectionAndRefusesAnythingElse() {
        fun adjust(direction: Int?) = DirectTransportCommands.fromMessage(
                CommPaths.MESSAGE_ADJUST_VOLUME,
                direction?.let { ByteBuffer.allocate(4).putInt(it).array() })

        assertEquals(DirectTransportCommand.AdjustVolume(AudioManager.ADJUST_RAISE),
                adjust(AudioManager.ADJUST_RAISE))
        assertEquals(DirectTransportCommand.AdjustVolume(AudioManager.ADJUST_LOWER),
                adjust(AudioManager.ADJUST_LOWER))
        // ADJUST_SAME, mute and the rest: the service refuses these too, so they are not ours.
        assertNull(adjust(0))
        assertNull(adjust(AudioManager.ADJUST_MUTE))
        assertNull(adjust(null))
        assertNull(DirectTransportCommands.fromMessage(
                CommPaths.MESSAGE_ADJUST_VOLUME, ByteArray(2)))
    }

    @Test
    fun anAbsoluteVolumeIsAFractionClampedIntoRange() {
        fun set(value: Float) = DirectTransportCommands.fromMessage(
                CommPaths.MESSAGE_CHANGE_VOLUME, FloatPacker.packFloat(value))

        assertEquals(DirectTransportCommand.SetVolume(0.4f), set(0.4f))
        assertEquals(DirectTransportCommand.SetVolume(1f), set(1.7f))
        assertEquals(DirectTransportCommand.SetVolume(0f), set(-0.2f))
        assertNull(set(Float.NaN))
        assertNull(DirectTransportCommands.fromMessage(CommPaths.MESSAGE_CHANGE_VOLUME, null))
        assertNull(DirectTransportCommands.fromMessage(CommPaths.MESSAGE_CHANGE_VOLUME, ByteArray(3)))
    }

    @Test
    fun everythingThatNeedsTheServiceStaysWithTheService() {
        // A button arrives as EXECUTE_ACTION and is resolved from the phone's configuration, so
        // the message alone never decides it. The rest is state only the service holds.
        listOf(
                CommPaths.MESSAGE_EXECUTE_ACTION,
                CommPaths.MESSAGE_EXECUTE_MENU_ACTION,
                CommPaths.MESSAGE_SEEK_TO,
                CommPaths.MESSAGE_SEEK_RELATIVE,
                CommPaths.MESSAGE_QUICK_ACTION,
                CommPaths.MESSAGE_OPEN_PLAYBACK_QUEUE,
                CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED,
                CommPaths.MESSAGE_PLAY_FROM_SEARCH,
                CommPaths.MESSAGE_WATCH_OPENED,
                CommPaths.MESSAGE_WATCH_CLOSED,
                CommPaths.MESSAGE_ACK
        ).forEach { path ->
            assertNull(path, DirectTransportCommands.fromMessage(path, ByteArray(8)))
        }
    }

    @Test
    fun theTargetIsThePlayingSessionOtherwiseTheMostRecentOne() {
        // Sessions arrive most recently active first, the order media keys are routed in.
        val sessions = listOf("paused-recent", "playing", "paused-old")

        assertEquals("playing", DirectTransportTarget.pick(sessions) { it == "playing" })
        assertEquals("paused-recent", DirectTransportTarget.pick(sessions) { false })
        assertNull(DirectTransportTarget.pick(emptyList<String>()) { true })
    }

    @Test
    fun theFirstOfSeveralPlayingSessionsWins() {
        val sessions = listOf("a", "b", "c")
        assertEquals("b", DirectTransportTarget.pick(sessions) { it != "a" })
    }

    @Test
    fun serviceWakeAttemptsAreSpacedOut() {
        var now = 1_000L
        val throttle = ServiceWakeThrottle(minIntervalMs = 3_000L) { now }

        assertTrue(throttle.tryAcquire())
        now += 2_999L
        assertFalse(throttle.tryAcquire())
        now += 1L
        assertTrue(throttle.tryAcquire())
        // A refused attempt does not push the next one out: only a real attempt resets the clock.
        now += 100L
        assertFalse(throttle.tryAcquire())
        now += 2_900L
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun theFirstWakeIsNeverSuppressedEvenWhenTheClockStartsAtZero() {
        var now = 0L
        val throttle = ServiceWakeThrottle(minIntervalMs = 3_000L) { now }
        assertTrue(throttle.tryAcquire())
        now = 10L
        assertFalse(throttle.tryAcquire())
    }
}
