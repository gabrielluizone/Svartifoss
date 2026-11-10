package com.svartifoss.snfell.music

/**
 * How often the phone may send the music state to the watch, and what happens to a state that
 * comes too soon.
 *
 * The state is rebuilt on every callback a player makes and goes out whenever it says something
 * new, and a track change is a burst of callbacks. In the evening measured for
 * `docs/watch-sync-battery-investigation.md`, YouTube Music produced up to fifteen states in three
 * and a half seconds: the old track ending, the new one carrying the old one's position, its cover,
 * its length, the backdrop lookup landing, the like state. Each was a full state crossing Bluetooth
 * as a message and again as a DataItem, and most were superseded before the watch could draw them.
 *
 * Two rules. Both only ever *delay* a state, and the caller re-reads the session when the wait is
 * over, so what goes out then is the newest state, never a stale one:
 * - **Spacing.** A state arriving within [minIntervalMs] of the last one sent waits until that
 *   interval is over. The first state of a burst therefore still goes at once - a track change
 *   reaches the wrist as fast as it did - and the rest of the burst collapses into one.
 * - **Budget.** A bucket of [budget] transmissions, refilled at one per [refillMs], bounds what any
 *   sustained cause can cost. Nothing legitimate comes near it; it exists because the cause of the
 *   25-minute storm in that investigation was never reproduced, and a storm that empties the
 *   bucket is slowed to the refill rate and logged by the caller instead of showing up only in
 *   the battery.
 *
 * Pure and free of `android.*`; times are on the caller's monotonic clock.
 */
class MusicTransmitPacer(
        private val minIntervalMs: Long = MIN_INTERVAL_MS,
        private val budget: Int = BUDGET,
        private val refillMs: Long = REFILL_MS
) {

    /** What to do with a state that is worth sending. */
    sealed class Decision {
        /** Send it now, and call [sent]. */
        object Send : Decision()

        /** Read the session again at [atMs] and send what it says by then. [overBudget] is set
         *  when the budget, not the spacing, is what holds it back. */
        data class Wait(val atMs: Long, val overBudget: Boolean) : Decision()
    }

    private var lastSentMs: Long? = null
    private var tokens = budget.toDouble()
    private var tokensAtMs: Long? = null

    fun decide(nowMs: Long): Decision {
        refill(nowMs)
        val spacingEnd = lastSentMs?.let { it + minIntervalMs } ?: nowMs
        val budgetEnd = if (tokens >= 1.0) nowMs else nowMs + ((1.0 - tokens) * refillMs).toLong()
        val atMs = maxOf(spacingEnd, budgetEnd)
        return if (atMs <= nowMs) Decision.Send else Decision.Wait(atMs, budgetEnd > spacingEnd)
    }

    /** Records a transmission made at [nowMs]. */
    fun sent(nowMs: Long) {
        refill(nowMs)
        lastSentMs = nowMs
        tokens = (tokens - 1.0).coerceAtLeast(0.0)
    }

    private fun refill(nowMs: Long) {
        val since = tokensAtMs
        tokensAtMs = nowMs
        if (since == null) return
        val elapsed = (nowMs - since).coerceAtLeast(0L)
        tokens = minOf(budget.toDouble(), tokens + elapsed.toDouble() / refillMs)
    }

    companion object {
        /** Long enough to cover the part of a track change that arrives together - YouTube Music's
         *  old-track pause, new track, stale position and cover landed within one second - and short
         *  enough that a second change the user makes on purpose is not noticeably late. */
        const val MIN_INTERVAL_MS = 1_000L

        /** About fifteen track changes back to back before anything is slowed. */
        const val BUDGET = 30

        /** Fifteen a minute, sustained: ten times what continuous playback needs. */
        const val REFILL_MS = 4_000L
    }
}
