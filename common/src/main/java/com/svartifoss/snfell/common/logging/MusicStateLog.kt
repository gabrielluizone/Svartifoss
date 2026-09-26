package com.svartifoss.snfell.common.logging

import com.svartifoss.snfell.proto.MusicState

/**
 * A one-line account of this state for the logs, or "none".
 *
 * The phone logs every state it sends and the watch logs every state it receives (twice), and all
 * three used to pass the protobuf itself - whose `toString()` prints every field, including the
 * PNG bytes of each notification action icon, escaped as text. That is tens of kilobytes of
 * formatting per state change on each device, written to a log file that rotates every thirty
 * kilobytes: most of what the file logger kept was icon bytes, and the rotation it forced pushed
 * out the lines that explain a support report.
 *
 * Carries what those lines were actually read for - which track, its playback state and position,
 * the revision, and whether art or actions came with it.
 */
fun MusicState?.logSummary(): String {
    if (this == null) return "none"
    return buildString {
        append('\'').append(title).append("' by '").append(artist).append('\'')
        if (error) append(" ERROR")
        append(if (playing) " playing" else " paused")
        append(" at ").append(positionMs).append('/').append(durationMs).append("ms")
        append(" vol=").append(volume)
        append(" seq=").append(seq)
        if (albumArtPending) append(" art-pending")
        if (hasBackdropArt) append(" backdrop")
        if (mediaActionsCount > 0) append(" actions=").append(mediaActionsCount)
    }
}
