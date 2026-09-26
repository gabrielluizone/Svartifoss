package com.svartifoss.snfell.music

import kotlin.math.abs

/**
 * Whether a playback-state update from the *playing* session is worth passing on to the watch.
 *
 * The provider re-resolves which session to report whenever playback starts or stops, and that
 * always reports. What it never did was report a change *within* playing: its callback on the
 * playing session only asked "is this still the one playing?", and when the answer was yes it said
 * nothing. So a seek made on the phone, a playback-speed change, a like toggled in the player (the
 * player's custom actions) or a jump within its queue reached the watch only when something else
 * happened to cause a retransmission - the position was corrected by the watch's own periodic check
 * a minute later at best, and the rest waited for the next track.
 *
 * Passing every update on would fix that and cost a great deal instead: plenty of players publish
 * a fresh playback state every second while playing, each re-report rebuilds the whole state on
 * the phone, and rebuilding reads the session's metadata - cover bitmap included - across the
 * binder. So an update is reported only when it says something the last one did not, measured the
 * way the phone's own retransmission dedupe measures it: a position that has moved by more than
 * [SEEK_THRESHOLD_MS] from where the previous sample said it would be, or a change to speed, the
 * advertised actions, the active queue entry or the custom actions.
 *
 * Pure and free of `android.*`; the provider turns a `PlaybackState` into a [Snapshot].
 */
object PlaybackReportPolicy {

    /** Matches `MusicService.SEEK_DETECTION_THRESHOLD_MS`: a smaller jump is the player's own
     *  jitter, not a seek, and the phone would not retransmit for it anyway. */
    const val SEEK_THRESHOLD_MS = 1_500L

    /** The parts of a playback state this policy compares. */
    data class Snapshot(
            val playing: Boolean,
            val positionMs: Long,
            /** The sample's own timestamp, on the phone's monotonic clock. */
            val positionUpdateTimeMs: Long,
            val speed: Float,
            val actions: Long,
            val activeQueueItemId: Long,
            /** Identity of each custom action - its id, label and icon - in order. */
            val customActions: List<String>
    )

    fun worthReporting(previous: Snapshot?, current: Snapshot): Boolean {
        // Nothing to compare against means the session was only just resolved, and resolving
        // reports it - see ActiveMediaSessionProvider.
        if (previous == null) return false
        if (current.playing != previous.playing) return true
        if (current.speed != previous.speed) return true
        if (current.actions != previous.actions) return true
        if (current.activeQueueItemId != previous.activeQueueItemId) return true
        if (current.customActions != previous.customActions) return true

        val elapsed = current.positionUpdateTimeMs - previous.positionUpdateTimeMs
        val expected = if (previous.playing) {
            previous.positionMs + (elapsed * previous.speed).toLong()
        } else {
            previous.positionMs
        }
        return abs(current.positionMs - expected) > SEEK_THRESHOLD_MS
    }
}
