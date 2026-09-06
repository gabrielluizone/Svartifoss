package com.svartifoss.snfell

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceSnapshotDeliveryTest {
    @Test
    fun `slow durable write keeps only final theme while messages continue`() = runTest {
        val firstPut = CompletableDeferred<Unit>()
        val messages = mutableListOf<Int>()
        val puts = mutableListOf<Int>()
        val delivery = PreferenceSnapshotDelivery<Int>(backgroundScope,
                sendMessage = { messages += it },
                putData = {
                    puts += it
                    if (it == 1) firstPut.await()
                })

        delivery.offer(1)
        runCurrent()
        for (theme in 2..5) {
            delivery.offer(theme)
            runCurrent()
        }
        assertEquals((1..5).toList(), messages)
        assertEquals(listOf(1), puts)

        firstPut.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 5), puts)
    }

    @Test
    fun `slow message keeps only final theme without delaying durability`() = runTest {
        val firstMessage = CompletableDeferred<Unit>()
        val messages = mutableListOf<Int>()
        val puts = mutableListOf<Int>()
        val delivery = PreferenceSnapshotDelivery<Int>(backgroundScope,
                sendMessage = {
                    messages += it
                    if (it == 1) firstMessage.await()
                },
                putData = { puts += it })

        delivery.offer(1)
        runCurrent()
        for (theme in 2..5) {
            delivery.offer(theme)
            runCurrent()
        }
        assertEquals(listOf(1), messages)
        assertEquals((1..5).toList(), puts)

        firstMessage.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 5), messages)
    }

    @Test
    fun `retrying durability does not resend immediate message`() = runTest {
        val messages = mutableListOf<Int>()
        val puts = mutableListOf<Int>()
        val delivery = PreferenceSnapshotDelivery<Int>(backgroundScope,
                sendMessage = { messages += it }, putData = { puts += it })
        delivery.offer(5)
        runCurrent()
        delivery.retryData(5)
        runCurrent()

        assertEquals(listOf(5), messages)
        assertEquals(listOf(5, 5), puts)
    }

    @Test
    fun `stopping drops pending snapshots but lets started put finish`() = runTest {
        val firstPut = CompletableDeferred<Unit>()
        val finished = mutableListOf<Int>()
        val delivery = PreferenceSnapshotDelivery<Int>(backgroundScope,
                sendMessage = {}, putData = {
                    firstPut.await()
                    finished += it
                })
        delivery.offer(1)
        runCurrent()
        delivery.offer(2)
        delivery.stop()
        firstPut.complete(Unit)
        runCurrent()
        assertEquals(listOf(1), finished)
    }
}
