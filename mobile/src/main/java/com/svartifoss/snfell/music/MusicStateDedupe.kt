package com.svartifoss.snfell.music

import com.svartifoss.snfell.proto.MediaAction
import com.svartifoss.snfell.proto.MusicState
import kotlin.math.abs

/**
 * Whether a freshly built [MusicState] says anything the last one sent to the watch did not, and if
 * it does, what.
 *
 * The phone rebuilds the state on every callback a player makes, roughly once a second while a
 * track plays, and transmits only what this finds new. So this comparison is what keeps steady
 * playback off the Bluetooth link, and when it stops holding, nothing shows it but the battery: on
 * 2026-10-28 one 25-minute window carried a full state about every 1.7 s (29.6 MB, see
 * `docs/watch-sync-battery-investigation.md`), and the log could only say *that* each one went
 * out. That is why the answer is the first difference, worded for the log, rather than a yes or no.
 *
 * Fields that change on every build without saying anything are not compared: `time`, `seq`,
 * `positionUpdateTime` and `positionAgeMs`. The position itself drifts forward on every reading, so
 * it is compared against where the previous state said playback would be by now.
 *
 * Pure and free of `android.*`.
 */
object MusicStateDedupe {

    /** A jump larger than this from where the previous state put playback is a seek or a restart;
     *  anything smaller is the player's own jitter. */
    const val SEEK_DETECTION_THRESHOLD_MS = 1_500L

    /** The first thing [current] says that [previous] did not, or null when it says nothing new. */
    fun firstDifference(previous: MusicState?, current: MusicState): String? {
        if (previous == null) return "no previous state"
        return changed("playing", previous.playing, current.playing)
                ?: changed("artist", previous.artist, current.artist)
                ?: changed("app", previous.sourceAppName, current.sourceAppName)
                ?: changed("title", previous.title, current.title)
                ?: changed("volume", previous.volume, current.volume)
                ?: changed("error", previous.error, current.error)
                ?: changed("duration", previous.durationMs, current.durationMs)
                ?: changed("seekable", previous.seekable, current.seekable)
                // Without these three the watch's quick-actions panel state rings stayed frozen:
                // a state change that only flipped shuffle/repeat/like looked "equal" here and was
                // never retransmitted (LikeAction.scheduleStateRefresh hit the same dedupe).
                ?: changed("shuffle", previous.shuffleEnabled, current.shuffleEnabled)
                ?: changed("repeat", previous.repeatMode, current.repeatMode)
                ?: changed("liked", previous.liked, current.liked)
                // The watch extrapolates the position at this rate between samples, so a speed
                // changed on the phone has to reach it rather than wait for a seek-sized drift.
                ?: changed("speed", previous.playbackSpeed, current.playbackSpeed)
                // The Artist face draws its backdrop from these, and the lookup behind them
                // completes *after* the state that announced the track - so a resolution that
                // changes nothing else must still reach the watch, exactly as a late shuffle or
                // like flip must.
                ?: changed("backdrop", previous.hasBackdropArt, current.hasBackdropArt)
                ?: changed("backdrop pending", previous.backdropArtPending, current.backdropArtPending)
                // Settled after a wait of its own, so it changes when nothing else does.
                ?: changed("live", previous.live, current.live)
                // Changes with a setting on the phone, not with playback, so nothing else moves.
                ?: changed("audio block", previous.audioBlock, current.audioBlock)
                ?: actionsDifference(previous.mediaActionsList, current.mediaActionsList)
                ?: positionDifference(previous, current)
    }

    private fun changed(field: String, before: Any?, after: Any?): String? =
            if (before == after) null else "$field $before -> $after"

    /** Quoted, as `logSummary` quotes them: titles have spaces. */
    private fun changed(field: String, before: String, after: String): String? =
            if (before == after) null else "$field '$before' -> '$after'"

    /** Compared as floats rather than boxed: `Float.equals` would call NaN equal to itself and
     *  tell -0 from 0, and either would quietly change what counts as new. */
    private fun changed(field: String, before: Float, after: Float): String? =
            if (before == after) null else "$field $before -> $after"

    /** Names the first action that differs and the part of it that does. The icons are rasterized
     *  afresh for every notification update, so a glyph rendered to different bytes would defeat
     *  this comparison on every build - which is worth being able to see. */
    private fun actionsDifference(before: List<MediaAction>, after: List<MediaAction>): String? {
        if (before == after) return null
        if (before.size != after.size) return "actions ${before.size} -> ${after.size}"
        val index = before.indices.firstOrNull { before[it] != after[it] }
                ?: return "actions changed"
        val was = before[index]
        val now = after[index]
        val part = when {
            was.id != now.id -> "id ${digestOf(was.id)} -> ${digestOf(now.id)}"
            was.label != now.label -> "label ${was.label} -> ${now.label}"
            was.semantic != now.semantic -> "semantic ${was.semantic} -> ${now.semantic}"
            was.iconKey != now.iconKey -> "icon key ${was.iconKey} -> ${now.iconKey}"
            was.iconPng != now.iconPng -> "icon ${iconOf(was)} -> ${iconOf(now)}"
            else -> "field presence"
        }
        return "action #$index $part"
    }

    /** Size and content digest: `ByteString.hashCode` is computed over the bytes. */
    private fun iconOf(action: MediaAction): String =
            "${action.iconPng.size()} B ${digestOf(action.iconPng)}"

    /**
     * positionMs naturally drifts forward every time playback is read, so comparing it directly
     * would defeat the point of this check. Instead, extrapolate where playback "should" be from the
     * previous state and only treat a bigger-than-expected jump (a real seek, or a track restart) as
     * a change worth transmitting.
     */
    private fun positionDifference(previous: MusicState, current: MusicState): String? {
        val elapsedSincePrevious = current.positionUpdateTime - previous.positionUpdateTime
        val expectedPosition = if (previous.playing) {
            previous.positionMs + (elapsedSincePrevious * previous.playbackSpeed).toLong()
        } else {
            previous.positionMs
        }
        return if (abs(current.positionMs - expectedPosition) <= SEEK_DETECTION_THRESHOLD_MS) {
            null
        } else {
            "position expected $expectedPosition ms, got ${current.positionMs} ms"
        }
    }

    /** A short stand-in for a value too long or too opaque to print: enough to see whether two of
     *  them are the same, and whether a pair keeps alternating. */
    private fun digestOf(value: Any): String = Integer.toHexString(value.hashCode())
}
