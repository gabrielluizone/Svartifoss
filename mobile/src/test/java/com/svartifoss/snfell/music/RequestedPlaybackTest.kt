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
}
