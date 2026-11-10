package com.svartifoss.snfell.music

/**
 * Whether a track that arrived without a cover has gone without one long enough to look for it.
 *
 * [CoverFallback] exists for players that never publish a cover (YouTube, for a video or a live),
 * but plenty of players that do have one publish a new track *before* its cover: YouTube Music sent
 * a track with no bitmap and no art address, and its own cover 0.96 s later (2026-10-28). Asking at
 * once looked that cover up online on nearly every YouTube Music track change - a network request,
 * a YouTube Data API search out of a quota the whole app shares, and a rebuild when the answer
 * landed - for a picture the player was about to hand over anyway.
 *
 * So the lookup waits until the track has been without a cover for [graceMs] *continuously*. A
 * cover from the player in between resets the wait, which is what keeps a cover that flickers out
 * for a moment later in the track from being treated as missing all along.
 *
 * Pure and free of `android.*`; times are on the caller's monotonic clock.
 */
class CoverLookupGrace(private val graceMs: Long = GRACE_MS) {

    private var key: String? = null
    private var sinceMs = 0L

    /** The track [key] has no cover of its own at [nowMs]. Returns whether the wait is over. */
    fun withoutCover(key: String, nowMs: Long): Boolean {
        if (key != this.key) {
            this.key = key
            sinceMs = nowMs
        }
        return nowMs - sinceMs >= graceMs
    }

    /** When the wait for the track last reported without a cover ends, or null when there is none. */
    fun dueAtMs(): Long? = key?.let { sinceMs + graceMs }

    /** The player published a cover of its own: whatever was waiting is moot. */
    fun playerCover() {
        key = null
    }

    companion object {
        /** Twice the gap measured for YouTube Music. A player that never sends a cover pays it once
         *  per track, before a cover that was otherwise found at once. */
        const val GRACE_MS = 2_000L
    }
}
