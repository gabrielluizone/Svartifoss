package com.svartifoss.snfell.config

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigSyncQueueTest {
    @Test
    fun `overlapping edits send latest once and then stop`() = runTest {
        val inFlight = CompletableDeferred<Unit>()
        var current = 1
        val sent = mutableListOf<Int>()
        val queue = ConfigSyncQueue(backgroundScope, { throw it }) {
            val snapshot = current
            sent += snapshot
            if (snapshot == 1) inFlight.await()
        }
        queue.request(true)
        runCurrent()
        for (edit in 2..5) {
            current = edit
            queue.request(true)
        }
        assertEquals(listOf(1), sent)
        inFlight.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 5), sent)
        current = 6
        queue.request(true)
        runCurrent()
        assertEquals(listOf(1, 5, 6), sent)
    }

    @Test
    fun `glyph retransmit cannot overtake a commit or erase its pending disk save`() = runTest {
        val inFlight = CompletableDeferred<Unit>()
        val saves = mutableListOf<Boolean>()
        val queue = ConfigSyncQueue(backgroundScope, { throw it }) { save ->
            saves += save
            if (saves.size == 1) inFlight.await()
        }
        queue.request(false)
        runCurrent()
        queue.request(true)
        queue.request(false)
        queue.request(false)
        assertEquals(listOf(false), saves)
        inFlight.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true), saves)
        queue.request(false)
        runCurrent()
        assertEquals(listOf(false, true, false), saves)
    }

    @Test
    fun `failed write does not strand queued edits or future requests`() = runTest {
        val inFlight = CompletableDeferred<Unit>()
        var attempts = 0
        var failures = 0
        val queue = ConfigSyncQueue(backgroundScope, { failures++ }) {
            attempts++
            if (attempts == 1) {
                inFlight.await()
                error("Data Layer unavailable")
            }
        }
        queue.request(true)
        runCurrent()
        queue.request(true)
        inFlight.complete(Unit)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(1, failures)
        queue.request(true)
        runCurrent()
        assertEquals(3, attempts)
    }
}
