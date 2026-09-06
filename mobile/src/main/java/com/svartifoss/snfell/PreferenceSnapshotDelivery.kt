package com.svartifoss.snfell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Two independent, serial transports for full preference snapshots. Each keeps at most one
 * pending value. A slow durable put must not hold up the next immediate message (or vice versa).
 * Started sends are allowed to finish: cancelling Task.await does not cancel a Data Layer put.
 * Transport callbacks own error handling so a failed send does not terminate their worker.
 */
internal class PreferenceSnapshotDelivery<T>(
        scope: CoroutineScope,
        sendMessage: suspend (T) -> Unit,
        putData: suspend (T) -> Unit
) {
    private val messages = Channel<T>(Channel.CONFLATED)
    private val data = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch { for (snapshot in messages) sendMessage(snapshot) }
        scope.launch { for (snapshot in data) putData(snapshot) }
    }

    fun offer(snapshot: T) {
        messages.trySend(snapshot)
        data.trySend(snapshot)
    }

    fun retryData(snapshot: T) {
        data.trySend(snapshot)
    }

    fun stop() {
        messages.cancel()
        data.cancel()
    }
}
