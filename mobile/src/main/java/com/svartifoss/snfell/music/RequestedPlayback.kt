package com.svartifoss.snfell.music

import kotlinx.coroutines.delay

internal data class PlaybackObservation(
        val active: Boolean,
        val identity: List<String?>,
        val positionMs: Long
)

/** A previously playing track is not evidence that a new command was accepted. */
internal suspend fun awaitRequestedPlayback(
        read: () -> PlaybackObservation,
        issue: () -> Unit,
        timeoutMs: Long = 5000,
        pollMs: Long = 200,
        retryMs: Long = 700
): Boolean {
    val before = read()
    var sawInactive = !before.active
    var waited = 0L
    var nextCommand = retryMs
    issue()
    while (waited < timeoutMs) {
        val pause = minOf(pollMs, timeoutMs - waited)
        delay(pause)
        waited += pause
        val after = read()
        sawInactive = sawInactive || !after.active
        val restarted = before.positionMs > 2000 && after.positionMs in 0..1000
        if (after.active && (sawInactive || after.identity != before.identity || restarted)) return true
        if (waited >= nextCommand && waited < timeoutMs) {
            issue()
            nextCommand = waited + retryMs
        }
    }
    return false
}
