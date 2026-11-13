package com.svartifoss.snfell.music

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RequestedPlaybackTest {
    private val playing = PlaybackObservation(true, listOf("old"), 10000)

    @Test fun alreadyPlayingDoesNotReportSuccessOrSkipTheCommand() = runTest {
        var commands = 0
        assertFalse(awaitRequestedPlayback({ playing }, { commands++ }))
        assertTrue(commands > 0)
    }

    @Test fun aDifferentTrackAfterTheCommandConfirmsPlayback() = runTest {
        var observation = playing
        var commands = 0
        assertTrue(awaitRequestedPlayback({ observation }, {
            commands++
            observation = playing.copy(identity = listOf("new"))
        }))
        assertEquals(1, commands)
    }

    @Test fun resumingTheSamePausedTrackIsSuccess() = runTest {
        var observation = playing.copy(active = false)
        assertTrue(awaitRequestedPlayback({ observation }, { observation = playing }))
    }

    @Test fun restartingTheRequestedTrackIsSuccess() = runTest {
        var observation = playing
        assertTrue(awaitRequestedPlayback({ observation }, { observation = playing.copy(positionMs = 0) }))
    }

    @Test fun cancellationStopsRetriesWithoutReportingSuccess() = runTest {
        var commands = 0
        val job = launch { awaitRequestedPlayback({ playing }, { commands++ }) }
        runCurrent()
        assertEquals(1, commands)
        job.cancel()
        job.join()
        assertEquals(1, commands)
    }

    private val idle = PlaybackObservation(active = false, identity = listOf("old"), positionMs = 0, playing = false)
    private val busy = PlaybackObservation(active = true, identity = listOf("old"), positionMs = 0, playing = false)

    @Test fun bufferingThatGivesUpIsNotPlayback() = runTest {
        // The app takes the address, buffers as if loading it, and gives up: nothing plays. Read
        // as success, that ended the shortcut with nothing playing and nothing opened.
        var observation = idle
        var readsSinceCommand = 0
        val played = awaitRequestedPlayback(
                read = {
                    if (observation.active && ++readsSinceCommand > 3) observation = idle
                    observation
                },
                issue = { observation = busy; readsSinceCommand = 0 })
        assertFalse(played)
    }

    @Test fun bufferingThatEndsInPlaybackIsSuccessAndIsNotInterrupted() = runTest {
        var observation = idle
        var reads = 0
        var commands = 0
        val played = awaitRequestedPlayback(
                read = {
                    if (observation.active && ++reads > 6) {
                        observation = PlaybackObservation(true, listOf("new"), 0, playing = true)
                    }
                    observation
                },
                issue = { commands++; observation = busy.copy(identity = listOf("new")) })
        assertTrue(played)
        // Six readings are 1.2 s, past the 700 ms after which an idle session is asked again. A
        // busy one is not: the command a second time would restart what it is loading.
        assertEquals(1, commands)
    }

    @Test fun stillBufferingWhenTheWaitRunsOutCountsAsASlowStart() = runTest {
        var observation = idle
        var commands = 0
        val played = awaitRequestedPlayback(
                read = { observation },
                issue = { commands++; observation = busy.copy(identity = listOf("new")) })
        assertTrue(played)
        assertEquals(1, commands)
    }

    @Test fun comingUpOnTheOldTrackIsNotPlayback() = runTest {
        // Connecting when the browser attaches, then paused on the track it had: the app coming
        // up, not the app obeying. It is asked again once it has settled.
        var reads = 0
        var commands = 0
        val played = awaitRequestedPlayback(
                read = { if (++reads <= 4) busy else idle },
                issue = { commands++ })
        assertFalse(played)
        assertTrue("asked again once idle, was asked $commands time(s)", commands > 1)
    }
}
