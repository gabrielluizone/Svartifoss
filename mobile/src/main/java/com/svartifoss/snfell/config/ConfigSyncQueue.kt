package com.svartifoss.snfell.config

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Main-thread owner of a config's commits and retransmits. Requests mean "read current config",
 * never queued old snapshots. A retransmit cannot erase a pending disk save or overtake a commit.
 * The supplied scope must outlive editors; started Data Layer Tasks are allowed to complete.
 */
internal class ConfigSyncQueue(
        private val scope: CoroutineScope,
        private val onFailure: (Exception) -> Unit,
        private val synchronize: suspend (saveToDisk: Boolean) -> Unit
) {
    private var running = false
    private var requested = false
    private var saveRequested = false

    fun request(saveToDisk: Boolean) {
        requested = true
        saveRequested = saveRequested || saveToDisk
        if (running) return
        running = true
        scope.launch {
            try {
                while (requested) {
                    requested = false
                    val save = saveRequested
                    saveRequested = false
                    try {
                        synchronize(save)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onFailure(e)
                    }
                }
            } finally {
                running = false
            }
        }
    }
}
